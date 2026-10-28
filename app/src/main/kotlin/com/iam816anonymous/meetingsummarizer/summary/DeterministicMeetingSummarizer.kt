package com.iam816anonymous.meetingsummarizer.summary

import com.iam816anonymous.meetingsummarizer.transcription.TranscriptSegment
import java.util.Locale

/** Conservative, fully offline rule-based extraction. Every finding retains its source segment. */
class DeterministicMeetingSummarizer {
    fun summarize(segments: List<TranscriptSegment>): MeetingSummary {
        val findings = linkedMapOf<String, SummaryFinding>()
        val ordered = segments.filter { it.isFinal && it.text.isNotBlank() }
            .sortedWith(compareBy<TranscriptSegment> { it.startMs }.thenBy { it.sequence })
        ordered.forEach { segment ->
            splitSentences(segment.text).forEach { sentence ->
                val normalized = normalize(sentence)
                if (normalized.isEmpty() || isNegatedAction(sentence)) return@forEach
                val tentative = TENTATIVE_PATTERN.containsMatchIn(sentence)
                val question = sentence.trim().endsWith("?") || QUESTION_PATTERN.containsMatchIn(sentence.trim())
                val decision = DECISION_PATTERN.containsMatchIn(sentence)
                val commitment = COMMITMENT_PATTERN.containsMatchIn(sentence)
                val task = TASK_PATTERN.containsMatchIn(sentence)
                val deadline = DEADLINE_PATTERN.find(sentence)?.value?.trim()
                val ambiguous = AMBIGUOUS_PRONOUN_PATTERN.containsMatchIn(sentence)
                val actor = EXPLICIT_ACTOR_PATTERN.find(sentence)?.value?.trim()
                val evidence = SummaryEvidence(
                    sessionId = segment.sessionId,
                    sequence = segment.sequence,
                    startMs = segment.startMs,
                    endMs = segment.endMs,
                    quote = sentence.trim()
                )
                val kinds = when {
                    tentative -> listOf(SummaryFindingKind.TENTATIVE_PROPOSAL)
                    else -> buildList {
                        if (decision) add(SummaryFindingKind.DECISION)
                        if (deadline != null) add(SummaryFindingKind.DEADLINE)
                        if (commitment) add(SummaryFindingKind.COMMITMENT)
                        if (question) add(SummaryFindingKind.QUESTION)
                        if (task && !question && !decision && !commitment) add(SummaryFindingKind.CANDIDATE_TASK)
                    }
                }
                kinds.forEach { kind ->
                    val key = kind.name + ":" + normalized
                    if (!findings.containsKey(key)) {
                        val confidence = when {
                            tentative || ambiguous -> SummaryConfidence.LOW
                            kind == SummaryFindingKind.DECISION || kind == SummaryFindingKind.COMMITMENT -> SummaryConfidence.HIGH
                            kind == SummaryFindingKind.DEADLINE || kind == SummaryFindingKind.QUESTION -> SummaryConfidence.MEDIUM
                            else -> SummaryConfidence.MEDIUM
                        }
                        val ambiguityNote = when {
                            tentative -> "Tentative language detected; this is not a confirmed decision or assignment."
                            ambiguous -> "Pronoun/reference is ambiguous. Owner or target is not inferred."
                            kind == SummaryFindingKind.CANDIDATE_TASK -> "Candidate task only; confirm whether it was assigned."
                            else -> null
                        }
                        findings[key] = SummaryFinding(
                            kind = kind,
                            text = sentence.trim(),
                            confidence = confidence,
                            evidence = evidence,
                            explicitActor = actor,
                            deadlineText = deadline,
                            ambiguityNote = ambiguityNote
                        )
                    }
                }
            }
        }
        val result = findings.values.toList()
        val counts = result.groupingBy { it.kind }.eachCount()
        val overview = if (result.isEmpty()) {
            "No rule-matched findings were extracted from the saved transcript."
        } else {
            listOf(
                counts[SummaryFindingKind.DECISION]?.let { "$it decision(s)" },
                counts[SummaryFindingKind.COMMITMENT]?.let { "$it commitment(s)" },
                counts[SummaryFindingKind.DEADLINE]?.let { "$it deadline mention(s)" },
                counts[SummaryFindingKind.CANDIDATE_TASK]?.let { "$it candidate task(s)" },
                counts[SummaryFindingKind.QUESTION]?.let { "$it question(s)" },
                counts[SummaryFindingKind.TENTATIVE_PROPOSAL]?.let { "$it tentative proposal(s)" }
            ).filterNotNull().joinToString(" · ")
        }
        return MeetingSummary(overview = overview, findings = result)
    }

