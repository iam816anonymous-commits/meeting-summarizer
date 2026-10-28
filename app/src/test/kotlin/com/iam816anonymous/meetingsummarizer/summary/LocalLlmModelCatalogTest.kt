package com.iam816anonymous.meetingsummarizer.summary

import org.junit.Assert.*
import org.junit.Test

class LocalLlmModelCatalogTest {
    @Test fun catalogHasTwoDirectDownloadOptionsInEveryResourceTier() {
        assertEquals(10, LocalLlmModelCatalog.models.size)
        assertEquals(setOf("Very small", "Small", "Medium", "Large", "Very large"), LocalLlmModelCatalog.models.map { it.tierLabel }.toSet())
        LocalLlmModelCatalog.models.groupBy { it.tierLabel }.forEach { (_, models) ->
            assertEquals(2, models.size)
        }
    }

    @Test fun everyArtifactHasPinnedHttpsMetadataAndValidDigest() {
        LocalLlmModelCatalog.models.forEach { model ->
            assertTrue(model.downloadUrl.startsWith("https://huggingface.co/"))
            assertTrue(model.downloadUrl.contains("/resolve/"))
            assertTrue(model.expectedBytes > 0L)
            assertTrue(model.minimumBytes > 0L && model.minimumBytes <= model.expectedBytes)
            assertTrue(model.sha256.matches(Regex("^[a-f0-9]{64}$")))
            assertTrue(model.license.isNotBlank())
            assertTrue(model.fileName.endsWith(".litertlm"))
            assertTrue(model.supportedLanguages.isNotEmpty())
        }
    }

    @Test fun modelVariantsHaveUniqueIdsFilesAndHashes() {
        val models = LocalLlmModelCatalog.models
        assertEquals(models.size, models.map { it.id }.toSet().size)
        assertEquals(models.size, models.map { it.fileName }.toSet().size)
        assertEquals(models.size, models.map { it.sha256 }.toSet().size)
    }

    @Test fun unsupportedLanguagesAreNotRecommended() {
        val candidates = LocalLlmModelCatalog.recommendations(
            languageCode = "xx",
            androidApi = 35,
            totalRamBytes = 8L * 1024 * 1024 * 1024,
            availableRamBytes = 7L * 1024 * 1024 * 1024,
            freeStorageBytes = 10L * 1024 * 1024 * 1024
        )
        assertTrue(candidates.isEmpty())
    }

    @Test fun recommendationsPreferSmallestEligibleModelsAndRespectResourceLimits() {
        val candidates = LocalLlmModelCatalog.recommendations(
            languageCode = "en",
            androidApi = 35,
            totalRamBytes = 4L * 1024 * 1024 * 1024,
            availableRamBytes = 3L * 1024 * 1024 * 1024,
            freeStorageBytes = 4L * 1024 * 1024 * 1024
        )
        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.all { it.tierLabel !in setOf("Medium", "Large", "Very large") })
        assertEquals("Very small", candidates.first().tierLabel)
    }

    @Test fun largeAndVeryLargeTiersRequireHighResourcesAndEnoughStorage() {
        val highEnd = LocalLlmModelCatalog.recommendations(
            languageCode = "en",
            androidApi = 35,
            totalRamBytes = 32L * 1024 * 1024 * 1024,
            availableRamBytes = 20L * 1024 * 1024 * 1024,
            freeStorageBytes = 20L * 1024 * 1024 * 1024
        )
        assertEquals(2, highEnd.count { it.tierLabel == "Large" })
        assertEquals(2, highEnd.count { it.tierLabel == "Very large" })

        val storageConstrained = LocalLlmModelCatalog.recommendations(
            languageCode = "en",
            androidApi = 35,
            totalRamBytes = 32L * 1024 * 1024 * 1024,
            availableRamBytes = 20L * 1024 * 1024 * 1024,
            freeStorageBytes = 3L * 1024 * 1024 * 1024
        )
        assertTrue(storageConstrained.none { it.tierLabel == "Large" || it.tierLabel == "Very large" })
    }

    @Test fun installedCheckRejectsFilesOutsideCatalogSizeBounds() {
        val model = LocalLlmModelCatalog.models.first()
        val directory = java.nio.file.Files.createTempDirectory("llm-catalog-test").toFile()
        try {
            val file = model.installedFile(directory)
            file.writeBytes(ByteArray(12))
            assertFalse(model.isInstalled(directory))
        } finally {
            directory.deleteRecursively()
        }
    }
}
