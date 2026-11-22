package dev.vcase.compose.desktop

import dev.vcase.core.AxisExpansion
import dev.vcase.core.CaptureOutcome
import dev.vcase.core.GoldenRun
import dev.vcase.core.GoldenStore
import dev.vcase.core.ManifestDiff
import dev.vcase.core.ManifestValidation
import dev.vcase.core.PixelTolerance
import dev.vcase.core.RunSummary
import dev.vcase.core.VCaseJson
import dev.vcase.core.VCaseManifest
import dev.vcase.core.VCaseMode
import dev.vcase.core.VariantResult
import java.io.File

/**
 * Where and how [runVCases] runs. [fromSystemProperties] reads the `vcase.*` properties a
 * Gradle test task sets:
 *
 * | property | default | meaning |
 * |---|---|---|
 * | `vcase.mode` | `verify` | `record`, `verify`, or `scan` (manifest only, no rendering) |
 * | `vcase.goldens` | `goldens` | golden root; images live in `<root>/<renderer>/` |
 * | `vcase.out` | `build/vcase` | actual images, diffs, metadata and `report/index.html` |
 * | `vcase.manifest` | `vcase.manifest.json` | committed manifest; empty string = none |
 * | `vcase.filter` | (all) | comma-separated id globs, e.g. `ds.button.*,screen.home` |
 * | `vcase.sourceRoot` | `""` | prefix for `source` paths in the manifest, e.g. `src/desktopTest/kotlin` |
 */
public data class VCaseDesktopConfig(
    val packages: List<String>,
    val mode: VCaseMode = VCaseMode.Verify,
    val goldensDir: File = File("goldens"),
    val outputDir: File = File("build/vcase"),
    val manifestFile: File? = File(VCaseManifest.FILE_NAME),
    val filter: List<String> = emptyList(),
    val sourceRoot: String = "",
    /** Only scan and write / check the manifest. */
    val scanOnly: Boolean = false,
    val tolerance: PixelTolerance = PixelTolerance.Exact,
) {
    public fun matches(id: String): Boolean = filter.isEmpty() || filter.any { glob -> globRegex(glob).matches(id) }

    public companion object {
        public fun fromSystemProperties(
            packages: List<String>,
            sourceRoot: String = "",
        ): VCaseDesktopConfig {
            val mode = System.getProperty("vcase.mode")?.trim()
            return VCaseDesktopConfig(
                packages = packages,
                mode = if (mode == "scan") VCaseMode.Record else VCaseMode.parse(mode),
                scanOnly = mode == "scan",
                goldensDir = File(System.getProperty("vcase.goldens") ?: "goldens"),
                outputDir = File(System.getProperty("vcase.out") ?: "build/vcase"),
                manifestFile = (System.getProperty("vcase.manifest") ?: VCaseManifest.FILE_NAME).takeIf { it.isNotBlank() }?.let(::File),
                filter = System.getProperty("vcase.filter").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
                sourceRoot = System.getProperty("vcase.sourceRoot") ?: sourceRoot,
            )
        }

        private fun globRegex(glob: String): Regex = Regex(glob.split('*').joinToString(".*") { Regex.escape(it) })
    }
}

/** Outcome of [runVCases]: the manifest check and the capture run. */
public class VCaseRunResult(
    public val manifest: VCaseManifest,
    /** Committed manifest vs scan; null when there is no committed manifest or in record mode. */
    public val manifestDiff: ManifestDiff?,
    /** Null for a scan-only run. */
    public val summary: RunSummary?,
) {
    public val ok: Boolean get() = (manifestDiff?.isEmpty ?: true) && (summary?.ok ?: true)

    public fun describe(): String =
        listOfNotNull(
            manifestDiff?.takeUnless { it.isEmpty }?.let { it.describe() + "\n  (run with vcase.mode=record or scan to rewrite the manifest)" },
            manifestDiff?.movedNote(),
            summary?.describe(),
        ).joinToString("\n").ifEmpty { "vcase: manifest written (${manifest.cases.size} cases)" }

    /** Throws [AssertionError] with [describe] unless [ok]. */
    public fun assertOk() {
        if (!ok) throw AssertionError(describe())
    }
}

/**
 * Scans [VCaseDesktopConfig.packages] for `@VCase` functions, checks or writes the manifest,
 * renders every variant with [host] and records or verifies the goldens.
 *
 * - verify: the committed manifest must equal the scan; every capture must equal its golden
 *   pixel for pixel; a missing golden, an error, or an orphan golden fails.
 * - record: rewrites the manifest, writes new and changed goldens, deletes orphan goldens.
 */
public fun runVCases(
    host: VCaseHost,
    config: VCaseDesktopConfig,
): VCaseRunResult {
    val session = VCaseSession(host, config)
    session.cases.forEach { session.run(it) }
    return session.finish()
}

/** One [runVCases], step by step: the manifest when created, then [run] per case, then [finish]. */
internal class VCaseSession(
    private val host: VCaseHost,
    private val config: VCaseDesktopConfig,
) {
    private val scanned = VCaseScanner.scan(config.packages, config.sourceRoot)
    private val manifest = VCaseScanner.manifest(host.project, host.defaults, scanned)
    private val manifestDiff: ManifestDiff?
    private val golden = GoldenRun(GoldenStore(config.goldensDir, host.renderer), config.outputDir, config.mode, config.tolerance)
    private val ran = mutableSetOf<String>()

    /** The cases to render: none for a scan-only run. */
    val cases: List<ScannedCase> = if (config.scanOnly) emptyList() else scanned.filter { config.matches(it.entry.id) }

    init {
        ManifestValidation.requireValid(manifest)
        manifestDiff =
            config.manifestFile?.let { file ->
                if (config.mode == VCaseMode.Record) {
                    VCaseJson.write(manifest, file)
                    null
                } else if (file.isFile) {
                    ManifestDiff.between(VCaseJson.read(file), manifest)
                } else {
                    ManifestDiff(manifest.cases.map { it.id }, emptyList(), emptyMap(), listOf("missing ${file.path}"))
                }
            }
    }

    /** Renders every variant of [case] and records or verifies it. */
    fun run(case: ScannedCase): List<VariantResult> {
        val results = AxisExpansion.expand(case.entry, manifest.defaults).map { variant ->
            val problem = case.problem
            val outcome =
                when {
                    case.entry.needsDevice -> CaptureOutcome.Skipped("needsDevice: not rendered on ${host.renderer}")
                    problem != null -> CaptureOutcome.Error(problem)
                    else -> DesktopRenderer.render(host, variant) { case.Content() }
                }
            golden.accept(variant, outcome)
        }
        // Only now: a case that threw leaves the run incomplete, so its goldens are not orphans.
        ran += case.entry.id
        return results
    }

    /** Writes the report and, when every case ran, checks for orphan goldens (record mode deletes them). */
    fun finish(): VCaseRunResult {
        if (config.scanOnly) return VCaseRunResult(manifest, manifestDiff, null)
        val complete = scanned.all { it.entry.id in ran }
        return VCaseRunResult(manifest, manifestDiff, golden.finish(complete, title = "vcase ${host.project} (${host.renderer})"))
    }
}
