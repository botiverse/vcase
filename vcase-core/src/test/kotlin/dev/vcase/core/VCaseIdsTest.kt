package dev.vcase.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VCaseIdsTest {
    @Test
    fun acceptsLowercaseDotSegments() {
        listOf("ds.button.variants", "screen.channel.members", "part.header", "ds.empty-state.default", "ds.h1.x2").forEach {
            assertTrue(VCaseIds.isValid(it), it)
        }
    }

    @Test
    fun rejectsOtherShapes() {
        listOf("", "button", "Ds.button", "ds.Button", "ds..button", ".ds.button", "ds.button.", "ds.1button", "ds.button_variants", "ds.button variants", "ds.button@dark")
            .forEach { assertFalse(VCaseIds.isValid(it), it) }
        assertEquals("id 'Ds.x' must be lowercase", VCaseIds.problem("Ds.x"))
        assertFailsWith<IllegalArgumentException> { VCaseIds.require("button") }
    }

    @Test
    fun parityReferences() {
        assertNull(VCaseIds.parityProblem("web:ui.button.variants"))
        assertNotNull(VCaseIds.parityProblem("ui.button.variants"))
        assertNotNull(VCaseIds.parityProblem("Web:ui.button.variants"))
        assertNotNull(VCaseIds.parityProblem("web:Button"))
    }
}
