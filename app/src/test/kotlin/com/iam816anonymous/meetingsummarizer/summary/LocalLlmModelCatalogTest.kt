package com.iam816anonymous.meetingsummarizer.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalLlmModelCatalogTest {
    @Test fun everyCatalogArtifactHasPinnedHttpsMetadata() {
        assertEquals(2, LocalLlmModelCatalog.models.size)
        LocalLlmModelCatalog.models.forEach { model ->
            assertTrue(model.downloadUrl.startsWith("https://huggingface.co/"))
            assertEquals(347_251_840L, model.expectedBytes)
            assertTrue(model.sha256.matches(Regex("^[a-f0-9]{64}$")))
            assertEquals("Apache-2.0", model.license)
            assertTrue(model.fileName.endsWith(".litertlm"))
        }
    }

    @Test fun catalogVariantsUseDistinctFilesAndChecksums() {
        val models = LocalLlmModelCatalog.models
        assertFalse(models[0].fileName == models[1].fileName)
        assertFalse(models[0].sha256 == models[1].sha256)
        assertTrue(models.all { it.id.isNotBlank() })
    }

    @Test fun installedRequiresExactFileSize() {
        val model = LocalLlmModelCatalog.models.first()
        val directory = createTempDir(prefix = "llm-catalog-test")
        try {
            val file = model.installedFile(directory)
            file.writeBytes(ByteArray(12))
            assertFalse(model.isInstalled(directory))
            file.delete()
        } finally {
            directory.deleteRecursively()
        }
    }
}
