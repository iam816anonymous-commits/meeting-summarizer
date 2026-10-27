package com.iam816anonymous.meetingsummarizer.data

data class DemoActionItem(
    val title: String,
    val owner: String,
    val due: String,
    val evidence: String
)

data class DemoMeetingResult(
    val title: String,
    val durationLabel: String,
    val summary: List<String>,
    val decisions: List<String>,
    val actionItems: List<DemoActionItem>,
    val transcriptExcerpt: List<String>
)

/**
 * Deterministic fixture repository for Phase 2 UI development.
 *
 * This class does not access a microphone, network, database, or AI model.
 * All content is explicitly sample content and must never be presented as
 * output from a user's actual meeting.
 */
class DemoMeetingRepository {
    fun loadSampleResult(): DemoMeetingResult = DemoMeetingResult(
        title = "Weekly project check-in",
        durationLabel = "Sample · 18 min",
        summary = listOf(
            "The team reviewed the release timeline and remaining risks.",
            "The first test build should be reviewed before the next check-in.",
            "Two follow-ups need owners and confirmation before they are treated as commitments."
        ),
        decisions = listOf(
            "Review the first test build before the next check-in.",
            "Keep unresolved tasks marked for confirmation until an owner is explicit."
        ),
        actionItems = listOf(
            DemoActionItem(
                title = "Review the first test build",
                owner = "You · sample assignment",
                due = "Before next check-in",
                evidence = "Sample evidence: “Please review the first test build before we meet again.”"
            ),
            DemoActionItem(
                title = "Confirm the release checklist owner",
                owner = "Needs confirmation",
                due = "Not specified",
                evidence = "Sample evidence: “Someone should confirm who owns the release checklist.”"
            )
        ),
        transcriptExcerpt = listOf(
            "Speaker 1: Let's review the release timeline and the remaining risks.",
            "Speaker 2: Please review the first test build before we meet again.",
            "Speaker 1: Someone should confirm who owns the release checklist."
        )
    )
}
