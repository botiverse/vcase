package dev.vcase.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import dev.vcase.VCase
import dev.vcase.VCaseDivergence
import dev.vcase.VCaseKind

// Visual cases: plain composable functions, one per UI state, each with a global id.
// Axes not listed (theme, viewport, locale, pointer) come from the project defaults.

@VCase("ds.button.variants", pointer = ["touch", "fine"], parity = ["web:ui.button.variants"])
@Composable
fun ButtonVariantsCase() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SampleButton("Primary")
            SampleButton("Secondary", ButtonKind.Secondary)
            SampleButton("Delete", ButtonKind.Danger)
        }
        SampleButton("Disabled", enabled = false)
    }
}

@VCase("ds.badge.counts")
@Composable
fun BadgeCountsCase() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SampleBadge(1)
        SampleBadge(42)
        SampleBadge(1000)
    }
}

@VCase("ds.card.default", fontScale = [1.0f, 1.3f])
@Composable
fun CardCase() {
    SampleCard("Weekly sync", "Notes from Monday are ready to review.")
}

@VCase(
    "screen.inbox.empty",
    kind = VCaseKind.Screen,
    viewport = ["phone", "wide"],
    parity = ["web:screens.inbox.empty"],
    divergences = [
        VCaseDivergence(
            with = "web",
            region = "action",
            why = "The web page puts the action in its top bar, not under the hint",
            decidedBy = "sample",
            ref = "README#divergences",
        ),
    ],
)
@Composable
fun InboxEmptyCase() {
    SampleEmptyState("Nothing here yet", "New messages show up in this list.", "Compose")
}
