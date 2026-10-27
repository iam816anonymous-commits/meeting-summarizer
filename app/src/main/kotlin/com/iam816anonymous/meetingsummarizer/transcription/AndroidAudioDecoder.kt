package com.iam816anonymous.meetingsummarizer.transcription

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Decodes private AAC/M4A files into bounded 30-second mono 16 kHz windows. */
internal class AndroidAudioDecoder {
    fun decodeChunks(audioFile: File, onChunk: (samples16k: FloatArray, startMs: Long, endMs: Long) -> Unit) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(audioFile.absolutePath)
            var track = -1
            var inputFormat: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    track = index
                    inputFormat = format
                    break
                }
            }
            if (track < 0 || inputFormat == null) throw UnsupportedOperationException("No supported audio track was found.")
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw UnsupportedOperationException("The audio format is missing its codec.")
            extractor.selectTrack(track)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var outputEnded = false
            var inputEnded = false
            var samples = FloatArray(max(sampleRate * WINDOW_SECONDS, 4096))
            var sampleCount = 0
            var chunkIndex = 0L

            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                            ?: throw IllegalStateException("Decoder input buffer is unavailable.")
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val info = MediaCodec.BufferInfo()
                val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
                when {
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format = codec.outputFormat
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                        if (sampleCount > 0) throw IllegalStateException("Decoder changed audio format mid-stream.")
                        samples = FloatArray(max(sampleRate * WINDOW_SECONDS, 4096))
                    }
                    outputIndex >= 0 -> {
                        val output = codec.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset)
                            output.limit(info.offset + info.size)
                            output.order(ByteOrder.LITTLE_ENDIAN)
                            val mono = pcmToMono(output, channels, pcmEncoding)
                            var offset = 0
                            while (offset < mono.size) {
                                val required = sampleRate * WINDOW_SECONDS
                                if (samples.size < required) samples = samples.copyOf(required)
                                val amount = min(required - sampleCount, mono.size - offset)
                                mono.copyInto(samples, sampleCount, offset, offset + amount)
                                sampleCount += amount
                                offset += amount
                                if (sampleCount >= required) {
                                    val window = samples.copyOf(sampleCount)
                                    val start = chunkIndex * WINDOW_SECONDS * 1000L
                                    val end = start + sampleCount * 1000L / sampleRate
                                    onChunk(resample(window, sampleRate), start, end)
                                    chunkIndex++
                                    sampleCount = 0
                                }
                            }
                        }
                        outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            if (sampleCount > 0) {
                val start = chunkIndex * WINDOW_SECONDS * 1000L
                val end = start + sampleCount * 1000L / sampleRate
                onChunk(resample(samples.copyOf(sampleCount), sampleRate), start, end)
            }
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private fun pcmToMono(buffer: java.nio.ByteBuffer, channels: Int, encoding: Int): FloatArray {
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            AudioFormat.ENCODING_PCM_8BIT -> 1
            else -> 2
        }
        val frameBytes = bytesPerSample * channels
        val frames = buffer.remaining() / frameBytes
        val result = FloatArray(frames)
        for (frame in 0 until frames) {
            var sum = 0f
            for (channel in 0 until channels) {
                sum += when (encoding) {
                    AudioFormat.ENCODING_PCM_FLOAT -> buffer.float
                    AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 0xff) - 128) / 128f
                    else -> buffer.short / 32768f
                }
            }
            result[frame] = (sum / channels).coerceIn(-1f, 1f)
        }
        return result
    }

    private fun resample(input: FloatArray, sourceRate: Int): FloatArray {
        if (sourceRate == TARGET_SAMPLE_RATE) return input
        val outputSize = floor(input.size * TARGET_SAMPLE_RATE.toDouble() / sourceRate).toInt().coerceAtLeast(1)
        val output = FloatArray(outputSize)
        val ratio = sourceRate.toDouble() / TARGET_SAMPLE_RATE
        for (index in output.indices) {
            val position = index * ratio
            val left = position.toInt().coerceIn(0, input.lastIndex)
            val right = min(left + 1, input.lastIndex)
            val fraction = (position - left).toFloat()
            output[index] = input[left] * (1f - fraction) + input[right] * fraction
        }
        return output
    }

    companion object {
        private const val TARGET_SAMPLE_RATE = 16_000
        private const val WINDOW_SECONDS = 30
        private const val CODEC_TIMEOUT_US = 10_000L
    }
}
