package dev.vcase.compose.desktop

import androidx.compose.runtime.Composable
import dev.vcase.VCase
import dev.vcase.VCaseAxis
import dev.vcase.VCaseKind
import dev.vcase.core.AxisExpansion
import dev.vcase.core.ManifestCase
import dev.vcase.core.ManifestDefaults
import dev.vcase.core.ManifestDivergence
import dev.vcase.core.VCaseManifest
import io.github.classgraph.ClassGraph
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
import sergio.sastre.composable.preview.scanner.jvm.JvmAnnotationInfo
import sergio.sastre.composable.preview.scanner.jvm.JvmAnnotationScanner

/** A case found on the classpath: its manifest entry and how to compose it. */
public class ScannedCase(
    public val entry: ManifestCase,
    private val preview: ComposablePreview<JvmAnnotationInfo>,
    /** Problems that make the case unrenderable here (e.g. a fixture parameter). */
    public val problem: String?,
) {
    @Composable
    public fun Content() {
        preview()
    }
}

/**
 * Finds `@VCase` functions in compiled classes. Discovery and invocation go through
 * ComposablePreviewScanner's [JvmAnnotationScanner] (a custom-annotation scanner on JVM
 * classpaths, so KMP commonMain cases compiled for desktop are found too); ClassGraph adds
 * the source file and line for the manifest.
 */
public object VCaseScanner {
    public fun scan(
        packages: List<String>,
        sourceRoot: String = "",
    ): List<ScannedCase> {
        require(packages.isNotEmpty()) { "give at least one package to scan" }
        val previews =
            JvmAnnotationScanner(VCase::class.java.name)
                .scanPackageTrees(*packages.toTypedArray())
                .includePrivatePreviews()
                .getPreviews()
        val locations = sourceLocations(packages)
        return previews.map { p ->
            val a = annotationOf(p)
            val loc = locations["${p.declaringClass}#${p.methodName}"]
            val source =
                loc?.let { (file, line) ->
                    val dir = p.declaringClass.substringBeforeLast('.', "").replace('.', '/')
                    listOf(sourceRoot.trimEnd('/'), dir, file).filter { it.isNotEmpty() }.joinToString("/") + if (line > 0) ":$line" else ""
                } ?: "${p.declaringClass}.${p.methodName}"
            val problem =
                if (p.methodParametersType.isNotBlank()) {
                    "fixture parameters are not supported yet (${p.methodParametersType}); read LocalVCaseVariant.current.case.fixture instead"
                } else {
                    null
                }
            ScannedCase(entryOf(a, source), p, problem)
        }.sortedBy { it.entry.id }
    }

    /** The manifest a scan produces: cases sorted by id. */
    public fun manifest(
        project: String,
        defaults: ManifestDefaults,
        cases: List<ScannedCase>,
    ): VCaseManifest = VCaseManifest(project = project, defaults = defaults, cases = cases.map { it.entry }).sorted()

    internal fun entryOf(
        a: VCase,
        source: String,
    ): ManifestCase {
        val axes = linkedMapOf<String, List<String>>()
        if (a.theme.isNotEmpty()) axes[VCaseAxis.THEME] = a.theme.toList()
        if (a.viewport.isNotEmpty()) axes[VCaseAxis.VIEWPORT] = a.viewport.toList()
        if (a.locale.isNotEmpty()) axes[VCaseAxis.LOCALE] = a.locale.toList()
        if (a.pointer.isNotEmpty()) axes[VCaseAxis.POINTER] = a.pointer.toList()
        if (a.fontScale.isNotEmpty()) axes[VCaseAxis.FONT_SCALE] = a.fontScale.map { AxisExpansion.formatScale(it) }
        return ManifestCase(
            id = a.id,
            kind = if (a.kind == VCaseKind.Screen) ManifestCase.KIND_SCREEN else ManifestCase.KIND_COMPONENT,
            source = source,
            fixture = a.fixture.ifEmpty { null },
            axes = axes,
            parity = a.parity.toList(),
            divergences =
                a.divergences.map {
                    ManifestDivergence(
                        with = it.with,
                        region = it.region.ifEmpty { null },
                        kind = it.kind,
                        why = it.why,
                        decidedBy = it.decidedBy.ifEmpty { null },
                        ref = it.ref.ifEmpty { null },
                        expires = it.expires.ifEmpty { null },
                    )
                },
            formerly = a.formerly.toList(),
            needsDevice = a.needsDevice,
        )
    }

    private fun annotationOf(p: ComposablePreview<JvmAnnotationInfo>): VCase {
        val cls = Class.forName(p.declaringClass, false, Thread.currentThread().contextClassLoader ?: VCase::class.java.classLoader)
        return cls.declaredMethods.firstNotNullOfOrNull { m -> m.takeIf { it.name == p.methodName }?.getAnnotation(VCase::class.java) }
            ?: error("${p.declaringClass}.${p.methodName}: @VCase not readable at runtime")
    }

    /** `class#method` to (source file name, first line of the method body). */
    private fun sourceLocations(packages: List<String>): Map<String, Pair<String, Int>> =
        ClassGraph()
            .enableClassInfo()
            .enableMethodInfo()
            .enableAnnotationInfo()
            .ignoreMethodVisibility()
            .ignoreClassVisibility()
            .acceptPackages(*packages.toTypedArray())
            .scan()
            .use { scan ->
                scan.getClassesWithMethodAnnotation(VCase::class.java.name).flatMap { ci ->
                    ci.declaredMethodInfo.filter { it.hasAnnotation(VCase::class.java.name) }.map { m ->
                        "${ci.name}#${m.name}" to ((ci.sourceFile ?: "${ci.simpleName}.kt") to m.minLineNum)
                    }
                }.toMap()
            }
}
