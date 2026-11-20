package dev.vcase.compose.desktop.cases

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.vcase.VCase
import dev.vcase.VCaseDivergence
import dev.vcase.VCaseKind
import dev.vcase.compose.desktop.LocalVCaseVariant

@VCase("test.square.plain", parity = ["web:test.square.plain"], divergences = [VCaseDivergence(with = "web", region = "edge", why = "test")])
@Composable
fun PlainSquare() {
    Box(Modifier.size(10.dp, 6.dp).background(Color.Red))
}

@VCase("test.square.pointer", theme = ["dark"], pointer = ["touch", "fine"])
@Composable
private fun PointerSquare() {
    val fine = LocalVCaseVariant.current?.axes?.pointer?.value == "fine"
    Box(Modifier.size(if (fine) 4.dp else 8.dp).background(Color.Blue))
}

@VCase("test.screen.full", kind = VCaseKind.Screen, theme = ["light"])
@Composable
fun FullScreen() {
    Box(Modifier.fillMaxSize().background(Color.Green))
}

@VCase("test.device.only", needsDevice = true)
@Composable
fun DeviceOnly() {
    Box(Modifier.size(1.dp))
}
