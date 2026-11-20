package dev.vcase.core

import java.io.File

public enum class VCaseMode {
    /** Write goldens that are missing or differ; identical ones keep their bytes. */
    Record,

    /** Compare against goldens with zero tolerance; any difference or missing golden fails. */
    Verify,
    ;

    public companion object {
        public fun parse(value: String?): VCaseMode =
            when (value?.lowercase()) {
                null, "", "verify" -> Verify
                "record" -> Record
                else -> throw IllegalArgumentException("vcase mode '$value' must be record or verify")
            }
    }
}

/**
 * Goldens of one renderer: `<root>/<renderer>/<id>@<axes>.png`. Goldens are kept per
 * renderer (`desktop-skiko`, `android-robolectric`, ...) so switching a renderer re-records
 * only its own set.
 */
public class GoldenStore(
    public val root: File,
    public val renderer: String,
) {
    public val dir: File get() = File(root, renderer)

    public fun golden(stem: String): File = File(dir, "$stem.png")

    public fun stems(): Set<String> = dir.listFiles { f -> f.isFile && f.name.endsWith(".png") }?.map { it.name.removeSuffix(".png") }?.toSet() ?: emptySet()
}

public enum class VariantStatus(
    public val failing: Boolean,
) {
    Passed(false),
    Recorded(false),
    Unchanged(false),
    Skipped(false),
    Failed(true),
    MissingGolden(true),
    Error(true),
}

public data class VariantResult(
    val variant: VCaseVariant,
    val status: VariantStatus,
    val message: String = "",
    val actual: File? = null,
    val expected: File? = null,
    val diff: File? = null,
    val meta: CaptureMeta? = null,
)

public data class RunSummary(
    val mode: VCaseMode,
    val renderer: String,
    val results: List<VariantResult>,
    /** Goldens no variant produced (case removed or renamed); record mode has deleted them. */
    val orphanGoldens: List<String>,
    val reportFile: File?,
) {
    val failures: List<VariantResult> get() = results.filter { it.status.failing }
    val ok: Boolean get() = failures.isEmpty() && (mode == VCaseMode.Record || orphanGoldens.isEmpty())

    public fun describe(): String =
        buildString {
            val counts = results.groupingBy { it.status }.eachCount()
            append("vcase ${mode.name.lowercase()} [$renderer]: ${results.size} captures")
            VariantStatus.entries.forEach { s -> counts[s]?.let { append(", ${s.name.lowercase()} $it") } }
            if (orphanGoldens.isNotEmpty()) append(", orphan goldens ${orphanGoldens.size}")
            failures.forEach { append("\n  ${it.status}: ${it.variant.fileStem}: ${it.message}") }
            orphanGoldens.forEach {
                append(if (mode == VCaseMode.Verify) "\n  orphan golden: $it.png (re-record or delete)" else "\n  deleted orphan golden: $it.png")
            }
            reportFile?.let { append("\n  report: ${it.absolutePath}") }
        }
}

/**
 * Applies the record / verify rule to captures, renderer-agnostic: an adapter only renders.
 * Actual images, diffs and metadata go to `<outputDir>/<renderer>/`, the report to
 * `<outputDir>/report/index.html`.
 */
public class GoldenRun(
    public val goldens: GoldenStore,
    public val outputDir: File,
    public val mode: VCaseMode,
    public val tolerance: PixelTolerance = PixelTolerance.Exact,
) {
    private val captureDir = File(outputDir, goldens.renderer)
    private val results = mutableListOf<VariantResult>()

    public fun accept(
        variant: VCaseVariant,
        outcome: CaptureOutcome,
    ): VariantResult {
        val result =
            when (outcome) {
                is CaptureOutcome.Skipped -> VariantResult(variant, VariantStatus.Skipped, outcome.reason)
                is CaptureOutcome.Error -> VariantResult(variant, VariantStatus.Error, outcome.message)
                is CaptureOutcome.Image -> image(variant, outcome)
            }
        results += result
        return result
    }

    private fun image(
        variant: VCaseVariant,
        outcome: CaptureOutcome.Image,
    ): VariantResult {
        captureDir.mkdirs()
        val stem = variant.fileStem
        val actualFile = File(captureDir, "$stem.png").apply { writeBytes(outcome.png) }
        File(captureDir, "$stem.json").writeText(outcome.meta.toJson())
        val golden = goldens.golden(stem)
        val actualImage = ImageCompare.readPng(outcome.png)
        val cmp = if (golden.isFile) ImageCompare.compare(ImageCompare.readPng(golden), actualImage, tolerance) else null
        return when (mode) {
            VCaseMode.Record ->
                when {
                    cmp == null -> {
                        golden.parentFile.mkdirs()
                        golden.writeBytes(outcome.png)
                        VariantResult(variant, VariantStatus.Recorded, "new golden", actualFile, golden, meta = outcome.meta)
                    }
                    cmp.matches -> VariantResult(variant, VariantStatus.Unchanged, "", actualFile, golden, meta = outcome.meta)
                    else -> {
                        val diffFile = writeDiff(stem, cmp)
                        golden.writeBytes(outcome.png)
                        VariantResult(variant, VariantStatus.Recorded, "updated: ${cmp.describe()}", actualFile, golden, diffFile, outcome.meta)
                    }
                }
            VCaseMode.Verify ->
                when {
                    cmp == null -> VariantResult(variant, VariantStatus.MissingGolden, "no golden at ${golden.path}", actualFile, meta = outcome.meta)
                    cmp.matches -> VariantResult(variant, VariantStatus.Passed, "", actualFile, golden, meta = outcome.meta)
                    else -> {
                        val expectedCopy = File(captureDir, "$stem.expected.png").apply { writeBytes(golden.readBytes()) }
                        VariantResult(variant, VariantStatus.Failed, cmp.describe(), actualFile, expectedCopy, writeDiff(stem, cmp), outcome.meta)
                    }
                }
        }
    }

    private fun writeDiff(
        stem: String,
        cmp: CompareResult,
    ): File? {
        val img = cmp.diffImage ?: return null
        val f = File(captureDir, "$stem.diff.png")
        javax.imageio.ImageIO.write(img, "png", f)
        return f
    }

    /**
     * Ends the run: finds orphan goldens (only meaningful when every case ran, so pass
     * [complete] = false for a filtered run), deletes them in record mode, writes the report.
     */
    public fun finish(
        complete: Boolean,
        title: String = "vcase",
    ): RunSummary {
        val produced = results.map { it.variant.fileStem }.toSet()
        val orphans = if (complete) (goldens.stems() - produced).sorted() else emptyList()
        if (mode == VCaseMode.Record) orphans.forEach { goldens.golden(it).delete() }
        val summary = RunSummary(mode, goldens.renderer, results.toList(), orphans, null)
        val report = HtmlReport.write(File(outputDir, "report"), summary, title)
        return summary.copy(reportFile = report)
    }
}
