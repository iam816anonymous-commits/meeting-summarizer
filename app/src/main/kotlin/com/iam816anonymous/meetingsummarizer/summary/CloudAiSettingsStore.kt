package com.iam816anonymous.meetingsummarizer.summary

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Provider key is encrypted with an AES/GCM key generated inside Android Keystore. */
class CloudAiSettingsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cloud_ai_settings", Context.MODE_PRIVATE)
    fun endpoint() = prefs.getString("endpoint", DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
    fun model() = prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
    fun isEnabled() = prefs.getBoolean("enabled", false)
    fun hasApiKey() = prefs.getString("encrypted_api_key", null)?.isNotBlank() == true
    fun providerLabel() = runCatching { java.net.URL(endpoint()).host }.getOrDefault("configured provider")

    fun save(endpoint: String, model: String, apiKey: String, enabled: Boolean) {
        validateEndpoint(endpoint.trim())
        require(model.trim().isNotBlank() && model.trim().length <= 160) { "Model name must be 1–160 characters." }
        require(apiKey.length <= 512) { "API key is too long." }
        val edit = prefs.edit().putString("endpoint", endpoint.trim()).putString("model", model.trim()).putBoolean("enabled", enabled)
        if (apiKey.isNotBlank()) edit.putString("encrypted_api_key", encrypt(apiKey.trim()))
        edit.apply()
    }
    fun clearApiKey() { prefs.edit().remove("encrypted_api_key").putBoolean("enabled", false).apply() }
    fun readApiKey(): String = prefs.getString("encrypted_api_key", null)?.let(::decrypt).orEmpty()

    private fun validateEndpoint(value: String) {
        require(value.length <= 1000) { "Endpoint URL is too long." }
        val url = java.net.URL(value)
        require(url.protocol.equals("https", true) && url.host.isNotBlank() && url.userInfo == null && url.port != 80 && url.query == null && url.ref == null) {
            "Use an HTTPS endpoint without embedded credentials, query parameters or fragments."
        }
    }
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val data = ByteBuffer.allocate(4 + cipher.iv.size + encrypted.size).putInt(cipher.iv.size).put(cipher.iv).put(encrypted).array()
        return Base64.encodeToString(data, Base64.NO_WRAP)
    }
    private fun decrypt(value: String): String {
        val buffer = ByteBuffer.wrap(Base64.decode(value, Base64.NO_WRAP))
        val ivLength = buffer.int
        require(ivLength in 12..16 && buffer.remaining() > ivLength) { "Saved key is corrupt. Save the key again." }
        val iv = ByteArray(ivLength).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
    companion object {
        const val DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        private const val ALIAS = "meeting_summarizer_cloud_ai_key_v1"
    }
}
