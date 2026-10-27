package com.iam816anonymous.meetingsummarizer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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

    private val recordingStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != RecordingService.ACTION_STATE) return
            recordingStatus = intent.getStringExtra(RecordingService.EXTRA_STATE) ?: recordingStatus
            recordingError = intent.getStringExtra(RecordingService.EXTRA_ERROR)
            recordingActive = recordingStatus == RecordingLifecycleState.RECORDING.name ||
                recordingStatus == RecordingLifecycleState.STOPPING.name
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RecordingService.recoverInterruptedSegments(this)
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

        when (workflowState.screen) {
            MeetingScreenState.IDLE -> renderHome(content)
            MeetingScreenState.DEMO_ACTIVE -> renderDemoActive(content)
            MeetingScreenState.RESULTS_READY -> renderResults(content)
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

        addSpace(content, 20)
        addText(content, "PHASE 3 · EXPLICIT, LOCAL MICROPHONE RECORDING", 11f, COLOR_MUTED, true, Gravity.CENTER)
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
        addText(parent, "Make every meeting\nmore actionable.", 31f, COLOR_TEXT, true)
        addSpace(parent, 8)
        addText(parent, "A focused place for decisions, follow-ups, and the things that matter to you.", 15f, COLOR_MUTED)
        addSpace(parent, 22)

        val hero = card(parent, COLOR_PRIMARY_DARK)
        addText(hero, "YOUR NEXT MEETING", 11f, 0xFFC9C5FF.toInt(), true)
        addSpace(hero, 10)
        addText(hero, "Capture first. Understand next.", 22f, COLOR_WHITE, true)
        addSpace(hero, 8)
        addText(hero, "Start a real local recording when you are ready. You remain in control, and a persistent notification provides a stop action.", 14f, 0xFFE6E4FF.toInt())
        addSpace(hero, 18)
        if (recordingActive) {
            addText(hero, "● RECORDING · " + recordingStatus, 13f, 0xFFFFD4A3.toInt(), true)
            addSpace(hero, 10)
            addButton(hero, "Stop recording", true) { stopRecordingService() }
        } else {
            addButton(hero, "Start real recording", true) { confirmAndStartRecording() }
        }
        if (recordingError != null) {
            addSpace(hero, 8)
            addText(hero, recordingError!!, 13f, 0xFFFFC7C7.toInt())
        }
        addSpace(hero, 18)
        addButton(hero, "Start demo session", true) {
            workflowState = MeetingWorkflowReducer.reduce(
                workflowState,
                MeetingWorkflowAction.StartDemo(System.currentTimeMillis())
            )
            render()
        }
        addSpace(hero, 10)
        addButton(hero, "Preview sample summary", false) {
            workflowState = MeetingWorkflowReducer.reduce(workflowState, MeetingWorkflowAction.OpenSampleResults)
            render()
        }

        addSpace(parent, 20)
        addText(parent, "WHAT YOU'LL GET", 12f, COLOR_MUTED, true)
        addSpace(parent, 10)
        addFeatureCard(parent, "01", "Meeting summary", "The main discussion, decisions, and open questions in one place.")
        addFeatureCard(parent, "02", "My action items", "Your follow-ups first, with evidence and uncertainty clearly shown.")
        addFeatureCard(parent, "03", "Offline-first", "The core experience is designed to work without a cloud account.")
        val notice = card(parent, COLOR_SURFACE_ALT)
        addText(notice, "Privacy by default", 16f, COLOR_TEXT, true)
        addSpace(notice, 5)
        addText(notice, "Recording requires your explicit confirmation and microphone permission. Audio segments stay in app-private storage; no audio is uploaded.", 13f, COLOR_MUTED)
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
