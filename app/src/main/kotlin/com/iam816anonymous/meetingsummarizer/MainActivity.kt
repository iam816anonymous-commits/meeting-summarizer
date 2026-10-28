package com.iam816anonymous.meetingsummarizer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import java.io.File
import android.os.StatFs
import java.util.Properties
import com.iam816anonymous.meetingsummarizer.summary.DeterministicMeetingSummarizer
import com.iam816anonymous.meetingsummarizer.summary.SummaryFindingKind
import com.iam816anonymous.meetingsummarizer.summary.PersonalProfile
import com.iam816anonymous.meetingsummarizer.summary.PersonalActionItemClassifier
import com.iam816anonymous.meetingsummarizer.summary.PersonalActionCategory
import com.iam816anonymous.meetingsummarizer.summary.DeviceRuntimeSnapshot
import com.iam816anonymous.meetingsummarizer.summary.OnDeviceLlmCapability
import com.iam816anonymous.meetingsummarizer.summary.LocalModelRegistry
import com.iam816anonymous.meetingsummarizer.summary.LocalLlmModelCatalog
import com.iam816anonymous.meetingsummarizer.summary.LocalLlmModel
import com.iam816anonymous.meetingsummarizer.summary.VerifiedModelArtifact
import com.iam816anonymous.meetingsummarizer.summary.VerifiedModelDownloader
import com.iam816anonymous.meetingsummarizer.summary.ModelInstallResult
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.runBlocking
import com.iam816anonymous.meetingsummarizer.summary.ModelDeviceSnapshot
import com.iam816anonymous.meetingsummarizer.transcription.OfflineModelManager
import com.iam816anonymous.meetingsummarizer.transcription.SherpaOnnxEnglishSpeechEngine
import com.iam816anonymous.meetingsummarizer.transcription.SherpaOnnxWhisperSpeechEngine
import com.iam816anonymous.meetingsummarizer.transcription.WhisperTinyModelManager
import com.iam816anonymous.meetingsummarizer.transcription.SpeechEngineResult
import com.iam816anonymous.meetingsummarizer.transcription.TranscriptStore
import com.iam816anonymous.meetingsummarizer.data.DemoMeetingRepository
import com.iam816anonymous.meetingsummarizer.workflow.MeetingScreenState
import com.iam816anonymous.meetingsummarizer.workflow.MeetingWorkflowAction
import com.iam816anonymous.meetingsummarizer.workflow.MeetingWorkflowReducer
import com.iam816anonymous.meetingsummarizer.workflow.MeetingWorkflowState

/**
 * Phase 2 UI prototype. Real capture will be introduced in Phase 3.
 * The demo workflow is deliberately disconnected from the microphone.
 */
class MainActivity : Activity() {
    private val demoRepository = DemoMeetingRepository()
    private var workflowState = MeetingWorkflowState()
    private var recordingActive = false
    private var recordingStatus = "NOT RECORDING"
    private var recordingError: String? = null
    private var receiverRegistered = false
    private var showRecordings = false
    private var showModelSettings = false
    private var currentSessionId: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlaying: File? = null
    private var showTranscriptSessionId: String? = null
    private var transcriptSearchQuery: String = ""
    private var summaryVisibleSessionId: String? = null
    private var llmAssessmentMessage: String? = null
    private var modelDownloadBusy = false
    private var englishDownloadBusy = false
    private var whisperDownloadBusy = false
    private var englishDownloadPercent = 0
    private var whisperDownloadPercent = 0
    private var englishDownloadStage = "Ready"
    private var whisperDownloadStage = "Ready"
    private val llmDownloadBusy = mutableSetOf<String>()
    private val llmDownloadPercent = mutableMapOf<String, Int>()
    private val llmDownloadStage = mutableMapOf<String, String>()
    private val llmTestBusy = mutableSetOf<String>()
    private var llmStatusMessage: String? = null
    private val llmDownloader by lazy { VerifiedModelDownloader() }
    private fun llmModelDirectory(): File = File(filesDir, "llm-models")
    private var modelDownloadPercent = 0
    private var modelInstallStage = "Preparing download"
    private var modelProgressBar: ProgressBar? = null
    private var modelProgressLabel: TextView? = null
    private val profilePreferences by lazy { getSharedPreferences("personal_profile", MODE_PRIVATE) }
    private var transcriptionBusy = false
    private var transcriptionMessage: String? = null
    private var transcriptStore: TranscriptStore? = null
    private val offlineModelManager by lazy { OfflineModelManager(this) }
    private val speechEngine by lazy { SherpaOnnxEnglishSpeechEngine() }
    private val whisperModelManager by lazy { WhisperTinyModelManager(this) }
    private val modelSelectionPreferences by lazy { getSharedPreferences("model_selection", MODE_PRIVATE) }
    private fun selectedLanguage(): String = modelSelectionPreferences.getString("transcription_language", "en") ?: "en"
    private fun selectedEngine(): String = modelSelectionPreferences.getString("transcription_engine", "zipformer-en") ?: "zipformer-en"

