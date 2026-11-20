package dev.vcase

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VCasePointerTest {
    @Test
    fun parsesPointerNames() {
        assertEquals(VCasePointer.Touch, VCasePointer.of("touch"))
        assertEquals(VCasePointer.Fine, VCasePointer.of("fine"))
        assertFailsWith<IllegalArgumentException> { VCasePointer.of("mouse") }
    }

    @Test
    fun axisOrderStartsWithThemeAndViewport() {
        assertEquals(listOf("theme", "viewport"), VCaseAxis.ORDER.take(2))
    }
}
