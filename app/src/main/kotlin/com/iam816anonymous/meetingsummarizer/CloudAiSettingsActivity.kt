package com.iam816anonymous.meetingsummarizer

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.iam816anonymous.meetingsummarizer.summary.CloudAiSettingsStore

/** Settings screen; saving configuration does not send data. */
class CloudAiSettingsActivity : Activity() {
    private lateinit var store: CloudAiSettingsStore
    private lateinit var endpoint: EditText
    private lateinit var model: EditText
    private lateinit var key: EditText
    private lateinit var enabled: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = CloudAiSettingsStore(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 28, 28, 32) }
        val scroll = ScrollView(this)
        scroll.addView(root, ViewGroup.LayoutParams(-1, -2))
        root.addView(TextView(this).apply { text = "Cloud AI · Privacy & settings"; textSize = 25f })
        root.addView(TextView(this).apply {
            text = "OFF by default. Configure an OpenAI-compatible HTTPS chat-completions endpoint, model and your own key. The key is encrypted with Android Keystore; this screen does not send data."
            textSize = 14f; setPadding(0, 18, 0, 18)
        })
        endpoint = addField(root, "HTTPS endpoint", store.endpoint())
        model = addField(root, "Model name", store.model())
        key = addField(root, "API key (leave blank to keep saved key)", "")
        key.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        root.addView(TextView(this).apply { text = if (store.hasApiKey()) "API key saved (encrypted)" else "No API key saved"; textSize = 12f; setPadding(0, 8, 0, 8) })
        enabled = CheckBox(this).apply { text = "Enable cloud processing (still asks per meeting)"; isChecked = store.isEnabled() }
        root.addView(enabled)
        root.addView(TextView(this).apply {
            text = "Before every request, review the exact transcript payload and confirm. Common email/phone patterns are redacted, but not all sensitive information can be detected. Raw audio is never sent. Provider retention terms still apply."
            textSize = 13f; setPadding(0, 12, 0, 18)
        })
        root.addView(Button(this).apply {
            text = "Save settings"
            setOnClickListener {
                try {
                    if (enabled.isChecked && key.text.isBlank() && !store.hasApiKey()) throw IllegalArgumentException("Enter an API key before enabling cloud processing.")
                    store.save(endpoint.text.toString(), model.text.toString(), key.text.toString(), enabled.isChecked)
                    Toast.makeText(this@CloudAiSettingsActivity, "Settings saved.", Toast.LENGTH_LONG).show()
                    finish()
                } catch (e: Exception) {
                    AlertDialog.Builder(this@CloudAiSettingsActivity).setTitle("Settings not saved").setMessage(e.message ?: "Check the settings.").setPositiveButton("OK", null).show()
                }
            }
        })
        root.addView(Button(this).apply {
            text = "Clear saved key and disable cloud AI"
            setOnClickListener {
                AlertDialog.Builder(this@CloudAiSettingsActivity).setTitle("Clear saved key?")
                    .setMessage("Delete the encrypted key and disable cloud processing?")
                    .setNegativeButton("Cancel", null).setPositiveButton("Clear") { _, _ ->
                        store.clearApiKey(); Toast.makeText(this@CloudAiSettingsActivity, "Key cleared.", Toast.LENGTH_LONG).show(); finish()
                    }.show()
            }
        })
        root.addView(Button(this).apply { text = "Back"; setOnClickListener { finish() } })
        setContentView(scroll)
    }
    private fun addField(root: LinearLayout, label: String, value: String): EditText {
        root.addView(TextView(this).apply { text = label; textSize = 13f; setPadding(0, 8, 0, 4) })
        val field = EditText(this).apply { setSingleLine(true); setText(value); textSize = 15f }
        root.addView(field, ViewGroup.LayoutParams(-1, -2))
        return field
    }
}
