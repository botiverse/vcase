package dev.vcase.sample

import dev.vcase.compose.desktop.VCaseDesktopTest

/** The one test class: every @VCase in dev.vcase.sample, against goldens/desktop-skiko. */
class SampleVCases : VCaseDesktopTest() {
    override val host = SampleVCaseHost
    override val packages = listOf("dev.vcase.sample")
}
