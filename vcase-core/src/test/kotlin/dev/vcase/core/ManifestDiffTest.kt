package dev.vcase.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManifestDiffTest {
    private val base = VCaseManifest(project = "p", defaults = DEFAULTS, cases = listOf(case("ds.a.b"), case("ds.c.d"), case("ds.e.f")))

    @Test
    fun identicalIsEmpty() {
        val d = ManifestDiff.between(base, base.copy())
        assertTrue(d.isEmpty)
        assertEquals("manifest matches the scan", d.describe())
    }

    @Test
    fun reportsAddedRemovedChangedAndHeader() {
        val scanned =
            base.copy(
                project = "q",
                cases =
                    listOf(
                        case("ds.a.b").copy(source = "Cases.kt:9"),
                        case("ds.c.d", mapOf("theme" to listOf("dark")), parity = listOf("web:ui.c.d")),
                        case("ds.x.y"),
                    ),
            )
        val d = ManifestDiff.between(base, scanned)
        assertEquals(listOf("ds.x.y"), d.added)
        assertEquals(listOf("ds.e.f"), d.removed)
        assertEquals(mapOf("ds.a.b" to listOf("source"), "ds.c.d" to listOf("axes", "parity")), d.changed)
        assertEquals(listOf("project"), d.header)
        val text = d.describe()
        assertTrue("+ ds.x.y" in text && "- ds.e.f" in text && "~ ds.c.d (axes, parity)" in text, text)
    }

    @Test
    fun caseOrderDoesNotMatter() {
        assertTrue(ManifestDiff.between(base, base.copy(cases = base.cases.reversed())).isEmpty)
    }
}
