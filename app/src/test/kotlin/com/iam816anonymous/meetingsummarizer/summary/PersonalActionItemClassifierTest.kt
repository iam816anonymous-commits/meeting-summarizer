package com.iam816anonymous.meetingsummarizer.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalActionItemClassifierTest {
    private val classifier = PersonalActionItemClassifier()

    private fun finding(text: String, kind: SummaryFindingKind = SummaryFindingKind.CANDIDATE_TASK) =
        SummaryFinding(
            kind = kind,
            text = text,
            confidence = SummaryConfidence.MEDIUM,
            evidence = SummaryEvidence("meeting-1", 2, 1200L, 2600L, text)
        )

    @Test
    fun configuredNameWithAssignmentLanguageIsOnlyPossibleDirectAssignment() {
        val result = classifier.classify(
            listOf(finding("Vivek, please review the release notes.")),
            PersonalProfile("Vivek", listOf("Vivek Reddy"))
        )
        assertEquals(PersonalActionCategory.DIRECT_ASSIGNMENT, result.single().category)
        assertTrue(result.single().explanation.contains("Confirm"))
    }

    @Test
    fun nameMentionWithoutAssignmentIsNotTreatedAsAssignment() {
        val result = classifier.classify(
            listOf(finding("Vivek joined the discussion about the release.")),
            PersonalProfile("Vivek")
        )
        assertEquals(PersonalActionCategory.NAME_MENTION, result.single().category)
    }

    @Test
    fun genericYouAssignmentRemainsUncertain() {
        val result = classifier.classify(
            listOf(finding("You should send the report tomorrow.")),
            PersonalProfile("Vivek")
        )
        assertEquals(PersonalActionCategory.POSSIBLE_ASSIGNMENT, result.single().category)
    }

    @Test
    fun firstPersonCommitmentDoesNotAssumeUserIsSpeaker() {
        val result = classifier.classify(
            listOf(finding("I will send the report.", SummaryFindingKind.COMMITMENT)),
            PersonalProfile("Vivek")
        )
        assertEquals(PersonalActionCategory.SELF_COMMITMENT_UNVERIFIED, result.single().category)
        assertTrue(result.single().explanation.contains("cannot identify"))
    }

    @Test
    fun unrelatedFindingIsNotRankedAsPersonal() {
        val result = classifier.classify(
            listOf(finding("The meeting starts at nine.")),
            PersonalProfile("Vivek")
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun workContextCanSurfaceCandidateWithoutClaimingOwnership() {
        val result = classifier.classify(
            listOf(finding("Please review the Playverse release.", SummaryFindingKind.CANDIDATE_TASK)),
            PersonalProfile(workContext = "Playverse")
        )
        assertEquals(PersonalActionCategory.WORK_CONTEXT_MATCH, result.single().category)
    }
}
