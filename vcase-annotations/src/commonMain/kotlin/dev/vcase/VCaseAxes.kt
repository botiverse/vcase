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

/**
 * Conventional viewport size classes ([VCaseViewport.sizeClass]): the layout mode an app picks
 * from its window width (phone vs desktop type scale, control sizes). Projects may use any
 * other names (e.g. Material's `compact` / `medium` / `expanded`).
 */
public object VCaseSizeClass {
    public const val COMPACT: String = "compact"
    public const val WIDE: String = "wide"
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
 *
 * [sizeClass] is the layout mode the host should pin for this viewport (e.g.
 * [VCaseSizeClass.WIDE]), or null when the project does not declare one. It lets a host
 * render the wide layout without deriving it from the capture size: a component is captured
 * at its content size, which says nothing about the window it is designed for.
 */
public data class VCaseViewport(
    val name: String,
    val width: Int,
    val height: Int,
    val density: Float,
    val sizeClass: String? = null,
)

/** One concrete point of the axis space a case is rendered at. */
public data class VCaseAxisValues(
    val theme: String,
    val viewport: VCaseViewport,
    val locale: String,
    val pointer: VCasePointer,
    val fontScale: Float = 1f,
)
