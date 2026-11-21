package dev.vcase.sample

import dev.vcase.compose.desktop.VCaseDesktopConfig
import dev.vcase.compose.desktop.runVCases
import dev.vcase.core.VCaseMode
import dev.vcase.core.VariantStatus
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Record into a scratch directory, verify (zero diff), then one changed pixel must fail. */
class RecordVerifyTest {
    @Test
    fun recordThenVerifyThenOnePixelFails() {
        val root = kotlin.io.path.createTempDirectory("vcase-sample").toFile()
        fun config(mode: VCaseMode) =
            VCaseDesktopConfig(
                packages = listOf("dev.vcase.sample"),
                mode = mode,
                goldensDir = File(root, "goldens"),
                outputDir = File(root, "out"),
                manifestFile = File(root, "vcase.manifest.json"),
            )

        val recorded = runVCases(SampleVCaseHost, config(VCaseMode.Record))
        assertTrue(recorded.ok, recorded.describe())
        val stems = recorded.summary!!.results.map { it.variant.fileStem }.toSet()
        // 4 button (2 themes x 2 pointers) + 2 badge + 4 card (2 themes x 2 font scales) + 4 screen (2 x 2 viewports)
        // + 2 wide toolbar + 2 tooltip + 4 desktop button (2 themes x 2 pointers)
        assertEquals(22, stems.size, stems.toString())
        assertTrue("ds.button.desktop@dark,desktop,fine" in stems)
        assertTrue("ds.button.variants@dark,phone,fine" in stems)
        assertTrue("ds.card.default@light,phone,fs1.3" in stems)
        assertTrue("screen.inbox.empty@dark,wide" in stems)

        val verified = runVCases(SampleVCaseHost, config(VCaseMode.Verify))
        assertTrue(verified.ok, verified.describe())
        assertEquals(setOf(VariantStatus.Passed), verified.summary!!.results.map { it.status }.toSet())

        val golden = File(root, "goldens/desktop-skiko/ds.badge.counts@light,phone.png")
        val img = ImageIO.read(golden)
        img.setRGB(img.width / 2, img.height / 2, img.getRGB(img.width / 2, img.height / 2) xor 0x00010000)
        ImageIO.write(img, "png", golden)
        val broken = runVCases(SampleVCaseHost, config(VCaseMode.Verify))
        assertFalse(broken.ok)
        val failure = broken.summary!!.failures.single()
        assertEquals("ds.badge.counts@light,phone", failure.variant.fileStem)
        assertTrue(failure.message.startsWith("1 of "), failure.message)
    }

    @Test
    fun screenIsViewportSizedAndComponentIsContentSized() {
        val root = kotlin.io.path.createTempDirectory("vcase-sample").toFile()
        val r =
            runVCases(
                SampleVCaseHost,
                VCaseDesktopConfig(
                    packages = listOf("dev.vcase.sample"),
                    mode = VCaseMode.Record,
                    goldensDir = File(root, "g"),
                    outputDir = File(root, "o"),
                    manifestFile = null,
                    filter = listOf("screen.*", "ds.badge.*"),
                ),
            )
        assertTrue(r.ok, r.describe())
        val dir = File(root, "g/desktop-skiko")
        val wide = ImageIO.read(File(dir, "screen.inbox.empty@light,wide.png"))
        assertEquals(960 to 600, wide.width to wide.height)
        val phone = ImageIO.read(File(dir, "screen.inbox.empty@light,phone.png"))
        assertEquals(720 to 1280, phone.width to phone.height)
        val badge = ImageIO.read(File(dir, "ds.badge.counts@light,phone.png"))
        assertTrue(badge.width < 400 && badge.height < 200, "${badge.width}x${badge.height}")
    }

    @Test
    fun captureSettings() {
        val root = kotlin.io.path.createTempDirectory("vcase-sample").toFile()
        val r =
            runVCases(
                SampleVCaseHost,
                VCaseDesktopConfig(
                    packages = listOf("dev.vcase.sample"),
                    mode = VCaseMode.Record,
                    goldensDir = File(root, "g"),
                    outputDir = File(root, "o"),
                    manifestFile = null,
                    filter = listOf("ds.toolbar.wide", "ds.button.desktop"),
                ),
            )
        assertTrue(r.ok, r.describe())
        val dir = File(root, "g/desktop-skiko")
        // maxWidth = 720: 600dp of toolbar + 2 x 16dp of canvas, past the 360dp phone bound.
        val toolbar = ImageIO.read(File(dir, "ds.toolbar.wide@light,phone.png"))
        assertEquals(1264, toolbar.width)
        assertTrue(r.summary!!.results.all { it.meta!!.warnings.isEmpty() }, r.summary!!.results.map { it.meta!!.warnings }.toString())
        // With a mouse the controls are shorter than with a finger.
        val desktop = ImageIO.read(File(dir, "ds.button.desktop@light,desktop,fine.png"))
        val desktopTouch = ImageIO.read(File(dir, "ds.button.desktop@light,desktop,touch.png"))
        assertTrue(desktopTouch.height > desktop.height, "touch ${desktopTouch.height} vs fine ${desktop.height}")
    }
}
