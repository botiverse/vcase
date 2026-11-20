package dev.vcase

/**
 * Names of the built-in axes, in the order they appear in capture file names
 * (`<id>@<theme>,<viewport>[,<locale>][,<pointer>][,<fontScale>].png`).
 */
public object VCaseAxis {
    public const val THEME: String = "theme"
    public const val VIEWPORT: String = "viewport"
    public const val LOCALE: String = "locale"
    public const val POINTER: String = "pointer"
    public const val FONT_SCALE: String = "fontScale"

    public val ORDER: List<String> = listOf(THEME, VIEWPORT, LOCALE, POINTER, FONT_SCALE)
}

/** Conventional theme axis values. Projects may use any other names. */
public object VCaseTheme {
    public const val LIGHT: String = "light"
    public const val DARK: String = "dark"
}

/** Pointer axis values: a finger (large targets, no hover) or a mouse / trackpad. */
public enum class VCasePointer(public val value: String) {
    Touch("touch"),
    Fine("fine"),
    ;

    public companion object {
        public fun of(value: String): VCasePointer =
            entries.firstOrNull { it.value == value } ?: throw IllegalArgumentException("unknown pointer '$value'")
    }
}

/**
 * A named viewport: logical size in dp ([width] x [height]) and pixel [density]
 * (1dp = density px). Declared once in the project defaults, referenced by [name].
 */
public data class VCaseViewport(
    val name: String,
    val width: Int,
    val height: Int,
    val density: Float,
)

/** One concrete point of the axis space a case is rendered at. */
public data class VCaseAxisValues(
    val theme: String,
    val viewport: VCaseViewport,
    val locale: String,
    val pointer: VCasePointer,
    val fontScale: Float = 1f,
)