    private fun splitSentences(text: String): List<String> {
        // ASR often returns a single all-caps chunk with no punctuation.
        val boundary = Regex(
            "(?<=[.!?])\\s+|\\n+|\\s+(?=(?:we need to\\b|i need the\\b|for the development team\\b|qa[, ]|all documentation\\b|let.?s reconvene\\b|the primary focus is\\b))",
            RegexOption.IGNORE_CASE
        )
        return text.split(boundary).map(String::trim).filter(String::isNotEmpty)
    }

    private fun normalize(text: String): String =
        text.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun isNegatedAction(text: String): Boolean =
        NEGATION_PATTERN.containsMatchIn(text) &&
            (COMMITMENT_PATTERN.containsMatchIn(text) || TASK_PATTERN.containsMatchIn(text) ||
                DECISION_PATTERN.containsMatchIn(text) || DEADLINE_PATTERN.containsMatchIn(text))

    private companion object {
        val NEGATION_PATTERN = Regex("\\b(?:not|don't|do not|doesn't|does not|didn't|did not|won't|will not|can't|cannot|couldn't|could not|shouldn't|should not|never|no need to)\\b", RegexOption.IGNORE_CASE)
        val TENTATIVE_PATTERN = Regex("\\b(?:maybe|perhaps|possibly|might|could consider|consider|tentatively|proposal|what if|we could|we might|it may be worth)\\b", RegexOption.IGNORE_CASE)
        val DECISION_PATTERN = Regex("\\b(?:decided|decision is|agreed to|we agreed|agreed that|approved|finalized|will proceed with|the plan is to)\\b", RegexOption.IGNORE_CASE)
        val COMMITMENT_PATTERN = Regex("\\b(?:i will|i'll|we will|we'll|i commit to|i promise to|i am going to|i'm going to|we are going to|we're going to)\\b", RegexOption.IGNORE_CASE)
        val TASK_PATTERN = Regex("\\b(?:please|need to|needs to|must|should|action item|follow up|follow-up|prepare|send|review|update|create|check|confirm|schedule|share|finish|complete|investigate|write|call|email|deliver|submit|document|test|deploy|prioritize|prioritise|stabilize|stabilizing|stabilise|finalize|finalise|migrate|migrating|reconvene|begin running|primary focus)\\b", RegexOption.IGNORE_CASE)
        val QUESTION_PATTERN = Regex("^(?:who|what|when|where|why|how|can we|could we|should we|do we|does anyone|is anyone)\\b", RegexOption.IGNORE_CASE)
        val DEADLINE_PATTERN = Regex("\\b(?:by (?:today|tomorrow|monday|tuesday|wednesday|thursday|friday|saturday|sunday|end of day|eod|next week|\\d{1,2}(?:st|nd|rd|th)?(?:\\s+[a-z]+)?|\\d{4}-\\d{2}-\\d{2})|due (?:on|by) [^,.;!?]+|deadline is [^,.;!?]+|no later than [^,.;!?]+)\\b", RegexOption.IGNORE_CASE)
        val AMBIGUOUS_PRONOUN_PATTERN = Regex("\\b(?:he|she|they|them|their|it|this|that|those|these)\\b", RegexOption.IGNORE_CASE)
        val EXPLICIT_ACTOR_PATTERN = Regex("^(?:I(?:'ll| will| am going to|'m going to)|We(?:'ll| will| are going to|'re going to))\\b", RegexOption.IGNORE_CASE)
    }
}

enum class SummaryFindingKind(val displayName: String) {
    DECISION("Decision"),
    DEADLINE("Deadline mentioned"),
    COMMITMENT("Commitment"),
    QUESTION("Open question"),
    CANDIDATE_TASK("Candidate task"),
    TENTATIVE_PROPOSAL("Tentative proposal")
}

enum class SummaryConfidence { HIGH, MEDIUM, LOW }

data class SummaryEvidence(
    val sessionId: String,
    val sequence: Long,
    val startMs: Long,
    val endMs: Long,
    val quote: String
)

data class SummaryFinding(
    val kind: SummaryFindingKind,
    val text: String,
    val confidence: SummaryConfidence,
    val evidence: SummaryEvidence,
    val explicitActor: String? = null,
    val deadlineText: String? = null,
    val ambiguityNote: String? = null
)

data class MeetingSummary(val overview: String, val findings: List<SummaryFinding>)
