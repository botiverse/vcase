package dev.vcase.sample

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp

/** A tiny design system: two palettes, one font, a touch / fine pointer switch. */
@Immutable
data class SampleColors(
    val canvas: Color,
    val surface: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
)

val LightColors =
    SampleColors(
        canvas = Color(0xFFF6F6F4),
        surface = Color(0xFFFFFFFF),
        border = Color(0xFFDDDDD8),
        text = Color(0xFF1B1B19),
        textMuted = Color(0xFF6B6B66),
        accent = Color(0xFF2F5BEA),
        onAccent = Color(0xFFFFFFFF),
        danger = Color(0xFFD23C2A),
    )

val DarkColors =
    SampleColors(
        canvas = Color(0xFF151514),
        surface = Color(0xFF222220),
        border = Color(0xFF3A3A37),
        text = Color(0xFFF1F1EE),
        textMuted = Color(0xFFA3A39E),
        accent = Color(0xFF7C9BFF),
        onAccent = Color(0xFF0B1330),
        danger = Color(0xFFFF7A66),
    )

enum class SamplePointer { Touch, Fine }

val LocalSampleColors = staticCompositionLocalOf { LightColors }
val LocalSamplePointer = staticCompositionLocalOf { SamplePointer.Touch }

object SampleFonts {
    private fun bytes(name: String): ByteArray =
        SampleFonts::class.java.getResourceAsStream("/fonts/$name")?.use { it.readBytes() } ?: error("missing font $name")

    val regular: ByteArray by lazy { bytes("SpaceGrotesk-Regular.ttf") }
    val bold: ByteArray by lazy { bytes("SpaceGrotesk-Bold.ttf") }

    /** Bundled so captures never depend on the machine's fonts. */
    val family: FontFamily by lazy {
        FontFamily(
            Font("SpaceGrotesk-Regular", regular, FontWeight.Normal),
            Font("SpaceGrotesk-Bold", bold, FontWeight.Bold),
        )
    }
}

object SampleTheme {
    val colors: SampleColors @Composable get() = LocalSampleColors.current
    val pointer: SamplePointer @Composable get() = LocalSamplePointer.current

    val body: TextStyle @Composable get() = TextStyle(fontFamily = SampleFonts.family, fontSize = 14.sp, color = colors.text)
    val title: TextStyle @Composable get() = TextStyle(fontFamily = SampleFonts.family, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.text)
}

@Composable
fun SampleTheme(
    dark: Boolean,
    pointer: SamplePointer = SamplePointer.Touch,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSampleColors provides if (dark) DarkColors else LightColors,
        LocalSamplePointer provides pointer,
        content = content,
    )
}
