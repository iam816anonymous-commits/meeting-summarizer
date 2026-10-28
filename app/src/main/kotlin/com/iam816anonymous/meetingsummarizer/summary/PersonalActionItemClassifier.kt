package com.iam816anonymous.meetingsummarizer.summary

import java.util.Locale

/** User-controlled, on-device identity hints. They never establish speaker identity by themselves. */
data class PersonalProfile(
    val preferredName: String = "",
    val aliases: List<String> = emptyList(),
    val workContext: String = ""
) {
    fun allNames(): List<String> = (listOf(preferredName) + aliases)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase(Locale.ROOT) }
}

enum class PersonalActionCategory(val displayName: String) {
    DIRECT_ASSIGNMENT("Possible direct assignment"),
    SELF_COMMITMENT_UNVERIFIED("Speaker commitment · identity unverified"),
    NAME_MENTION("Name mention · not necessarily assigned"),
    POSSIBLE_ASSIGNMENT("Possible assignment · needs confirmation"),
    WORK_CONTEXT_MATCH("Work-context match · review relevance")
}

data class PersonalActionCandidate(
    val finding: SummaryFinding,
    val category: PersonalActionCategory,
    val explanation: String
)

/**
 * Conservative local ranking. The classifier does not infer that first-person speech belongs
 * to the configured user and never promotes a name mention to a confirmed assignment.
 */
class PersonalActionItemClassifier {
    fun classify(findings: List<SummaryFinding>, profile: PersonalProfile): List<PersonalActionCandidate> {
        val names = profile.allNames()
        val contextTerms = profile.workContext.split(Regex("[,;\\n]+"))
            .map(String::trim).filter { it.length >= 3 }
        return findings.mapNotNull { finding ->
            val text = finding.text
            val hasName = names.any { name ->
                Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(name) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
                    .containsMatchIn(text)
            }
            val explicitNamedAssignment = hasName && NAMED_ASSIGNMENT.containsMatchIn(text)
            val genericYouAssignment = GENERIC_YOU_ASSIGNMENT.containsMatchIn(text)
            val firstPersonCommitment = FIRST_PERSON_COMMITMENT.containsMatchIn(text)
            val contextMatch = contextTerms.any { term ->
                Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(term) + "(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
                    .containsMatchIn(text)
            }
            when {
                explicitNamedAssignment -> PersonalActionCandidate(
                    finding, PersonalActionCategory.DIRECT_ASSIGNMENT,
                    "A configured name appears with assignment wording. Confirm it refers to you before acting."
                )
                genericYouAssignment -> PersonalActionCandidate(
                    finding, PersonalActionCategory.POSSIBLE_ASSIGNMENT,
                    "The transcript says “you”, but the addressee is not verified. Confirm ownership manually."
                )
                firstPersonCommitment -> PersonalActionCandidate(
                    finding, PersonalActionCategory.SELF_COMMITMENT_UNVERIFIED,
                    "A speaker says “I/we will”; the app cannot identify that speaker as you."
                )
                hasName -> PersonalActionCandidate(
                    finding, PersonalActionCategory.NAME_MENTION,
                    "A configured name is mentioned, but no direct assignment is established."
                )
                contextMatch && finding.kind in setOf(
                    SummaryFindingKind.CANDIDATE_TASK,
                    SummaryFindingKind.COMMITMENT,
                    SummaryFindingKind.DECISION,
                    SummaryFindingKind.DEADLINE
                ) -> PersonalActionCandidate(
                    finding, PersonalActionCategory.WORK_CONTEXT_MATCH,
                    "This item matches a work-context keyword; relevance and ownership still need review."
                )
                else -> null
            }
        }.distinctBy { it.finding.kind.name + ":" + it.finding.evidence.sessionId + ":" + it.finding.evidence.sequence + ":" + it.category.name }
    }

    private companion object {
        val NAMED_ASSIGNMENT = Regex("\\b(?:please|you should|you need to|must|will|can you|could you|needs to|should)\\b", RegexOption.IGNORE_CASE)
        val GENERIC_YOU_ASSIGNMENT = Regex("\\b(?:you should|you need to|you must|you will|can you|could you|please make sure you|your action item is)\\b", RegexOption.IGNORE_CASE)
        val FIRST_PERSON_COMMITMENT = Regex("\\b(?:i will|i'll|i am going to|i'm going to|we will|we'll|we are going to|we're going to|i commit to)\\b", RegexOption.IGNORE_CASE)
    }
}
