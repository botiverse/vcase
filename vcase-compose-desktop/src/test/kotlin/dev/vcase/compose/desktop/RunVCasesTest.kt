package dev.vcase.compose.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.vcase.core.ImageCompare
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
            viewport = listOf(ManifestViewport("tiny", 20, 12, 2f)),
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
        assertEquals(listOf("test.device.only", "test.screen.full", "test.square.plain", "test.square.pointer"), cases.map { it.entry.id })
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
