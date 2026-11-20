package dev.vcase.core

import dev.vcase.VCaseAxis
import dev.vcase.VCaseAxisValues
import dev.vcase.VCasePointer
import dev.vcase.VCaseViewport

/**
 * One capture: a case at one point of its axis space. [fileStem] is `<id>@<name>`,
 * the capture is `<fileStem>.png` and its metadata `<fileStem>.json`.
 */
public data class VCaseVariant(
    val case: ManifestCase,
    val axes: VCaseAxisValues,
    /** Comma-joined axis values, e.g. `dark,phone` or `light,phone,fine`. */
    val name: String,
) {
    val id: String get() = case.id
    val fileStem: String get() = "${case.id}@$name"

    /** Axis name to value as written in capture metadata. */
    public fun axisMap(): Map<String, String> =
        linkedMapOf(
            VCaseAxis.THEME to axes.theme,
            VCaseAxis.VIEWPORT to axes.viewport.name,
            VCaseAxis.LOCALE to axes.locale,
            VCaseAxis.POINTER to axes.pointer.value,
            VCaseAxis.FONT_SCALE to AxisExpansion.formatScale(axes.fontScale),
        )
}

/**
 * Expands cases into variants: the cartesian product of the effective values of every axis
 * (case override, else project default), in [VCaseAxis.ORDER], values in declared order.
 *
 * File naming: theme and viewport always appear in the name; locale, pointer and font
 * scale appear only when the case overrides that axis or the project default lists more than
 * one value for it. So `ds.button.variants@dark,phone.png`, and with
 * `pointer = ["touch", "fine"]`, `ds.button.variants@dark,phone,fine.png`. Font scales are
 * written `fs1.3`.
 */
public object AxisExpansion {
    public val KNOWN_AXES: Set<String> = VCaseAxis.ORDER.toSet()

    public fun expand(manifest: VCaseManifest): List<VCaseVariant> = manifest.cases.flatMap { expand(it, manifest.defaults) }

    public fun expand(
        case: ManifestCase,
        defaults: ManifestDefaults,
    ): List<VCaseVariant> {
        val unknown = case.axes.keys - KNOWN_AXES
        require(unknown.isEmpty()) { "${case.id}: unknown axes $unknown (known: ${VCaseAxis.ORDER})" }

        val themes = case.axes[VCaseAxis.THEME] ?: defaults.theme
        val viewports =
            (case.axes[VCaseAxis.VIEWPORT] ?: defaults.viewport.map { it.name }).map { name ->
                val v = defaults.viewport(name) ?: throw IllegalArgumentException("${case.id}: unknown viewport '$name'")
                VCaseViewport(v.name, v.w, v.h, v.density)
            }
        val locales = case.axes[VCaseAxis.LOCALE] ?: defaults.locale
        val pointers =
            (case.axes[VCaseAxis.POINTER] ?: defaults.pointer).map {
                runCatching { VCasePointer.of(it) }.getOrElse { _ -> throw IllegalArgumentException("${case.id}: unknown pointer '$it'") }
            }
        val scales =
            case.axes[VCaseAxis.FONT_SCALE]?.map {
                it.toFloatOrNull() ?: throw IllegalArgumentException("${case.id}: font scale '$it' is not a number")
            } ?: defaults.fontScale

        for ((axis, values) in listOf("theme" to themes, "viewport" to viewports, "locale" to locales, "pointer" to pointers, "fontScale" to scales)) {
            require(values.isNotEmpty()) { "${case.id}: axis '$axis' has no values" }
        }

        val showLocale = VCaseAxis.LOCALE in case.axes || defaults.locale.size > 1
        val showPointer = VCaseAxis.POINTER in case.axes || defaults.pointer.size > 1
        val showScale = VCaseAxis.FONT_SCALE in case.axes || defaults.fontScale.size > 1

        val out = ArrayList<VCaseVariant>()
        for (theme in themes) for (viewport in viewports) for (locale in locales) for (pointer in pointers) for (scale in scales) {
            val parts = mutableListOf(theme, viewport.name)
            if (showLocale) parts += locale
            if (showPointer) parts += pointer.value
            if (showScale) parts += "fs" + formatScale(scale)
            out += VCaseVariant(case, VCaseAxisValues(theme, viewport, locale, pointer, scale), parts.joinToString(","))
        }
        val dup = out.groupBy { it.name }.filterValues { it.size > 1 }.keys
        require(dup.isEmpty()) { "${case.id}: duplicate axis values give the same capture name $dup" }
        return out
    }

    /** `1.0` -> `1`, `1.3` -> `1.3`. */
    public fun formatScale(scale: Float): String {
        val s = scale.toString()
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }
}
