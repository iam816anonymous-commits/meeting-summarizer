package com.iam816anonymous.meetingsummarizer

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView

/**
 * Phase 1 smoke-test screen only.
 * Recording, transcription and summarization are intentionally not implemented.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val message = TextView(this).apply {
            text = getString(R.string.foundation_message)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            contentDescription = getString(R.string.foundation_message)
        }

        setContentView(message)
    }
}
