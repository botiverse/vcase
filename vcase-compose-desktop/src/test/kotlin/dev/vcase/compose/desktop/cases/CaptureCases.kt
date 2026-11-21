package dev.vcase.compose.desktop.cases

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.vcase.VCase
import dev.vcase.VCaseSizeClass
import dev.vcase.compose.desktop.LocalVCaseVariant

// Cases for the per-case capture settings: a bound wider than the viewport, settle frames and
// the frame clock, and a viewport size class.

/** 30dp wide: more than the 20dp `tiny` viewport, inside its own 40dp bound. */
@VCase("test.bar.wide", theme = ["light"], maxWidth = 40)
@Composable
fun WideBar() {
    Box(Modifier.size(30.dp, 2.dp).background(Color.Red))
}

/** The same bar on the default bound: cut at the viewport width. */
@VCase("test.bar.bounded", theme = ["light"])
@Composable
fun BoundedBar() {
    Box(Modifier.size(30.dp, 2.dp).background(Color.Red))
}

/**
 * Two 2dp squares: the left one turns blue once two frames have reached the content, the right
 * one once a frame at 100 ms or later has.
 */
@Composable
private fun FrameProbe() {
    var frames by remember { mutableIntStateOf(0) }
    var lastNanos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos {
                frames++
                lastNanos = it
            }
        }
    }
    Row {
        Box(Modifier.size(2.dp).background(if (frames >= 2) Color.Blue else Color.Red))
        Box(Modifier.size(2.dp).background(if (lastNanos >= 100_000_000L) Color.Blue else Color.Red))
    }
}

@VCase("test.frames.first", theme = ["light"])
@Composable
fun FramesFirst() {
    FrameProbe()
}

@VCase("test.frames.still", theme = ["light"], settleFrames = 3, frameIntervalMs = 0)
@Composable
fun FramesStill() {
    FrameProbe()
}

@VCase("test.frames.clock", theme = ["light"], settleFrames = 3, frameIntervalMs = 100)
@Composable
fun FramesClock() {
    FrameProbe()
}

/** 6dp on a wide viewport, 3dp otherwise. */
@VCase("test.square.size-class", theme = ["light"], viewport = ["tiny", "tiny-wide"], pointer = ["fine"])
@Composable
fun SizeClassSquare() {
    val wide = LocalVCaseVariant.current?.axes?.viewport?.sizeClass == VCaseSizeClass.WIDE
    Box(Modifier.size(if (wide) 6.dp else 3.dp).background(Color.Blue))
}
