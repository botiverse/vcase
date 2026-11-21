package dev.vcase.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The manifest the React adapter (`packages/react`) wrote for `samples/react` reads, validates,
 * expands and re-encodes byte for byte here: one format for every adapter. The React side runs
 * the mirror test against `samples/compose-desktop/vcase.manifest.json`.
 */
class ReactManifestCompatTest {
    // Gradle runs tests in the module directory.
    private val sample = File("../samples/react")

    @Test
    fun reactSampleManifestIsVcase1() {
        val file = File(sample, VCaseManifest.FILE_NAME)
        val text = file.readText()
        val manifest = VCaseJson.decodeManifest(text)
        assertEquals(emptyList(), ManifestValidation.problems(manifest))
        assertEquals(text, VCaseJson.encode(manifest))
        assertEquals(manifest, manifest.sorted())
    }

    @Test
    fun reactSampleGoldensAreTheKotlinExpansion() {
        val manifest = VCaseJson.read(File(sample, VCaseManifest.FILE_NAME))
        val goldens = GoldenStore(File(sample, "goldens"), "web-chromium")
        assertEquals(goldens.stems(), AxisExpansion.expand(manifest).map { it.fileStem }.toSet())
    }
}
