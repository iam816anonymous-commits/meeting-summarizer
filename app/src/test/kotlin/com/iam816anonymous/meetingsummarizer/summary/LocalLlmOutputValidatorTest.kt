package com.iam816anonymous.meetingsummarizer.summary

import org.junit.Assert.*
import org.junit.Test

class LocalLlmOutputValidatorTest {
    private val validator = LocalLlmOutputValidator()
    @Test fun rejectsBlankAndOversizedOutput() {
        assertTrue(validator.validate(" ", setOf("source")) is LocalLlmOutputValidator.Validation.Rejected)
        assertTrue(validator.validate("x".repeat(20), setOf("source"), 10) is LocalLlmOutputValidator.Validation.Rejected)
    }
    @Test fun rejectsOutputWithoutEvidenceAnchor() {
        assertTrue(validator.validate("The team decided to launch.", setOf("QA must test the build")) is LocalLlmOutputValidator.Validation.Rejected)
    }
    @Test fun acceptsOutputContainingEvidenceAnchor() {
        assertTrue(validator.validate("Action item: QA must test the build by Friday.", setOf("QA must test the build by Friday")) is LocalLlmOutputValidator.Validation.Accepted)
    }
}
