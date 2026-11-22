package dev.vcase.compose.desktop

import dev.vcase.core.VCaseMode
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.Description
import org.junit.runner.JUnitCore
import org.junit.runner.Request
import org.junit.runner.Result
import org.junit.runner.manipulation.Filter
import org.junit.runner.notification.RunListener

/** The class under test; run on its own by Gradle it records into a temporary directory. */
class RunnerVCases : VCaseDesktopTest() {
    override val host = TestHost
    override val packages = listOf("dev.vcase.compose.desktop.cases")

    override fun config() =
        VCaseDesktopConfig(
            packages = packages,
            mode = mode,
            goldensDir = File(root, "goldens"),
            outputDir = File(root, "out"),
            manifestFile = File(root, "vcase.manifest.json"),
        )

    companion object {
        var root: File = kotlin.io.path.createTempDirectory("vcase-runner").toFile()
        var mode = VCaseMode.Record
    }
}

class VCaseRunnerTest {
    private val started = mutableListOf<String>()

    private fun run(
        mode: VCaseMode,
        only: String? = null,
    ): Result {
        started.clear()
        RunnerVCases.mode = mode
        val core = JUnitCore()
        core.addListener(
            object : RunListener() {
                override fun testStarted(description: Description) {
                    started += description.methodName
                }
            },
        )
        val request = Request.aClass(RunnerVCases::class.java)
        val filter =
            object : Filter() {
                override fun shouldRun(description: Description) = description.isSuite || description.methodName == only

                override fun describe() = only.orEmpty()
            }
        return core.run(if (only == null) request else request.filterWith(filter)).also { RunnerVCases.mode = VCaseMode.Record }
    }

    @Test
    fun oneTestPerCaseThenTheSetCheck() {
        RunnerVCases.root = kotlin.io.path.createTempDirectory("vcase-runner").toFile()
        val dir = File(RunnerVCases.root, "goldens/desktop-skiko")

        val record = run(VCaseMode.Record)
        assertTrue(record.wasSuccessful(), record.failures.toString())
        val ids = VCaseScanner.scan(listOf("dev.vcase.compose.desktop.cases")).map { it.entry.id }
        assertEquals(ids + "manifestAndGoldens", started)
        assertTrue(File(dir, "test.square.plain@light,tiny.png").isFile)
        assertTrue(run(VCaseMode.Verify).wasSuccessful())

        // A changed golden fails its own case only.
        val golden = File(dir, "test.square.plain@dark,tiny.png")
        val image = ImageIO.read(golden).also { it.setRGB(0, 0, it.getRGB(0, 0) xor 1) }
        ImageIO.write(image, "png", golden)
        val changed = run(VCaseMode.Verify).failures.single()
        assertEquals("test.square.plain", changed.description.methodName)
        assertTrue("test.square.plain@dark,tiny" in changed.message, changed.message)

        // One case: the others' goldens are not orphans, in record mode they stay.
        assertTrue(run(VCaseMode.Record, only = "test.square.plain").wasSuccessful())
        assertEquals(listOf("test.square.plain"), started)
        assertTrue(run(VCaseMode.Verify).wasSuccessful())

        // An orphan golden and a drifted manifest fail the set check only.
        golden.copyTo(File(dir, "test.gone.case@light,tiny.png"))
        assertEquals("manifestAndGoldens", run(VCaseMode.Verify).failures.single().description.methodName)
        File(dir, "test.gone.case@light,tiny.png").delete()
        val manifest = File(RunnerVCases.root, "vcase.manifest.json")
        manifest.writeText(manifest.readText().replace("test.square.plain", "test.square.renamed"))
        assertEquals("manifestAndGoldens", run(VCaseMode.Verify).failures.single().description.methodName)
    }
}
