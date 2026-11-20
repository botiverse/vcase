package dev.vcase.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

enum class ButtonKind { Primary, Secondary, Danger }

@Composable
fun SampleButton(
    label: String,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
) {
    val c = SampleTheme.colors
    // Touch targets are taller than mouse ones.
    val height = if (SampleTheme.pointer == SamplePointer.Touch) 44.dp else 32.dp
    val (bg, fg) =
        when (kind) {
            ButtonKind.Primary -> c.accent to c.onAccent
            ButtonKind.Secondary -> c.surface to c.text
            ButtonKind.Danger -> c.danger to c.onAccent
        }
    Box(
        Modifier
            .sizeIn(minHeight = height, minWidth = 64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) bg else c.border)
            .border(1.dp, if (kind == ButtonKind.Secondary || !enabled) c.border else bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = SampleTheme.body.copy(color = if (enabled) fg else c.textMuted))
    }
}

@Composable
fun SampleBadge(count: Int) {
    val c = SampleTheme.colors
    Box(
        Modifier.sizeIn(minWidth = 20.dp, minHeight = 20.dp).clip(CircleShape).background(c.danger).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(if (count > 99) "99+" else "$count", style = SampleTheme.body.copy(color = c.onAccent))
    }
}

@Composable
fun SampleCard(
    title: String,
    body: String,
) {
    val c = SampleTheme.colors
    Column(
        Modifier
            .width(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicText(title, style = SampleTheme.title)
        BasicText(body, style = SampleTheme.body.copy(color = c.textMuted))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SampleButton("Open")
            SampleButton("Later", ButtonKind.Secondary)
        }
    }
}

@Composable
fun SampleEmptyState(
    title: String,
    hint: String,
    action: String,
) {
    val c = SampleTheme.colors
    Column(
        Modifier.fillMaxSize().background(c.canvas).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(c.border))
        BasicText(title, style = SampleTheme.title)
        BasicText(hint, style = SampleTheme.body.copy(color = c.textMuted))
        SampleButton(action)
    }
}
