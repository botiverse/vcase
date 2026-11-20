package dev.vcase.core

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoldenRunTest {
    private val variants = AxisExpansion.expand(case("ds.a.b"), DEFAULTS)

    private fun png(color: Int): ByteArray {
        val img = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until 4) for (x in 0 until 4) img.setRGB(x, y, color)
        return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }

    private fun capture(
        v: VCaseVariant,
        color: Int,
    ) = CaptureOutcome.Image(png(color), CaptureMeta(v.id, v.axisMap(), "test", "test-renderer", durationMs = 1))

    private fun run(
        root: File,
        mode: VCaseMode,
        color: (VCaseVariant) -> Int,
    ): RunSummary {
        val run = GoldenRun(GoldenStore(File(root, "goldens"), "test-renderer"), File(root, "out"), mode)
        variants.forEach { run.accept(it, capture(it, color(it))) }
        return run.finish(complete = true)
    }

    @Test
    fun recordThenVerifyThenDetectChange() {
        val root = kotlin.io.path.createTempDirectory("vcase").toFile()
        val rec = run(root, VCaseMode.Record) { 0xFF112233.toInt() }
        assertEquals(listOf(VariantStatus.Recorded, VariantStatus.Recorded), rec.results.map { it.status })
        assertTrue(File(root, "goldens/test-renderer/ds.a.b@dark,phone.png").isFile)
        assertTrue(File(root, "out/test-renderer/ds.a.b@dark,phone.json").readText().contains("\"renderer\": \"test-renderer\""))

        val goldenBytes = File(root, "goldens/test-renderer/ds.a.b@light,phone.png").readBytes()
        val again = run(root, VCaseMode.Record) { 0xFF112233.toInt() }
        assertEquals(listOf(VariantStatus.Unchanged, VariantStatus.Unchanged), again.results.map { it.status })
        assertTrue(goldenBytes.contentEquals(File(root, "goldens/test-renderer/ds.a.b@light,phone.png").readBytes()))

        assertTrue(run(root, VCaseMode.Verify) { 0xFF112233.toInt() }.ok)

        val bad = run(root, VCaseMode.Verify) { if (it.axes.theme == "dark") 0xFF112234.toInt() else 0xFF112233.toInt() }
        assertFalse(bad.ok)
        val failed = bad.failures.single()
        assertEquals("ds.a.b@dark,phone", failed.variant.fileStem)
        assertTrue(File(root, "out/test-renderer/ds.a.b@dark,phone.diff.png").isFile)
        assertTrue(File(root, "out/report/index.html").readText().contains("ds.a.b@dark,phone"))
    }

    @Test
    fun missingGoldenAndOrphansFailVerify() {
        val root = kotlin.io.path.createTempDirectory("vcase").toFile()
        val missing = run(root, VCaseMode.Verify) { 0xFF000000.toInt() }
        assertEquals(setOf(VariantStatus.MissingGolden), missing.results.map { it.status }.toSet())

        run(root, VCaseMode.Record) { 0xFF000000.toInt() }
        val orphan = File(root, "goldens/test-renderer/ds.gone.case@light,phone.png").apply { writeBytes(png(0)) }
        val verify = run(root, VCaseMode.Verify) { 0xFF000000.toInt() }
        assertFalse(verify.ok)
        assertEquals(listOf("ds.gone.case@light,phone"), verify.orphanGoldens)
        run(root, VCaseMode.Record) { 0xFF000000.toInt() }
        assertFalse(orphan.exists())
    }

    @Test
    fun errorsFailAndSkipsDoNot() {
        val root = kotlin.io.path.createTempDirectory("vcase").toFile()
        val run = GoldenRun(GoldenStore(File(root, "g"), "r"), File(root, "o"), VCaseMode.Verify)
        run.accept(variants[0], CaptureOutcome.Error("boom"))
        run.accept(variants[1], CaptureOutcome.Skipped("needsDevice"))
        val s = run.finish(complete = false)
        assertEquals(listOf(VariantStatus.Error), s.failures.map { it.status })
        assertTrue("boom" in s.describe())
    }

    @Test
    fun modeParsing() {
        assertEquals(VCaseMode.Verify, VCaseMode.parse(null))
        assertEquals(VCaseMode.Record, VCaseMode.parse("RECORD"))
    }
}
