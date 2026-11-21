package dev.vcase.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import dev.vcase.VCase

// Cases that change how they are captured, not only what they show.

/**
 * Wider than the 360dp phone viewport, which bounds a component by default: `maxWidth` lets
 * the capture grow to the content instead of cutting it at 360dp.
 */
@VCase("ds.toolbar.wide", maxWidth = 720)
@Composable
fun WideToolbarCase() {
    Row(Modifier.width(600.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf("Bold", "Italic", "Link", "Quote", "Code", "List").forEach { SampleButton(it, ButtonKind.Secondary) }
    }
}

/**
 * A tooltip open under its button. A popup lays itself out a frame after its anchor, so the
 * capture waits two settle frames; at time 0 each, so a fade-in would not have started
 * (`frameIntervalMs = 0`).
 */
@VCase("ds.tooltip.open", settleFrames = 2, frameIntervalMs = 0)
@Composable
fun TooltipOpenCase() {
    Box(Modifier.width(180.dp).height(100.dp)) {
        SampleButton("Save")
        Popup(offset = IntOffset(0, 112)) {
            val c = SampleTheme.colors
            Box(
                Modifier
                    .background(c.text, RoundedCornerShape(6.dp))
                    .border(1.dp, c.border, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                BasicText("Save changes (Ctrl+S)", style = SampleTheme.body.copy(color = c.canvas))
            }
        }
    }
}

/**
 * The buttons on a desktop-width window: the `desktop` viewport's size class is `wide`, so
 * the host pins the wide type scale; with a mouse (`fine`) the buttons are shorter than with
 * a finger.
 */
@VCase("ds.button.desktop", viewport = ["desktop"], pointer = ["fine", "touch"])
@Composable
fun DesktopButtonsCase() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SampleButton("Cancel", ButtonKind.Secondary)
        SampleButton("Save")
    }
}
