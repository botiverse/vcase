package dev.vcase.core

import dev.vcase.VCasePointer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AxisExpansionTest {
    private val twoViewports = DEFAULTS.copy(viewport = DEFAULTS.viewport + ManifestViewport("wide", 1280, 800, 2f))

    @Test
    fun defaultsOnlyExpandThemeAndNameThemeAndViewport() {
        val v = AxisExpansion.expand(case("ds.button.variants"), DEFAULTS)
        assertEquals(listOf("ds.button.variants@light,phone", "ds.button.variants@dark,phone"), v.map { it.fileStem })
        assertEquals(VCasePointer.Touch, v[0].axes.pointer)
        assertEquals(3f, v[0].axes.viewport.density)
    }

    @Test
    fun caseOverrideIsCartesianInAxisOrder() {
        val v =
            AxisExpansion.expand(
                case("screen.channel.members", mapOf("theme" to listOf("light", "dark"), "viewport" to listOf("phone", "wide"))),
                twoViewports,
            )
        assertEquals(
            listOf("light,phone", "light,wide", "dark,phone", "dark,wide"),
            v.map { it.name },
        )
        assertEquals("screen.channel.members@dark,wide", v.last().fileStem)
    }

    @Test
    fun overriddenNonCoreAxesAppearInName() {
        val v = AxisExpansion.expand(case("ds.button.variants", mapOf("pointer" to listOf("touch", "fine"))), DEFAULTS)
        assertEquals(listOf("light,phone,touch", "light,phone,fine", "dark,phone,touch", "dark,phone,fine"), v.map { it.name })
    }

    @Test
    fun fontScaleAndLocaleNaming() {
        val v = AxisExpansion.expand(case("ds.text.body", mapOf("theme" to listOf("light"), "locale" to listOf("en"), "fontScale" to listOf("1.0", "1.3"))), DEFAULTS)
        assertEquals(listOf("light,phone,en,fs1", "light,phone,en,fs1.3"), v.map { it.name })
        assertEquals(1.3f, v[1].axes.fontScale)
    }

    @Test
    fun multiValueDefaultsAppearInEveryName() {
        val v = AxisExpansion.expand(case("ds.a.b", mapOf("theme" to listOf("dark"))), DEFAULTS.copy(locale = listOf("zh-CN", "en")))
        assertEquals(listOf("dark,phone,zh-CN", "dark,phone,en"), v.map { it.name })
    }

    @Test
    fun axisMapListsEveryAxis() {
        val v = AxisExpansion.expand(case("ds.a.b"), DEFAULTS).first()
        assertEquals(mapOf("theme" to "light", "viewport" to "phone", "locale" to "zh-CN", "pointer" to "touch", "fontScale" to "1"), v.axisMap())
    }

    @Test
    fun rejectsUnknownAxisViewportPointerAndScale() {
        assertFailsWith<IllegalArgumentException> { AxisExpansion.expand(case("ds.a.b", mapOf("density" to listOf("2"))), DEFAULTS) }
        assertFailsWith<IllegalArgumentException> { AxisExpansion.expand(case("ds.a.b", mapOf("viewport" to listOf("tablet"))), DEFAULTS) }
        assertFailsWith<IllegalArgumentException> { AxisExpansion.expand(case("ds.a.b", mapOf("pointer" to listOf("pen"))), DEFAULTS) }
        assertFailsWith<IllegalArgumentException> { AxisExpansion.expand(case("ds.a.b", mapOf("fontScale" to listOf("big"))), DEFAULTS) }
        assertFailsWith<IllegalArgumentException> { AxisExpansion.expand(case("ds.a.b", mapOf("theme" to emptyList())), DEFAULTS) }
        assertFailsWith<IllegalArgumentException> { AxisExpansion.expand(case("ds.a.b", mapOf("theme" to listOf("dark", "dark"))), DEFAULTS) }
    }

    @Test
    fun expandsWholeManifest() {
        val m = VCaseManifest(project = "p", defaults = DEFAULTS, cases = listOf(case("ds.a.b"), case("ds.c.d", mapOf("theme" to listOf("dark")))))
        assertEquals(3, AxisExpansion.expand(m).size)
    }
}
