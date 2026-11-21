package dev.vcase.compose.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.vcase.core.AxisExpansion
import dev.vcase.core.ImageCompare
import dev.vcase.core.ManifestCapture
import dev.vcase.core.ManifestCase
import dev.vcase.core.ManifestDefaults
import dev.vcase.core.ManifestViewport
import dev.vcase.core.VCaseJson
import dev.vcase.core.VCaseMode
import dev.vcase.core.VCaseVariant
import dev.vcase.core.VariantStatus
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

object TestHost : VCaseHost {
    override val project = "vcase-test"
    override val defaults =
        ManifestDefaults(
            theme = listOf("light", "dark"),
            viewport = listOf(ManifestViewport("tiny", 20, 12, 2f), ManifestViewport("tiny-wide", 20, 12, 2f, optIn = true, sizeClass = "wide")),
            locale = listOf("en"),
            pointer = listOf("touch"),
        )

    @Composable
    override fun Wrap(
        variant: VCaseVariant,
        content: @Composable () -> Unit,
    ) {
        Box(Modifier.background(if (variant.axes.theme == "dark") Color.Black else Color.White)) { content() }
    }
}

class RunVCasesTest {
    private val pkg = listOf("dev.vcase.compose.desktop.cases")

    @Test
    fun scanFindsCustomAnnotationIncludingPrivateFunctions() {
        val cases = VCaseScanner.scan(pkg, "src/test/kotlin")
        assertEquals(
            listOf(
                "test.bar.bounded",
                "test.bar.wide",
                "test.device.only",
                "test.frames.clock",
                "test.frames.first",
                "test.frames.still",
                "test.screen.full",
                "test.square.plain",
                "test.square.pointer",
                "test.square.size-class",
            ),
            cases.map { it.entry.id },
        )
        val plain = cases.first { it.entry.id == "test.square.plain" }.entry
        assertTrue(plain.source.startsWith("src/test/kotlin/dev/vcase/compose/desktop/cases/TestCases.kt:"), plain.source)
        assertEquals(listOf("web:test.square.plain"), plain.parity)
        assertEquals("edge", plain.divergences.single().region)
        assertEquals(mapOf("theme" to listOf("dark"), "pointer" to listOf("touch", "fine")), cases.first { it.entry.id == "test.square.pointer" }.entry.axes)
        assertEquals("screen", cases.first { it.entry.id == "test.screen.full" }.entry.kind)
    }

    @Test
    fun recordVerifyAndCatchOnePixel() {
        val root = kotlin.io.path.createTempDirectory("vcase-desktop").toFile()
        fun cfg(mode: VCaseMode) =
            VCaseDesktopConfig(
                packages = pkg,
                mode = mode,
                goldensDir = File(root, "goldens"),
                outputDir = File(root, "out"),
                manifestFile = File(root, "vcase.manifest.json"),
            )

        val rec = runVCases(TestHost, cfg(VCaseMode.Record))
        assertTrue(rec.ok, rec.describe())
        val statuses = rec.summary!!.results.associate { it.variant.fileStem to it.status }
        assertEquals(VariantStatus.Skipped, statuses["test.device.only@light,tiny"])
        assertEquals(VariantStatus.Recorded, statuses["test.square.pointer@dark,tiny,fine"])

        val dir = File(root, "goldens/desktop-skiko")
        // Component: content size (10x6 dp at density 2); screen: the viewport (20x12 dp).
        val plain = ImageIO.read(File(dir, "test.square.plain@light,tiny.png"))
        assertEquals(20 to 12, plain.width to plain.height)
        assertEquals(0xFFFF0000.toInt(), plain.getRGB(5, 5))
        val screen = ImageIO.read(File(dir, "test.screen.full@light,tiny.png"))
        assertEquals(40 to 24, screen.width to screen.height)
        assertEquals(16, ImageIO.read(File(dir, "test.square.pointer@dark,tiny,touch.png")).width)
        assertEquals(8, ImageIO.read(File(dir, "test.square.pointer@dark,tiny,fine.png")).width)
        val meta = VCaseJson.json.parseToJsonElement(File(root, "out/desktop-skiko/test.square.plain@light,tiny.json").readText()).toString()
        assertTrue("\"renderer\":\"desktop-skiko\"" in meta && "\"platform\":\"desktop\"" in meta, meta)
        assertTrue(File(root, "vcase.manifest.json").isFile)

        val ok = runVCases(TestHost, cfg(VCaseMode.Verify))
        assertTrue(ok.ok, ok.describe())
        assertTrue(ok.manifestDiff!!.isEmpty)

        // One changed pixel in one golden fails verify, with a diff image.
        val f = File(dir, "test.square.plain@dark,tiny.png")
        val img = ImageIO.read(f).also { it.setRGB(0, 0, it.getRGB(0, 0) xor 0x00000001) }
        ImageIO.write(img, "png", f)
        val bad = runVCases(TestHost, cfg(VCaseMode.Verify))
        assertFalse(bad.ok)
        val failure = bad.summary!!.failures.single()
        assertEquals("test.square.plain@dark,tiny", failure.variant.fileStem)
        assertEquals(1, ImageCompare.compare(ImageIO.read(failure.expected!!), ImageIO.read(failure.actual!!)).mismatchedPixels)
        assertNotNull(failure.diff)
        assertTrue(File(root, "out/report/index.html").readText().contains("test.square.plain@dark,tiny"))
    }

