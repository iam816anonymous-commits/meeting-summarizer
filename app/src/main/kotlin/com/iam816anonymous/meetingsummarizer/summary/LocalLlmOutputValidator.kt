package com.iam816anonymous.meetingsummarizer.summary

/** Conservative output guard. Evidence matching reduces risk but does not prove factuality. */
class LocalLlmOutputValidator {
    fun validate(output: String, allowedEvidenceQuotes: Set<String>, maxCharacters: Int = 8_000): Validation {
        val normalized = output.trim()
        if (normalized.isEmpty()) return Validation.Rejected("Empty model output.")
        if (normalized.length > maxCharacters) return Validation.Rejected("Model output exceeded the output limit.")
        if (allowedEvidenceQuotes.isEmpty()) return Validation.Rejected("No transcript evidence was supplied.")
        val hasEvidence = allowedEvidenceQuotes.any { quote -> quote.isNotBlank() && normalized.contains(quote.take(48), ignoreCase = true) }
        if (!hasEvidence) return Validation.Rejected("Output could not be linked to supplied transcript evidence.")
        return Validation.Accepted(normalized)
    }
    sealed interface Validation {
        data class Accepted(val text: String) : Validation
        data class Rejected(val reason: String) : Validation
    }
}
