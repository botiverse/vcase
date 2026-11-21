package dev.vcase.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Per-case capture settings (`capture`) and viewport size classes: optional, absent while unset. */
class CaseCaptureTest {
    private val wide = ManifestViewport("wide", 1200, 2400, 2f, optIn = true, sizeClass = "wide")
    private val defaults = DEFAULTS.copy(viewport = DEFAULTS.viewport + wide)

    @Test
    fun unsetSettingsAreNotWritten() {
        val text = VCaseJson.encode(VCaseManifest(project = "p", defaults = DEFAULTS, cases = listOf(case("ds.a.b"))))
        assertFalse("capture" in text, text)
        assertFalse("sizeClass" in text, text)
    }

    @Test
    fun captureAndSizeClassRoundTrip() {
        val c = case("ds.a.b", mapOf("viewport" to listOf("wide"))).copy(capture = ManifestCapture(maxWidth = 1600, settleFrames = 2, frameIntervalMs = 0))
        val m = VCaseManifest(project = "p", defaults = defaults, cases = listOf(c))
        val text = VCaseJson.encode(m)
        assertTrue("\"sizeClass\": \"wide\"" in text, text)
        assertTrue("\"maxWidth\": 1600" in text && "\"settleFrames\": 2" in text && "\"frameIntervalMs\": 0" in text, text)
        assertFalse("maxHeight" in text, text)
        assertEquals(m, VCaseJson.decodeManifest(text))
        assertTrue(ManifestValidation.problems(m).isEmpty(), ManifestValidation.problems(m).toString())
    }

    @Test
    fun sizeClassReachesTheVariant() {
        val v = AxisExpansion.expand(case("ds.a.b", mapOf("viewport" to listOf("phone", "wide"))), defaults)
        assertEquals(listOf(null, "wide"), v.filter { it.axes.theme == "light" }.map { it.axes.viewport.sizeClass })
        // The size class is not an axis of its own: names and metadata are unchanged.
        assertEquals(listOf("light,phone", "light,wide", "dark,phone", "dark,wide"), v.map { it.name })
        assertFalse("sizeClass" in v[1].axisMap())
    }

    @Test
    fun frameTimes() {
        assertEquals(listOf(0L, 0L, 0L), (0..2).map { ManifestCapture(frameIntervalMs = 0).frameTimeNanos(it) })
        assertEquals(listOf(0L, 16_000_000L, 32_000_000L), (0..2).map { ManifestCapture(frameIntervalMs = 16).frameTimeNanos(it) })
        assertEquals(null, ManifestCapture(settleFrames = 2).frameTimeNanos(1))
    }

    @Test
    fun validationOfCaptureSettings() {
        fun problems(vararg cases: ManifestCase) = ManifestValidation.problems(VCaseManifest(project = "p", defaults = defaults, cases = cases.toList()))
        assertEquals(1, problems(case("ds.a.b").copy(capture = ManifestCapture())).size)
        assertEquals(1, problems(case("ds.a.b").copy(capture = ManifestCapture(maxWidth = 0))).size)
        assertEquals(1, problems(case("ds.a.b").copy(capture = ManifestCapture(maxHeight = -5))).size)
        assertEquals(1, problems(case("ds.a.b").copy(capture = ManifestCapture(settleFrames = -1))).size)
        assertEquals(1, problems(case("ds.a.b").copy(capture = ManifestCapture(frameIntervalMs = -1))).size)
        assertEquals(1, problems(case("screen.a.b").copy(kind = ManifestCase.KIND_SCREEN, capture = ManifestCapture(maxWidth = 1600))).size)
        assertTrue(problems(case("screen.a.b").copy(kind = ManifestCase.KIND_SCREEN, capture = ManifestCapture(settleFrames = 2))).isEmpty())
        val blank = ManifestValidation.problems(VCaseManifest(project = "p", defaults = DEFAULTS.copy(viewport = DEFAULTS.viewport + wide.copy(sizeClass = " ")), cases = emptyList()))
        assertEquals(1, blank.size, blank.toString())
    }

    @Test
    fun changedCaptureIsAManifestChange() {
        val a = VCaseManifest(project = "p", defaults = DEFAULTS, cases = listOf(case("ds.a.b")))
        val b = a.copy(cases = listOf(case("ds.a.b").copy(capture = ManifestCapture(settleFrames = 2))))
        assertEquals(mapOf("ds.a.b" to listOf("capture")), ManifestDiff.between(a, b).changed)
        assertEquals(listOf("defaults"), ManifestDiff.between(a, a.copy(defaults = defaults)).header)
    }
}
