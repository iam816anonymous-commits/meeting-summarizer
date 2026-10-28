package com.iam816anonymous.meetingsummarizer.summary

data class ModelBenchmarkSample(
    val modelId: String, val coldStartMs: Long, val generationMs: Long, val peakAppMemoryBytes: Long,
    val outputCharacters: Int, val outputAccepted: Boolean, val thermalStatus: Int?,
    val batteryPercentBefore: Int?, val batteryPercentAfter: Int?, val failureCategory: String? = null
)
data class ModelBenchmarkReport(
    val modelId: String, val runs: Int, val medianColdStartMs: Long?, val medianGenerationMs: Long?,
    val maximumPeakAppMemoryBytes: Long?, val acceptedOutputRate: Double, val failures: Map<String, Int>, val note: String
)

/** In-memory benchmark aggregation; never pass transcript text, prompts or generated text here. */
class OnDeviceModelBenchmark {
    fun summarize(samples: List<ModelBenchmarkSample>): List<ModelBenchmarkReport> =
        samples.groupBy { it.modelId }.map { (modelId, values) ->
            val coldStarts = values.map { it.coldStartMs }.filter { it >= 0 }.sorted()
            val generations = values.map { it.generationMs }.filter { it >= 0 }.sorted()
            ModelBenchmarkReport(
                modelId, values.size, median(coldStarts), median(generations),
                values.map { it.peakAppMemoryBytes }.filter { it >= 0 }.maxOrNull(),
                if (values.isEmpty()) 0.0 else values.count { it.outputAccepted }.toDouble() / values.size,
                values.mapNotNull { it.failureCategory }.groupingBy { it }.eachCount(),
                "Run on the target phone with representative meetings; these measurements do not generalize to other devices."
            )
        }.sortedBy { it.medianGenerationMs ?: Long.MAX_VALUE }

    private fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val middle = values.size / 2
        return if (values.size % 2 == 1) values[middle] else values[middle - 1] / 2 + values[middle] / 2
    }
}