    @Test
    fun scanReadsCaptureSettings() {
        val cases = VCaseScanner.scan(pkg).associate { it.entry.id to it.entry }
        assertEquals(null, cases.getValue("test.square.plain").capture)
        assertEquals(null, cases.getValue("test.frames.first").capture)
        assertEquals(ManifestCapture(maxWidth = 40), cases.getValue("test.bar.wide").capture)
        assertEquals(ManifestCapture(settleFrames = 3, frameIntervalMs = 0), cases.getValue("test.frames.still").capture)
        assertEquals(ManifestCapture(settleFrames = 3, frameIntervalMs = 100), cases.getValue("test.frames.clock").capture)
    }

    @Test
    fun framePlan() {
        val defaults = TestHost.defaults
        fun variant(capture: ManifestCapture?) = AxisExpansion.expand(ManifestCase(id = "ds.a.b", source = "x", capture = capture), defaults).first()
        // Nothing set: the host's frames, as before.
        val plain = DesktopRenderer.Frames.of(TestHost, variant(null))
        assertEquals(listOf(0L), plain.times(plain.captureSettle))
        val still = DesktopRenderer.Frames.of(TestHost, variant(ManifestCapture(settleFrames = 2, frameIntervalMs = 0)))
        assertEquals(0, still.measureSettle)
        assertEquals(listOf(0L, 0L, 0L), still.times(still.captureSettle))
        val stepped = DesktopRenderer.Frames.of(TestHost, variant(ManifestCapture(settleFrames = 2)))
        assertEquals(listOf(0L, 16_000_000L, 32_000_000L), stepped.times(stepped.captureSettle))
        // The case's bounds replace the host's; unset ones keep it.
        assertEquals(CaptureSize.WrapContent(1600, null), DesktopRenderer.Frames.captureSize(TestHost, variant(ManifestCapture(maxWidth = 1600))))
        assertEquals(CaptureSize.WrapContent(), DesktopRenderer.Frames.captureSize(TestHost, variant(null)))
    }

    @Test
    fun captureSettingsRender() {
        val root = kotlin.io.path.createTempDirectory("vcase-desktop").toFile()
        val r =
            runVCases(
                TestHost,
                VCaseDesktopConfig(
                    packages = pkg,
                    mode = VCaseMode.Record,
                    goldensDir = File(root, "g"),
                    outputDir = File(root, "o"),
                    manifestFile = null,
                    filter = listOf("test.bar.*", "test.frames.*", "test.square.size-class"),
                ),
            )
        assertTrue(r.ok, r.describe())
        val dir = File(root, "g/desktop-skiko")
        fun png(stem: String) = ImageIO.read(File(dir, "$stem.png"))

        // maxWidth: the whole 30dp bar; without it the capture stops at the 20dp viewport.
        assertEquals(60 to 4, png("test.bar.wide@light,tiny").let { it.width to it.height })
        assertEquals(40, png("test.bar.bounded@light,tiny").width)
        val warned = r.summary!!.results.first { it.variant.fileStem == "test.bar.bounded@light,tiny" }.meta!!.warnings
        assertTrue(warned.any { "max width" in it }, warned.toString())

        val red = 0xFFFF0000.toInt()
        val blue = 0xFF0000FF.toInt()
        fun probe(stem: String) = png(stem).let { it.getRGB(1, 1) to it.getRGB(5, 1) }
        // One frame at time 0: neither square has seen frames.
        assertEquals(red to red, probe("test.frames.first@light,tiny"))
        // Three settle frames at time 0: frames arrived, the clock stayed at 0.
        assertEquals(blue to red, probe("test.frames.still@light,tiny"))
        // Three settle frames 100 ms apart: the clock moved.
        assertEquals(blue to blue, probe("test.frames.clock@light,tiny"))

        // The size class reaches the case; the pointer axis names the file.
        assertEquals(6, png("test.square.size-class@light,tiny,fine").width)
        assertEquals(12, png("test.square.size-class@light,tiny-wide,fine").width)
    }

    @Test
    fun manifestDriftFailsVerify() {
        val root = kotlin.io.path.createTempDirectory("vcase-desktop").toFile()
        val manifest = File(root, "vcase.manifest.json")
        val scanOnly = VCaseDesktopConfig(pkg, VCaseMode.Record, File(root, "g"), File(root, "o"), manifest, scanOnly = true)
        val written = runVCases(TestHost, scanOnly)
        assertTrue(written.summary == null && manifest.isFile)
        manifest.writeText(manifest.readText().replace("test.square.plain", "test.square.renamed"))
        val check = runVCases(TestHost, scanOnly.copy(mode = VCaseMode.Verify))
        assertFalse(check.ok)
        assertEquals(listOf("test.square.plain"), check.manifestDiff!!.added)
        assertEquals(listOf("test.square.renamed"), check.manifestDiff!!.removed)
    }

    @Test
    fun filterGlobs() {
        val c = VCaseDesktopConfig(packages = pkg, filter = listOf("test.square.*"))
        assertTrue(c.matches("test.square.plain"))
        assertFalse(c.matches("test.screen.full"))
    }
}
