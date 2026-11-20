package dev.vcase.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vcase.VCasePointer
import dev.vcase.compose.desktop.VCaseHost
import dev.vcase.core.ManifestCase
import dev.vcase.core.ManifestDefaults
import dev.vcase.core.ManifestViewport
import dev.vcase.core.VCaseVariant
import java.security.MessageDigest
import java.util.Locale

/** How this project renders a case: its theme per axis values, on its canvas. */
object SampleVCaseHost : VCaseHost {
    override val project = "vcase-sample"

    override val defaults =
        ManifestDefaults(
            theme = listOf("light", "dark"),
            // `wide` is opt-in: only cases that list it (screens) are captured at it.
            viewport = listOf(ManifestViewport("phone", 360, 640, 2f), ManifestViewport("wide", 960, 600, 1f, optIn = true)),
            locale = listOf("en"),
            pointer = listOf("touch"),
        )

    override val fontsHash: String by lazy {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(SampleFonts.regular)
        md.update(SampleFonts.bold)
        md.digest().joinToString("") { "%02x".format(it) }.take(16)
    }

    private var savedLocale: Locale? = null

    override fun beforeCapture(variant: VCaseVariant) {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag(variant.axes.locale))
    }

    override fun afterCapture(variant: VCaseVariant) {
        savedLocale?.let(Locale::setDefault)
    }

    @Composable
    override fun Wrap(
        variant: VCaseVariant,
        content: @Composable () -> Unit,
    ) {
        val pointer = if (variant.axes.pointer == VCasePointer.Fine) SamplePointer.Fine else SamplePointer.Touch
        SampleTheme(dark = variant.axes.theme == "dark", pointer = pointer) {
            if (variant.case.kind == ManifestCase.KIND_SCREEN) {
                content()
            } else {
                // Components sit on the canvas with 16dp around them, like a gallery.
                Box(Modifier.background(SampleTheme.colors.canvas).padding(16.dp)) { content() }
            }
        }
    }
}