    private val recordingStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != RecordingService.ACTION_STATE) return
            recordingStatus = intent.getStringExtra(RecordingService.EXTRA_STATE) ?: recordingStatus
            recordingError = intent.getStringExtra(RecordingService.EXTRA_ERROR)
            currentSessionId = intent.getStringExtra(RecordingService.EXTRA_SESSION_ID) ?: currentSessionId
            recordingActive = recordingStatus == RecordingLifecycleState.RECORDING.name ||
                recordingStatus == RecordingLifecycleState.STOPPING.name ||
                recordingStatus == RecordingLifecycleState.PREPARING.name
            if (recordingStatus == RecordingLifecycleState.COMPLETED.name) {
                recordingError = null
                showRecordings = false
            }
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RecordingService.recoverInterruptedSegments(this)
        runCatching { getTranscriptStore().recoverInterruptedJobs() }
        render()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(RecordingService.ACTION_STATE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(recordingStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(recordingStateReceiver, filter)
        }
        receiverRegistered = true
        // Refresh state after registering so returning from the background cannot leave stale UI.
        startService(Intent(this, RecordingService::class.java).setAction(RecordingService.ACTION_QUERY_STATE))
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(recordingStateReceiver)
            receiverRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    private fun confirmAndStartRecording() {
        AlertDialog.Builder(this)
            .setTitle("Before you record")
            .setMessage(
                "Recording captures nearby room audio using this phone's microphone. " +
                    "Tell participants and obtain any consent or authorization required for this meeting. " +
                    "Audio is stored locally on this device. Recording starts only after you confirm and grant microphone permission."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue") { _, _ ->
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
                } else {
                    startRecordingService()
                }
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MICROPHONE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startRecordingService()
            } else {
                recordingError = "Microphone permission was denied. Nothing was recorded."
                render()
            }
        }
    }

    private fun startRecordingService() {
        recordingError = null
        val intent = Intent(this, RecordingService::class.java).setAction(RecordingService.ACTION_START)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            recordingStatus = RecordingLifecycleState.PREPARING.name
            render()
        } catch (error: Exception) {
            recordingError = "Could not start recording: " + (error.message ?: error.javaClass.simpleName)
            recordingStatus = RecordingLifecycleState.ERROR.name
            render()
        }
    }

    private fun stopRecordingService() {
        startService(Intent(this, RecordingService::class.java).setAction(RecordingService.ACTION_STOP))
    }

    private fun render() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(COLOR_BACKGROUND)
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(28))
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        addHeader(content)
        addSpace(content, 22)

        if (showTranscriptSessionId != null) {
            renderTranscript(content, showTranscriptSessionId!!)
        } else if (showRecordings) {
            renderRecordings(content)
        } else if (showModelSettings) {
            renderModelSettings(content)
        } else {
            when (workflowState.screen) {
                MeetingScreenState.IDLE -> renderHome(content)
                MeetingScreenState.DEMO_ACTIVE -> renderDemoActive(content)
                MeetingScreenState.RESULTS_READY -> renderResults(content)
            }
        }
        if (!recordingActive && recordingStatus == RecordingLifecycleState.COMPLETED.name && !showRecordings && !showModelSettings) {
            addSpace(content, 16)
            val saved = card(content, COLOR_TINT)
            addText(saved, "Recording stopped and saved", 19f, COLOR_PRIMARY_DARK, true)
            addSpace(saved, 6)
            val files = latestRecordingFiles()
            val totalBytes = files.sumOf { it.length() }
            addText(saved, if (files.isNotEmpty() && totalBytes > 0L) "${files.size} audio segment(s) · ${formatBytes(totalBytes)} stored on this device." else "The stop action completed, but no non-empty audio segment was found. Please record a short test again.", 13f, COLOR_TEXT)
            addSpace(saved, 10)
            addButton(saved, "View recordings", true) { showRecordings = true; render() }
        }
        if (recordingActive && workflowState.screen != MeetingScreenState.IDLE) {
            addSpace(content, 18)
            val activeRecording = card(content, COLOR_PRIMARY_DARK)
            addText(activeRecording, "● RECORDING · " + recordingStatus, 13f, 0xFFFFD4A3.toInt(), true)
            addSpace(activeRecording, 8)
            addText(activeRecording, "Microphone capture is active. Audio is saved locally on this device.", 13f, COLOR_WHITE)
            addSpace(activeRecording, 10)
            addButton(activeRecording, "Stop recording", true) { stopRecordingService() }
        }

        setContentView(scroll)
    }

    private fun addHeader(parent: LinearLayout) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = TextView(this).apply {
            text = "M"
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(COLOR_WHITE)
            background = rounded(COLOR_PRIMARY, 16)
        }
        row.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))
        val titleColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        addText(titleColumn, "Meeting Summarizer", 17f, COLOR_TEXT, true)
        addText(titleColumn, "PRIVATE BY DESIGN", 10f, COLOR_MUTED, true)
        row.addView(titleColumn, LinearLayout.LayoutParams(0, -2, 1f))
        val more = TextView(this).apply {
            text = "⋮"
            textSize = 26f
            gravity = Gravity.CENTER
            setTextColor(COLOR_TEXT)
            setPadding(dp(12), 0, dp(4), 0)
            setOnClickListener { showAppMenu() }
            contentDescription = "More options"
        }
        row.addView(more, LinearLayout.LayoutParams(dp(40), dp(48)))
        val mode = TextView(this).apply {
            text = "OFFLINE FIRST"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_PRIMARY_DARK)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(COLOR_TINT, 50)
        }
        row.addView(mode)
        parent.addView(row)
    }

    private fun renderHome(parent: LinearLayout) {
        addText(parent, "Meeting notes,\nmade actionable.", 30f, COLOR_TEXT, true)
        addSpace(parent, 7)
        addText(parent, "Record a meeting, then review its transcript and follow-ups.", 14f, COLOR_MUTED)
        addSpace(parent, 20)
        val hero = card(parent, COLOR_PRIMARY_DARK)
        addText(hero, if (recordingActive) "RECORDING IN PROGRESS" else "READY WHEN YOU ARE", 11f, 0xFFC9C5FF.toInt(), true)
        addSpace(hero, 10)
        addText(hero, if (recordingActive) "Your meeting is being captured." else "Capture the conversation.", 22f, COLOR_WHITE, true)
        addSpace(hero, 7)
        addText(hero, if (recordingActive) "Audio is saved locally on this device." else "Start recording when everyone is ready. Audio stays on this device.", 13f, 0xFFE6E4FF.toInt())
        addSpace(hero, 16)
        if (recordingActive) {
            addText(hero, "● " + recordingStatus.replace('_', ' '), 12f, 0xFFFFD4A3.toInt(), true)
            addSpace(hero, 10)
            addButton(hero, "Stop recording", true) { stopRecordingService() }
        } else {
            addButton(hero, "Start recording", true) { confirmAndStartRecording() }
        }
        if (recordingError != null) { addSpace(hero, 8); addText(hero, recordingError!!, 13f, 0xFFFFC7C7.toInt()) }
        addSpace(parent, 14)
        val actions = card(parent, COLOR_SURFACE)
        addText(actions, "Your meetings", 17f, COLOR_TEXT, true)
        addSpace(actions, 5)
        addText(actions, "Open saved audio and transcripts, or search across your meetings.", 13f, COLOR_MUTED)
        addSpace(actions, 10)
        addButton(actions, "View recordings", false) { showRecordings = true; render() }
        addSpace(actions, 6)
        addButton(actions, if (loadPersonalProfile().preferredName.isBlank()) "Set up personal profile" else "Edit personal profile", false) { promptPersonalProfile() }
        addSpace(parent, 12)
        addText(parent, "Private by design · Core recording and summaries work without a cloud account.", 12f, COLOR_MUTED, false, Gravity.CENTER)
    }
    private fun renderDemoActive(parent: LinearLayout) {
        addText(parent, "Demo session", 30f, COLOR_TEXT, true)
        addSpace(parent, 6)
        addText(parent, "This screen exercises the session workflow only.", 15f, COLOR_MUTED)
        addSpace(parent, 20)
        val active = card(parent, COLOR_SURFACE)
        val status = TextView(this).apply {
            text = "●  DEMO IN PROGRESS"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_WARNING)
            background = rounded(COLOR_WARNING_TINT, 50)
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }
        active.addView(status, wrapParams())
        addSpace(active, 16)
        addText(active, "No microphone is being used.", 21f, COLOR_TEXT, true)
        addSpace(active, 8)
        addText(active, "Phase 2 uses a controlled demo state. Nothing is being recorded and no audio file is created.", 14f, COLOR_MUTED)
        addSpace(active, 18)
        addButton(active, "End demo and view sample results", true) {
            workflowState = MeetingWorkflowReducer.reduce(workflowState, MeetingWorkflowAction.StopDemo)
            render()
        }
        addSpace(parent, 12)
        addText(parent, "This demo does not use the microphone. Any real recording remains separately controlled by the recording panel and notification.", 13f, COLOR_MUTED)
    }

    private fun renderResults(parent: LinearLayout) {
        val result = demoRepository.loadSampleResult()
        addText(parent, "Your meeting brief", 29f, COLOR_TEXT, true)
        addSpace(parent, 6)
        addText(parent, "SAMPLE DATA · NOT FROM A REAL RECORDING", 11f, COLOR_MUTED, true)
        addSpace(parent, 18)

        val overview = card(parent, COLOR_PRIMARY_DARK)
        addText(overview, result.title, 21f, COLOR_WHITE, true)
        addSpace(overview, 6)
        addText(overview, result.durationLabel, 12f, 0xFFD7D4FF.toInt(), true)
        addSpace(overview, 15)
        result.summary.forEachIndexed { index, line ->
            addBullet(overview, line, COLOR_WHITE, (index + 1).toString() + ".")
            addSpace(overview, 8)
        }

        addSpace(parent, 18)
        addSectionTitle(parent, "My action items", "Sample assignments")
        result.actionItems.forEachIndexed { index, item ->
            val itemCard = card(parent, COLOR_SURFACE)
            val number = TextView(this).apply {
                text = "0" + (index + 1).toString()
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(COLOR_PRIMARY_DARK)
                background = rounded(COLOR_TINT, 10)
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }
            itemCard.addView(number, wrapParams())
            addSpace(itemCard, 10)
            addText(itemCard, item.title, 17f, COLOR_TEXT, true)
            addSpace(itemCard, 6)
            addText(itemCard, item.owner, 13f, COLOR_PRIMARY_DARK, true)
            addText(itemCard, "Due: " + item.due, 12f, COLOR_MUTED)
            addSpace(itemCard, 10)
            addText(itemCard, item.evidence, 12f, COLOR_MUTED)
            addSpace(parent, 10)
        }

        addSpace(parent, 8)
        addSectionTitle(parent, "Decisions", "What was agreed")
        val decisions = card(parent, COLOR_SURFACE)
        result.decisions.forEachIndexed { index, decision ->
            addBullet(decisions, decision, COLOR_TEXT, "•")
            if (index < result.decisions.lastIndex) addSpace(decisions, 10)
        }

        addSpace(parent, 18)
        addSectionTitle(parent, "Transcript preview", "Sample excerpt")
        val transcript = card(parent, COLOR_SURFACE_ALT)
        result.transcriptExcerpt.forEachIndexed { index, line ->
            addText(transcript, line, 13f, COLOR_TEXT)
            if (index < result.transcriptExcerpt.lastIndex) addSpace(transcript, 12)
        }

        addSpace(parent, 20)
        addButton(parent, "Back to home", true) {
            workflowState = MeetingWorkflowReducer.reduce(workflowState, MeetingWorkflowAction.ReturnHome)
            render()
        }
        addSpace(parent, 10)
        addButton(parent, "Start another demo session", false) {
            workflowState = MeetingWorkflowReducer.reduce(
                workflowState,
                MeetingWorkflowAction.StartDemo(System.currentTimeMillis())
            )
            render()
        }
    }


    private fun renderRecordings(parent: LinearLayout) {
        addText(parent, "Saved recordings", 29f, COLOR_TEXT, true)
        addSpace(parent, 6)
        addText(parent, "Audio and transcripts stay on this device. The English model is downloaded only when you choose to install it; recognition runs locally.", 14f, COLOR_MUTED)
        addSpace(parent, 12)
        val searchCard = card(parent, COLOR_SURFACE_ALT)
        addText(searchCard, "Search transcripts", 17f, COLOR_TEXT, true)
        addSpace(searchCard, 6)
        addText(searchCard, "Find a phrase across saved meetings.", 13f, COLOR_MUTED)
        addSpace(searchCard, 8)
        addButton(searchCard, if (transcriptSearchQuery.isBlank()) "Search all transcripts" else "Search again · ${transcriptSearchQuery.take(24)}", true) { promptTranscriptSearch() }
        if (transcriptSearchQuery.isNotBlank()) {
            addSpace(searchCard, 8)
            val matches = runCatching { getTranscriptStore().searchSegments(transcriptSearchQuery, 100) }.getOrDefault(emptyList())
            addText(searchCard, "${matches.size} match(es) · showing up to 100", 12f, COLOR_MUTED, true)
            if (matches.isEmpty()) {
                addSpace(searchCard, 5)
                addText(searchCard, "No transcript text matched that phrase.", 13f, COLOR_MUTED)
            } else {
                matches.forEach { segment ->
                    addSpace(searchCard, 8)
                    addText(searchCard, "${segment.sessionId} · ${formatTimestamp(segment.startMs)}", 11f, COLOR_PRIMARY_DARK, true)
                    addText(searchCard, segment.text, 13f, COLOR_TEXT)
                    addButton(searchCard, "Open meeting transcript", false) {
                        showTranscriptSessionId = segment.sessionId
                        showRecordings = false
                        render()
                    }
                }
            }
            addSpace(searchCard, 8)
            addButton(searchCard, "Clear search", false) { transcriptSearchQuery = ""; render() }
        }
        addSpace(parent, 14)
        if (transcriptionBusy) { addSpace(parent, 8); addText(parent, "Transcription is running. You can keep using the app.", 12f, COLOR_MUTED) }
        addSpace(parent, 18)
        val root = File(filesDir, "recordings")
        val sessions = root.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.lastModified() }.orEmpty()
        if (sessions.isEmpty()) {
            val empty = card(parent, COLOR_SURFACE)
            addText(empty, "No recordings found yet", 18f, COLOR_TEXT, true)
            addSpace(empty, 6)
            addText(empty, "Start a real recording, speak for at least 30 seconds, then stop it. Completed audio will appear here.", 13f, COLOR_MUTED)
        } else {
            sessions.forEach { session ->
                val sessionCard = card(parent, COLOR_SURFACE)
                addText(sessionCard, session.name, 16f, COLOR_TEXT, true)
                addSpace(sessionCard, 6)
                val audioFiles = session.listFiles()?.filter { it.isFile && it.extension.equals("m4a", true) }?.sortedBy { it.name }.orEmpty()
                if (audioFiles.isEmpty()) {
                    addText(sessionCard, "No audio segment found.", 13f, COLOR_MUTED)
                } else {
                    audioFiles.forEach { audio ->
                        val metadata = readMetadata(audio)
                        val status = metadata.getProperty("status", "UNVERIFIED")
                        addSpace(sessionCard, 8)
                        addText(sessionCard, audio.name + " · " + formatBytes(audio.length()), 14f, COLOR_TEXT, true)
                        addText(sessionCard, "Status: " + status, 12f, if (status == "COMPLETE") COLOR_PRIMARY_DARK else COLOR_WARNING)
                        if (audio.length() > 0L) {
                            addSpace(sessionCard, 6)
                            addButton(sessionCard, if (currentlyPlaying == audio && mediaPlayer?.isPlaying == true) "Stop playback" else "Play audio", false) {
                                if (currentlyPlaying == audio && mediaPlayer?.isPlaying == true) {
                                    stopPlayback()
                                    render()
                                } else {
                                    playAudio(audio)
                                }
                            }
                        }
                    }
                    val completeAudio = audioFiles.filter {
                        it.isFile && it.length() > 0L && readMetadata(it).getProperty("status") == "COMPLETE"
                    }
                    val savedSegments = runCatching { getTranscriptStore().listSegments(session.name) }.getOrDefault(emptyList())
                    if (savedSegments.isNotEmpty()) {
                        addSpace(sessionCard, 8)
                        addButton(sessionCard, "View transcript · ${savedSegments.size} segments", false) {
                            showTranscriptSessionId = session.name
                            showRecordings = false
                            render()
                        }
                    }
                    if (completeAudio.isNotEmpty()) {
                        addSpace(sessionCard, 8)
                        addButton(sessionCard, if (transcriptionBusy) "Transcription in progress…" else "Transcribe meeting offline", true) {
                            transcribeSession(session, completeAudio)
                        }
                    }
                }
                addSpace(sessionCard, 8)
                addButton(sessionCard, "Delete meeting and audio", false) { confirmDeleteMeeting(session) }
                addSpace(parent, 10)
            }
        }
        addSpace(parent, 12)
        addButton(parent, "Refresh recordings", false) { render() }
        addSpace(parent, 8)
        addButton(parent, "Back to home", true) { showRecordings = false; render() }
    }

    private fun showAppMenu() {
        AlertDialog.Builder(this).setItems(arrayOf("About this app", "Languages & models", "Saved recordings")) { dialog, which ->
            when (which) { 0 -> showAboutApp(); 1 -> { showModelSettings = true; showRecordings = false; render() }; 2 -> { showRecordings = true; showModelSettings = false; render() } }
            dialog.dismiss()
        }.show()
    }

    private fun showAboutApp() {
        AlertDialog.Builder(this).setTitle("About Meeting Summarizer")
            .setMessage("A private, offline-first meeting recorder. Recordings stay on this device. English transcription is the default; optional multilingual transcription can be installed when needed.")
            .setPositiveButton("Languages & models") { _, _ -> showModelSettings = true; showRecordings = false; render() }
            .setNegativeButton("Close", null).show()
    }

    private fun renderModelSettings(parent: LinearLayout) {
        addText(parent, "Languages & models", 27f, COLOR_TEXT, true)
        addSpace(parent, 5)
        addText(parent, "Choose a transcription language and manage on-device speech models.", 13f, COLOR_MUTED)
        addSpace(parent, 8)
        addButton(parent, "Check device resources", false) { assessOnDeviceLlm() }
        addSpace(parent, 16)
        val languageCard = card(parent, COLOR_SURFACE)
        addText(languageCard, "Transcription language", 17f, COLOR_TEXT, true)
        addSpace(languageCard, 5)
        addText(languageCard, languageDisplayName(selectedLanguage()), 15f, COLOR_PRIMARY_DARK, true)
        val whisperReady = runCatching { whisperModelManager.isInstalled() }.getOrDefault(false)
        val activeDescription = if (selectedEngine() == "whisper-multilingual" && whisperReady) "Multilingual Whisper is active on this device." else if (selectedLanguage() == "en") "Default English model selected." else "English remains active until the multilingual model is installed and selected."
        addText(languageCard, activeDescription, 12f, COLOR_MUTED)
        addSpace(languageCard, 9)
        addButton(languageCard, "Choose language", true) { showLanguagePicker() }
        addSpace(parent, 12)
        val englishCard = card(parent, COLOR_SURFACE)
        addText(englishCard, "English transcription", 16f, COLOR_TEXT, true)
        addText(englishCard, if (offlineModelManager.isInstalled()) "Installed · available offline" else "Not installed · download once to use offline", 12f, if (offlineModelManager.isInstalled()) COLOR_PRIMARY_DARK else COLOR_MUTED)
        if (!offlineModelManager.isInstalled() && !englishDownloadBusy) { addSpace(englishCard, 8); addButton(englishCard, "Download English model", false) { installOfflineModel() } }
        if (englishDownloadBusy) { addSpace(englishCard, 8); addText(englishCard, englishDownloadStage + " · " + englishDownloadPercent + "%", 12f, COLOR_PRIMARY_DARK, true); englishCard.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = englishDownloadPercent }, matchWrapParams()) }
        addSpace(parent, 12)
        val whisperCard = card(parent, COLOR_SURFACE)
        addText(whisperCard, "Multilingual Whisper Tiny", 16f, COLOR_TEXT, true)
        addText(whisperCard, if (whisperReady) "Installed · about 99 MiB · works offline" else "Optional · about 99 MiB download · keep 250 MiB free for installation", 12f, if (whisperReady) COLOR_PRIMARY_DARK else COLOR_MUTED)
        addSpace(whisperCard, 8)
        if (!whisperReady && !whisperDownloadBusy) { addButton(whisperCard, "Download multilingual model", false) { installWhisperModel() }
        } else if (whisperReady && selectedEngine() != "whisper-multilingual") {
            addButton(whisperCard, "Use multilingual model", false) { modelSelectionPreferences.edit().putString("transcription_engine", "whisper-multilingual").apply(); transcriptionMessage = "Multilingual Whisper selected for " + languageDisplayName(selectedLanguage()) + "."; render() }
        } else if (whisperReady) {
            addText(whisperCard, "Currently selected", 12f, COLOR_PRIMARY_DARK, true)
            addButton(whisperCard, "Switch to default English model", false) { modelSelectionPreferences.edit().putString("transcription_engine", "zipformer-en").putString("transcription_language", "en").apply(); transcriptionMessage = "Default English model selected."; render() }
        }
        addSpace(parent, 18)
        addText(parent, "On-device text models", 21f, COLOR_TEXT, true)
        addSpace(parent, 5)
        addText(parent, "Optional downloads for local summaries and structured meeting notes. Files are stored on this device and verified before they can be loaded.", 13f, COLOR_MUTED)
        LocalLlmModelCatalog.models.forEach { model ->
            addSpace(parent, 10)
            val llmCard = card(parent, COLOR_SURFACE)
            addText(llmCard, model.displayName, 16f, COLOR_TEXT, true)
            addSpace(llmCard, 4)
            addText(llmCard, model.description, 12f, COLOR_MUTED)
            addSpace(llmCard, 5)
            addText(llmCard, "Download: about 331 MiB · License: " + model.license, 12f, COLOR_MUTED)
            val installed = model.isInstalled(llmModelDirectory())
            addText(llmCard, if (installed) "Installed · size verified" else "Not installed", 12f, if (installed) COLOR_PRIMARY_DARK else COLOR_MUTED, true)
            if (model.id in llmDownloadBusy) {
                addSpace(llmCard, 8)
                addText(llmCard, (llmDownloadStage[model.id] ?: "Downloading") + " · " + (llmDownloadPercent[model.id] ?: 0) + "%", 12f, COLOR_PRIMARY_DARK, true)
                llmCard.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = llmDownloadPercent[model.id] ?: 0 }, matchWrapParams())
            } else if (installed) {
                addSpace(llmCard, 8)
                addButton(llmCard, if (model.id in llmTestBusy) "Testing model…" else "Test local model", false) {
                    if (model.id !in llmTestBusy) testLocalLlmModel(model)
                }
            } else {
                addSpace(llmCard, 8)
                addButton(llmCard, "Download model", false) { downloadLocalLlmModel(model) }
            }
        }
        llmStatusMessage?.let {
            addSpace(parent, 8)
            addText(parent, it, 12f, if (it.contains("failed", true) || it.contains("not enough", true)) COLOR_WARNING else COLOR_PRIMARY_DARK)
        }
        addSpace(parent, 6)
        addText(parent, "Compatibility note: RAM checks are estimates. These two files share Qwen3-0.6B base weights but use different response templates; they are not two different base models. The target phone must pass a real load and generation test before either is considered reliable.", 11f, COLOR_MUTED)
        if (modelDownloadBusy) {
            addSpace(parent, 12)
            val progressCard = card(parent, COLOR_SURFACE_ALT)
            addText(progressCard, modelInstallProgressText(), 13f, COLOR_PRIMARY_DARK, true)
            val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = modelDownloadPercent }
            progressCard.addView(progress, matchWrapParams())
            addText(progressCard, "Keep the app open until installation finishes.", 12f, COLOR_MUTED)
        }
        if (whisperDownloadBusy) { addSpace(whisperCard, 8); addText(whisperCard, whisperDownloadStage + " · " + whisperDownloadPercent + "%", 12f, COLOR_PRIMARY_DARK, true); whisperCard.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = whisperDownloadPercent }, matchWrapParams()) }
        if (englishDownloadBusy || whisperDownloadBusy) { addSpace(parent, 12); addText(parent, "Downloads continue while you navigate. Wait for both installations to finish before transcribing.", 12f, COLOR_MUTED) }
        if (transcriptionMessage != null) { addSpace(parent, 8); addText(parent, transcriptionMessage!!, 12f, if (transcriptionMessage!!.contains("failed", true)) COLOR_WARNING else COLOR_PRIMARY_DARK) }
        addSpace(parent, 20)
        addButton(parent, "Back to home", true) { showModelSettings = false; render() }
    }
    private fun languageDisplayName(code: String): String = when (code) {
        "en" -> "English"
        "hi" -> "Hindi"
        "te" -> "Telugu"
        "kn" -> "Kannada"
        "ta" -> "Tamil"
        "ml" -> "Malayalam"
        "bn" -> "Bengali"
        "mr" -> "Marathi"
        "gu" -> "Gujarati"
        "ur" -> "Urdu"
        "fr" -> "French"
        "de" -> "German"
        "es" -> "Spanish"
        "ja" -> "Japanese"
        "zh" -> "Chinese"
        else -> code
    }

    private fun showLanguagePicker() {
        val labels = arrayOf(
            "English (en)", "Hindi (hi)", "Telugu (te)", "Kannada (kn)", "Tamil (ta)",
            "Malayalam (ml)", "Bengali (bn)", "Marathi (mr)", "Gujarati (gu)", "Urdu (ur)",
            "French (fr)", "German (de)", "Spanish (es)", "Japanese (ja)", "Chinese (zh)"
        )
        val codes = arrayOf("en", "hi", "te", "kn", "ta", "ml", "bn", "mr", "gu", "ur", "fr", "de", "es", "ja", "zh")
        val current = codes.indexOf(selectedLanguage()).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Choose transcription language")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                val code = codes[which]
                modelSelectionPreferences.edit().putString("transcription_language", code).apply()
                if (code != "en") {
                    if (whisperModelManager.isInstalled()) {
                        modelSelectionPreferences.edit().putString("transcription_engine", "whisper-multilingual").apply()
                        transcriptionMessage = "Selected ${languageDisplayName(code)} with multilingual Whisper."
                    } else {
                        transcriptionMessage = "${languageDisplayName(code)} selected. Download multilingual Whisper below to enable it; English remains available."
                    }
                } else {
                    modelSelectionPreferences.edit().putString("transcription_engine", "zipformer-en").apply()
                    transcriptionMessage = "English selected. The default English Zipformer model remains active."
                }
                dialog.dismiss()
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun installWhisperModel() {
        if (whisperDownloadBusy || transcriptionBusy) return
        whisperDownloadBusy = true
        modelDownloadBusy = true
        modelDownloadPercent = 0
        modelInstallStage = "Preparing multilingual Whisper download"
        transcriptionMessage = "Downloading verified multilingual Whisper model. English remains available."
        render()
        Thread {
            try {
                whisperModelManager.install(
                    onProgress = { progress ->
                        runOnUiThread {
                            modelDownloadPercent = progress.percent.coerceIn(0, 100)
                            whisperDownloadPercent = modelDownloadPercent
                            whisperDownloadStage = progress.fileName
                            modelInstallStage = "Whisper · ${progress.fileName}"
                            if (!isFinishing) render()
                        }
                    },
                    onStage = { stage ->
                        runOnUiThread {
                            modelInstallStage = stage
                            whisperDownloadStage = stage
                            if (!isFinishing) render()
                        }
                    }
                )
                runOnUiThread {
                    whisperDownloadBusy = false
                    modelDownloadBusy = englishDownloadBusy
                    modelDownloadPercent = 100
                    whisperDownloadPercent = 100
                    whisperDownloadStage = "Installed"
                    modelInstallStage = "Whisper installed"
                    showModelSettings = true
                    modelSelectionPreferences.edit().putString("transcription_engine", "whisper-multilingual").apply()
                    transcriptionMessage = "Multilingual Whisper installed and checksum-verified. Selected language: ${languageDisplayName(selectedLanguage())}. It works offline."
                    if (!isFinishing) render()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    whisperDownloadBusy = false
                    modelDownloadBusy = englishDownloadBusy
                    whisperDownloadStage = "Failed"
                    modelInstallStage = "Whisper installation failed"
                    showModelSettings = true
                    transcriptionMessage = "Multilingual model download failed: " + (error.message ?: error.javaClass.simpleName)
                    if (!isFinishing) render()
                }
            }
        }.start()
    }

    private fun installOfflineModel() {
        if (englishDownloadBusy || transcriptionBusy) return
        englishDownloadBusy = true
        modelDownloadBusy = true
        modelDownloadPercent = 0
        modelInstallStage = "Connecting to model host"
        transcriptionMessage = "Preparing the offline English model download…"
        render()
        Thread {
            try {
                offlineModelManager.install(
                    onProgress = { percent ->
                        runOnUiThread {
                            modelDownloadPercent = percent.coerceIn(0, 100)
                            englishDownloadPercent = modelDownloadPercent
                            englishDownloadStage = "Downloading"
                            modelProgressLabel?.text = modelInstallProgressText()
                            if (!isFinishing) render()
                            modelProgressBar?.progress = modelDownloadPercent
                        }
                    },
                    onStage = { stage ->
                        runOnUiThread {
                            modelInstallStage = stage
                            modelProgressLabel?.text = modelInstallProgressText()
                            if (!isFinishing) render()
                        }
                    }
                )
                runOnUiThread {
                    englishDownloadBusy = false
                    modelDownloadBusy = whisperDownloadBusy
                    modelDownloadPercent = 100
                    englishDownloadPercent = 100
                    englishDownloadStage = "Installed"
                    modelInstallStage = "Installed"
                    modelProgressBar = null
                    modelProgressLabel = null
                    transcriptionMessage = "English model installed. You can now transcribe with Airplane mode enabled."
                    if (!isFinishing) render()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    englishDownloadBusy = false
                    modelDownloadBusy = whisperDownloadBusy
                    englishDownloadStage = "Failed"
                    modelProgressBar = null
                    modelProgressLabel = null
                    modelInstallStage = "Installation failed"
                    transcriptionMessage = "Model installation failed: " + (error.message ?: error.javaClass.simpleName)
                    if (!isFinishing) render()
                }
            }
        }.start()
    }

    private fun modelInstallProgressText(): String = when (modelInstallStage) {
        "Downloading model" -> "Downloading English model — $modelDownloadPercent%"
        "Installing model files" -> "Installing English model files…"
        "Verifying model" -> "Verifying model files…"
        "Installed" -> "English model installed."
        "Installation failed" -> "Model installation failed."
        else -> "$modelInstallStage…"
    }

    private fun transcribeSession(session: File, audioFiles: List<File>) {
        if (transcriptionBusy || modelDownloadBusy) return
        val useWhisper = selectedEngine() == "whisper-multilingual"
        val language = selectedLanguage()
        if (language != "en" && !useWhisper) {
            Toast.makeText(this, "The English Zipformer model cannot transcribe ${languageDisplayName(language)}. Install and select multilingual Whisper first.", Toast.LENGTH_LONG).show()
            return
        }
        if (useWhisper && !whisperModelManager.isInstalled()) {
            Toast.makeText(this, "Download the multilingual Whisper model first.", Toast.LENGTH_LONG).show()
            return
        }
        if (!useWhisper && !offlineModelManager.isInstalled()) {
            Toast.makeText(this, "Download the offline English model first.", Toast.LENGTH_LONG).show()
            return
        }
        val validFiles = audioFiles.filter { it.isFile && it.length() > 0L }
        if (validFiles.isEmpty()) {
            Toast.makeText(this, "No completed audio files are available to transcribe.", Toast.LENGTH_LONG).show()
            return
        }
        transcriptionBusy = true
        transcriptionMessage = "Starting local transcription…"
        showRecordings = true
        showTranscriptSessionId = null
        render()
        Thread {
            val store = getTranscriptStore()
            var sequenceOffset = 0L
            var timeOffsetMs = 0L
            var totalSegments = 0
            try {
                // Retry replaces the old transcript; each new segment is persisted immediately.
                store.deleteUneditedSegments(session.name)
                validFiles.forEachIndexed { index, audio ->
                    if (Thread.currentThread().isInterrupted) throw InterruptedException("Transcription cancelled.")
                    store.enqueueChunk(session.name, audio.absolutePath)
                    store.markChunkRunning(session.name, audio.absolutePath)
                    val activeEngine = if (useWhisper) SherpaOnnxWhisperSpeechEngine(language, "tiny-multilingual") else speechEngine
                    val activeModelDirectory = if (useWhisper) whisperModelManager.modelDirectory() else offlineModelManager.modelDirectory()
                    val result = activeEngine.transcribe(
                        audioFile = audio,
                        sessionId = session.name,
                        modelDirectory = activeModelDirectory,
                        sequenceOffset = sequenceOffset,
                        timeOffsetMs = timeOffsetMs
                    ) { segment ->
                        store.saveSegment(segment)
                        runOnUiThread {
                            transcriptionMessage = "Transcribing ${audio.name} · ${segment.sequence + 1} text segment(s) saved…"
                            if (!isFinishing) render()
                        }
                    }
                    when (result) {
                        is SpeechEngineResult.Success -> {
                            store.markChunkComplete(session.name, audio.absolutePath)
                            sequenceOffset += result.segmentCount
                            totalSegments += result.segmentCount
                        }
                        is SpeechEngineResult.Failure -> {
                            store.markChunkFailed(session.name, audio.absolutePath, result.message)
                            throw IllegalStateException(result.message)
                        }
                    }
                    timeOffsetMs += audioDurationMs(audio)
                    runOnUiThread {
                        transcriptionMessage = "Processed audio segment ${index + 1} of ${validFiles.size}…"
                        if (!isFinishing) render()
                    }
                }
                runOnUiThread {
                    transcriptionBusy = false
                    transcriptionMessage = if (totalSegments > 0) {
                        "Transcription complete: $totalSegments text segment(s) saved locally. No audio or transcript was uploaded."
                    } else {
                        "Transcription finished, but no speech was recognized. Try a clearer recording."
                    }
                    showTranscriptSessionId = session.name
                    showRecordings = false
                    if (!isFinishing) render()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    transcriptionBusy = false
                    transcriptionMessage = "Transcription failed: " + (error.message ?: error.javaClass.simpleName)
                    showRecordings = true
                    showTranscriptSessionId = null
                    if (!isFinishing) render()
                }
            }
        }.start()
    }

    private fun renderTranscript(parent: LinearLayout, sessionId: String) {
        addText(parent, "Meeting transcript", 29f, COLOR_TEXT, true)
        addSpace(parent, 5)
        addText(parent, "OFFLINE ENGLISH · SAVED LOCALLY", 11f, COLOR_PRIMARY_DARK, true)
        addSpace(parent, 12)
        val segments = runCatching { getTranscriptStore().listSegments(sessionId) }.getOrDefault(emptyList())
        if (segments.isEmpty()) {
            val empty = card(parent, COLOR_SURFACE)
            addText(empty, "No transcript text yet", 17f, COLOR_TEXT, true)
            addSpace(empty, 6)
            addText(empty, transcriptionMessage ?: "Transcribe a completed recording to see its recognized speech here.", 13f, COLOR_MUTED)
        } else {
            addText(parent, "${segments.size} saved text segment(s)", 13f, COLOR_MUTED)
            addSpace(parent, 10)
            if (summaryVisibleSessionId == sessionId) {
                val summary = DeterministicMeetingSummarizer().summarize(segments)
                val summaryCard = card(parent, COLOR_SURFACE_ALT)
                addText(summaryCard, "Offline meeting summary", 19f, COLOR_TEXT, true)
                addSpace(summaryCard, 5)
                addText(summaryCard, "Rule-based extraction · no network or LLM · evidence linked", 12f, COLOR_MUTED)
                addSpace(summaryCard, 8)
                addText(summaryCard, summary.overview, 14f, COLOR_TEXT)
                addSpace(summaryCard, 12)
                addText(summaryCard, "My action items", 18f, COLOR_TEXT, true)
                addText(summaryCard, "Profile matches are suggestions, not proof of speaker identity or ownership.", 12f, COLOR_MUTED)
                addSpace(summaryCard, 6)
                val personalProfile = loadPersonalProfile()
                val personalCandidates = PersonalActionItemClassifier().classify(summary.findings, personalProfile)
                if (personalCandidates.isEmpty()) {
                    addText(summaryCard, "No likely personal action items were detected. You can still review all evidence-linked findings below.", 13f, COLOR_MUTED)
                } else {
                    personalCandidates.forEach { candidate ->
                        val reviewKey = personalReviewKey(sessionId, candidate.finding)
                        val reviewState = profilePreferences.getString(reviewKey, "NEEDS_REVIEW") ?: "NEEDS_REVIEW"
                        val personalCard = card(summaryCard, COLOR_SURFACE)
                        addText(personalCard, candidate.category.displayName, 11f, COLOR_PRIMARY_DARK, true)
                        addSpace(personalCard, 4)
                        addText(personalCard, candidate.finding.text, 14f, COLOR_TEXT, true)
                        addSpace(personalCard, 4)
                        addText(personalCard, "Review status: " + reviewState.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, 11f, if (reviewState == "CONFIRMED") COLOR_PRIMARY_DARK else COLOR_MUTED)
                        addText(personalCard, candidate.explanation, 12f, COLOR_MUTED)
                        addText(personalCard, "Evidence · ${formatTimestamp(candidate.finding.evidence.startMs)} · “${candidate.finding.evidence.quote}”", 12f, COLOR_MUTED)
                        if (reviewState == "NEEDS_REVIEW") {
                            addSpace(personalCard, 6)
                            addButton(personalCard, "Confirm as my action item", true) {
                                profilePreferences.edit().putString(reviewKey, "CONFIRMED").apply()
                                render()
                            }
                            addSpace(personalCard, 4)
                            addButton(personalCard, "Not mine / reject", false) {
                                profilePreferences.edit().putString(reviewKey, "REJECTED").apply()
                                render()
                            }
                        } else {
                            addSpace(personalCard, 4)
                            addButton(personalCard, "Reset review", false) {
                                profilePreferences.edit().remove(reviewKey).apply()
                                render()
                            }
                        }
                    }
                }
                addSpace(summaryCard, 12)
                addText(summaryCard, "All evidence-linked findings: ${summary.findings.size}", 12f, COLOR_PRIMARY_DARK, true)
                if (summary.findings.isEmpty()) {
                    addSpace(summaryCard, 6)
                    addText(summaryCard, "No high-confidence decisions or action candidates were detected. Review the transcript manually; this does not mean the meeting had none.", 13f, COLOR_MUTED)
                } else {
                    summary.findings.forEach { finding ->
                        val findingCard = card(summaryCard, COLOR_SURFACE)
                        addText(findingCard, finding.kind.displayName, 11f, COLOR_PRIMARY_DARK, true)
                        addSpace(findingCard, 4)
                        addText(findingCard, finding.text, 14f, COLOR_TEXT, true)
                        addSpace(findingCard, 4)
                        addText(findingCard, "Confidence: ${finding.confidence.name.lowercase()}", 11f, COLOR_MUTED)
                        if (finding.ambiguityNote != null) {
                            addSpace(findingCard, 4)
                            addText(findingCard, finding.ambiguityNote, 12f, COLOR_WARNING)
                        }
                        if (finding.explicitActor != null || finding.deadlineText != null) {
                            addSpace(findingCard, 4)
                            finding.explicitActor?.let { addText(findingCard, "Explicit speaker wording: $it", 12f, COLOR_MUTED) }
                            finding.deadlineText?.let { addText(findingCard, "Deadline mentioned: $it", 12f, COLOR_MUTED) }
                        }
                        addSpace(findingCard, 6)
                        addText(findingCard, "Evidence · ${formatTimestamp(finding.evidence.startMs)}–${formatTimestamp(finding.evidence.endMs)} · segment #${finding.evidence.sequence + 1}", 11f, COLOR_PRIMARY_DARK, true)
                        addText(findingCard, "“${finding.evidence.quote}”", 12f, COLOR_MUTED)
                    }
                }
                addSpace(summaryCard, 8)
                addButton(summaryCard, "Hide summary", false) { summaryVisibleSessionId = null; render() }
            } else {
                addButton(parent, "Generate offline summary", true) {
                    summaryVisibleSessionId = sessionId
                    render()
                }
            }
            addSpace(parent, 12)
            segments.forEach { segment ->
                val item = card(parent, COLOR_SURFACE)
                addText(item, "${formatTimestamp(segment.startMs)} – ${formatTimestamp(segment.endMs)}", 11f, COLOR_PRIMARY_DARK, true)
                addSpace(item, 6)
                addText(item, segment.text, 15f, COLOR_TEXT)
                addSpace(item, 8)
                addButton(item, "Correct transcript text", false) {
                    promptSegmentCorrection(segment.sessionId, segment.sequence, segment.text)
                }
            }
        }
        if (transcriptionMessage != null) {
            addSpace(parent, 8)
            addText(parent, transcriptionMessage!!, 12f, COLOR_MUTED)
        }
        addSpace(parent, 16)
        addButton(parent, "Back to recordings", true) {
            showTranscriptSessionId = null
            showRecordings = true
            render()
        }
    }

    private fun assessOnDeviceLlm() {
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        val storage = runCatching { StatFs(filesDir.absolutePath).availableBytes }.getOrDefault(0L)
        val supportedAbis = Build.SUPPORTED_ABIS.toSet()
        val snapshot = DeviceRuntimeSnapshot(
            Build.VERSION.SDK_INT,
            supportedAbis.any { it == "arm64-v8a" || it == "armeabi-v7a" },
            memory.totalMem,
            memory.availMem,
            storage
        )
        val candidates = LocalModelRegistry().evaluate(
            ModelDeviceSnapshot(Build.VERSION.SDK_INT, supportedAbis, memory.availMem, storage)
        )
        val firstEligible = candidates.firstOrNull { it.eligible }
        val message = buildString {
            append("DEVICE SCREEN\n")
            append("Android API: ${snapshot.androidApi}\n")
            append("RAM: ${formatBytes(snapshot.totalRamBytes)} total; ${formatBytes(snapshot.availableRamBytes)} currently available\n")
            append("Free storage: ${formatBytes(snapshot.freeStorageBytes)}\n\n")
            if (firstEligible == null) {
                append("No model tier passes the current resource screen. Keep deterministic offline summaries.\n")
            } else {
                append("Smallest resource-eligible tier: ${firstEligible.profile.displayName}.\n")
                append("This is only a screening result. A verified model artifact must be installed and its runtime trial must pass before use.\n")
            }
            candidates.forEach { candidate ->
                append("\n• ").append(candidate.profile.displayName).append(": ")
                append(if (candidate.eligible) "resource-eligible candidate" else "not eligible")
                if (candidate.reasons.isNotEmpty()) append(" — ").append(candidate.reasons.joinToString(" "))
            }
            append("\n\nThe app does not force model use. This assessment does not download a model or run inference. Deterministic offline findings remain available.")
        }
        llmAssessmentMessage = message
        AlertDialog.Builder(this)
            .setTitle(if (firstEligible != null) "Smallest eligible model tier found" else "Keep deterministic offline mode")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> render() }
            .show()
    }

    private fun loadPersonalProfile(): PersonalProfile = PersonalProfile(
        preferredName = profilePreferences.getString("preferred_name", "").orEmpty(),
        aliases = profilePreferences.getString("aliases", "").orEmpty().split(',').map(String::trim).filter(String::isNotBlank),
        workContext = profilePreferences.getString("work_context", "").orEmpty()
    )

    private fun personalReviewKey(sessionId: String, finding: com.iam816anonymous.meetingsummarizer.summary.SummaryFinding): String =
        "review_${sessionId}_${finding.kind.name}_${finding.evidence.sequence}_${finding.text.hashCode()}"

    private fun promptPersonalProfile() {
        val profile = loadPersonalProfile()
        val nameField = EditText(this).apply { hint = "Preferred name (optional)"; setText(profile.preferredName); setSingleLine(true) }
        val aliasesField = EditText(this).apply { hint = "Aliases, separated by commas"; setText(profile.aliases.joinToString(", ")); setSingleLine(false); minLines = 2 }
        val contextField = EditText(this).apply { hint = "Work context / project keywords (optional)"; setText(profile.workContext); setSingleLine(false); minLines = 2 }
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            addView(nameField, matchWrapParams())
            addView(aliasesField, matchWrapParams())
            addView(contextField, matchWrapParams())
        }
        AlertDialog.Builder(this).setTitle("Personal action-item profile")
            .setMessage("Saved only on this device. Names help find possible assignments, but do not identify the speaker or prove ownership. Leave any field blank if you prefer.")
            .setView(fields).setNegativeButton("Cancel", null)
            .setPositiveButton("Save profile") { _, _ ->
                profilePreferences.edit()
                    .putString("preferred_name", nameField.text?.toString()?.trim().orEmpty())
                    .putString("aliases", aliasesField.text?.toString()?.trim().orEmpty())
                    .putString("work_context", contextField.text?.toString()?.trim().orEmpty())
                    .apply()
                Toast.makeText(this, "Personal profile saved on this device.", Toast.LENGTH_SHORT).show()
                render()
            }
            .setNeutralButton("Clear profile") { _, _ ->
                profilePreferences.edit().clear().apply()
                Toast.makeText(this, "Personal profile cleared.", Toast.LENGTH_SHORT).show()
                render()
            }.show()
    }

    private fun promptTranscriptSearch() {
        val field = EditText(this).apply {
            setSingleLine(true)
            hint = "Phrase to find"
            setText(transcriptSearchQuery)
            setSelection(text.length)
        }
        AlertDialog.Builder(this).setTitle("Search saved transcripts").setView(field)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Search") { _, _ -> transcriptSearchQuery = field.text?.toString()?.trim().orEmpty(); render() }
            .show()
    }

    private fun promptSegmentCorrection(sessionId: String, sequence: Long, currentText: String) {
        val field = EditText(this).apply {
            setText(currentText); hint = "Correct recognized words"; minLines = 3; maxLines = 8; gravity = Gravity.TOP
        }
        AlertDialog.Builder(this).setTitle("Correct transcript")
            .setMessage("Your correction is saved locally and will be preserved when transcription is retried.")
            .setView(field).setNegativeButton("Cancel", null)
            .setPositiveButton("Save correction") { _, _ ->
                val corrected = field.text?.toString().orEmpty().trim()
                val saved = runCatching { getTranscriptStore().updateSegmentText(sessionId, sequence, corrected) }.getOrDefault(false)
                Toast.makeText(this, if (saved) "Transcript correction saved." else "Correction must contain text and could not be saved.", if (saved) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                render()
            }.show()
    }

    private fun confirmDeleteMeeting(session: File) {
        AlertDialog.Builder(this).setTitle("Delete this meeting?")
            .setMessage("This permanently deletes this meeting's saved audio, transcript text, and transcription jobs from this device. The shared offline English model and other meetings are kept.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete meeting") { _, _ ->
                val recordingsRoot = File(filesDir, "recordings").canonicalFile
                val target = runCatching { session.canonicalFile }.getOrNull()
                if (target == null || target.parentFile != recordingsRoot || !target.isDirectory) {
                    Toast.makeText(this, "Could not verify the meeting folder; nothing was deleted.", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val databaseDeleted = runCatching { getTranscriptStore().deleteMeeting(session.name); true }.getOrDefault(false)
                val audioDeleted = runCatching { target.deleteRecursively() }.getOrDefault(false)
                if (databaseDeleted && audioDeleted) {
                    if (showTranscriptSessionId == session.name) showTranscriptSessionId = null
                    Toast.makeText(this, "Meeting and local audio deleted.", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this, "Deletion was incomplete. Refresh and check this meeting before retrying.", Toast.LENGTH_LONG).show()
                render()
            }.show()
    }

    private fun getTranscriptStore(): TranscriptStore =
        transcriptStore ?: TranscriptStore(this).also { transcriptStore = it }

    private fun audioDurationMs(audio: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(audio.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun formatTimestamp(milliseconds: Long): String {
        val totalSeconds = milliseconds.coerceAtLeast(0L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }

    private fun latestRecordingFiles(): List<File> {
        val root = File(filesDir, "recordings")
        val session = currentSessionId?.let { File(root, it) }
        return session?.listFiles()?.filter { it.isFile && it.extension.equals("m4a", true) }.orEmpty()
    }

    private fun readMetadata(audio: File): Properties = Properties().apply {
        val meta = File(audio.parentFile, audio.name + ".meta")
        if (meta.isFile) runCatching { meta.inputStream().use { load(it) } }
    }

    private fun playAudio(file: File) {
        stopPlayback()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { stopPlayback(); if (!isFinishing) render() }
                prepare()
                start()
            }
            currentlyPlaying = file
            Toast.makeText(this, "Playing " + file.name, Toast.LENGTH_SHORT).show()
            render()
        } catch (error: Exception) {
            stopPlayback()
            Toast.makeText(this, "Could not play audio: " + (error.message ?: "unknown error"), Toast.LENGTH_LONG).show()
        }
    }

    private fun stopPlayback() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        currentlyPlaying = null
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
    }

    private fun addFeatureCard(parent: LinearLayout, number: String, title: String, body: String) {
        val feature = card(parent, COLOR_SURFACE)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val badge = TextView(this).apply {
            text = number
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_PRIMARY_DARK)
            background = rounded(COLOR_TINT, 12)
            gravity = Gravity.CENTER
        }
        row.addView(badge, LinearLayout.LayoutParams(dp(38), dp(38)))
        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        addText(textColumn, title, 16f, COLOR_TEXT, true)
        addSpace(textColumn, 4)
        addText(textColumn, body, 13f, COLOR_MUTED)
        row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
        feature.addView(row)
        addSpace(parent, 10)
    }

    private fun addSectionTitle(parent: LinearLayout, title: String, subtitle: String) {
        addText(parent, title, 20f, COLOR_TEXT, true)
        addText(parent, subtitle, 12f, COLOR_MUTED)
        addSpace(parent, 10)
    }

    private fun card(parent: LinearLayout, color: Int): LinearLayout {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = rounded(color, 22)
        }
        parent.addView(view, matchWrapParams())
        return view
    }

    private fun addText(
        parent: LinearLayout,
        value: String,
        sizeSp: Float,
        color: Int,
        bold: Boolean = false,
        gravity: Int = Gravity.START
    ): TextView {
        val view = TextView(this).apply {
            text = value
            textSize = sizeSp
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            this.gravity = gravity
            setLineSpacing(dp(2).toFloat(), 1.04f)
        }
        parent.addView(view, wrapParams())
        return view
    }

    private fun addBullet(parent: LinearLayout, value: String, color: Int, marker: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
        }
        val bullet = TextView(this).apply {
            text = marker
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color)
        }
        row.addView(bullet, LinearLayout.LayoutParams(dp(26), -2))
        val body = TextView(this).apply {
            text = value
            textSize = 14f
            setTextColor(color)
            setLineSpacing(dp(2).toFloat(), 1.04f)
        }
        row.addView(body, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(row, wrapParams())
    }

    private fun addButton(parent: LinearLayout, label: String, primary: Boolean, action: () -> Unit) {
        val button = Button(this).apply {
            text = label
            textSize = 14f
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (primary) COLOR_WHITE else COLOR_PRIMARY_DARK)
            background = rounded(if (primary) COLOR_PRIMARY else COLOR_SURFACE, 16)
            minHeight = dp(52)
            setPadding(dp(14), dp(8), dp(14), dp(8))
            stateListAnimator = null
            setOnClickListener { action() }
        }
        parent.addView(button, matchWrapParams())
    }

    private fun addSpace(parent: LinearLayout, heightDp: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(heightDp)))
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    private fun wrapParams() = LinearLayout.LayoutParams(-2, -2)
    private fun matchWrapParams() = LinearLayout.LayoutParams(-1, -2)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val COLOR_BACKGROUND = 0xFFF5F5FA.toInt()
        const val COLOR_SURFACE = 0xFFFFFFFF.toInt()
        const val COLOR_SURFACE_ALT = 0xFFEFEEF8.toInt()
        const val COLOR_TEXT = 0xFF202033.toInt()
        const val COLOR_MUTED = 0xFF69697E.toInt()
        const val COLOR_PRIMARY = 0xFF6257D9.toInt()
        const val COLOR_PRIMARY_DARK = 0xFF28224F.toInt()
        const val COLOR_TINT = 0xFFE8E5FF.toInt()
        const val COLOR_WHITE = 0xFFFFFFFF.toInt()
        const val COLOR_WARNING = 0xFF8A4D00.toInt()
        const val COLOR_WARNING_TINT = 0xFFFFE8C5.toInt()
        const val REQUEST_MICROPHONE = 3001
    }
}
