package dev.vcase.core

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * `vcase.manifest.json`: every case of a project, produced by a scan of the source code
 * (never written by hand). Committed so reviews show added and removed cases; CI scans again
 * and fails when the result differs from the committed file ([ManifestDiff]).
 */
@Serializable
public data class VCaseManifest(
    val schema: String = SCHEMA,
    val project: String,
    val defaults: ManifestDefaults,
    val cases: List<ManifestCase>,
) {
    public fun case(id: String): ManifestCase? = cases.firstOrNull { it.id == id }

    /** Same manifest with cases sorted by id: the canonical order scans write. */
    public fun sorted(): VCaseManifest = copy(cases = cases.sortedBy { it.id })

    public companion object {
        public const val SCHEMA: String = "vcase/1"
        public const val FILE_NAME: String = "vcase.manifest.json"
    }
}

/** Project-wide axis values; a case lists only the axes it changes. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class ManifestDefaults(
    val theme: List<String>,
    val viewport: List<ManifestViewport>,
    val locale: List<String>,
    val pointer: List<String>,
    /** Omitted from the JSON while it is the single scale 1.0. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val fontScale: List<Float> = listOf(1f),
) {
    public fun viewport(name: String): ManifestViewport? = viewport.firstOrNull { it.name == name }

    /** Viewports a case without a viewport override expands over. */
    public fun defaultViewports(): List<ManifestViewport> = viewport.filterNot { it.optIn }
}

/**
 * A named viewport: [w] x [h] dp at [density] px per dp. Every declared viewport is part of
 * the default expansion unless [optIn] is set: then only cases that name it use it
 * (e.g. `wide` for screens only). [optIn] is an addition to the design-doc schema and is
 * omitted from the JSON while false.
 *
 * [sizeClass] is the layout mode the host pins for this viewport (`compact`, `wide`, ...;
 * see `VCaseSizeClass`), handed to the host as `variant.axes.viewport.sizeClass`. Omitted
 * from the JSON while null.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class ManifestViewport(
    val name: String,
    val w: Int,
    val h: Int,
    val density: Float,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val optIn: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val sizeClass: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class ManifestCase(
    val id: String,
    /** `component` or `screen` ([KIND_COMPONENT], [KIND_SCREEN]). */
    val kind: String = KIND_COMPONENT,
    /** `path/to/File.kt:line` of the declaration. */
    val source: String,
    val fixture: String? = null,
    /** Axis overrides, axis name to values (viewports by name). */
    val axes: Map<String, List<String>> = emptyMap(),
    val parity: List<String> = emptyList(),
    val divergences: List<ManifestDivergence> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val formerly: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val needsDevice: Boolean = false,
    /** Per-case capture settings; omitted from the JSON while the case uses the host's. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val capture: ManifestCapture? = null,
) {
    public companion object {
        public const val KIND_COMPONENT: String = "component"
        public const val KIND_SCREEN: String = "screen"
    }
}

/**
 * How one case is captured, where it differs from the host. Every field is optional (null =
 * the host's value) and omitted from the JSON while null; a case that sets none has no
 * `capture` object at all.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class ManifestCapture(
    /** Bound in dp of a component capture's width (content wider than the host's bound). */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val maxWidth: Int? = null,
    /** Bound in dp of a component capture's height. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val maxHeight: Int? = null,
    /** Frames rendered before the captured one. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val settleFrames: Int? = null,
    /** Frame `i` renders at `i * frameIntervalMs` ms; 0 = every frame at time 0. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val frameIntervalMs: Int? = null,
) {
    public val isEmpty: Boolean get() = maxWidth == null && maxHeight == null && settleFrames == null && frameIntervalMs == null

    /** Frame time of frame [index] in nanoseconds, or null when the host decides. */
    public fun frameTimeNanos(index: Int): Long? = frameIntervalMs?.let { index.toLong() * it * 1_000_000L }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class ManifestDivergence(
    val with: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val region: String? = null,
    val kind: String,
    val why: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val decidedBy: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val ref: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val expires: String? = null,
)

/** JSON form of the manifest and of capture metadata: pretty, stable, defaults written. */
public object VCaseJson {
    @OptIn(ExperimentalSerializationApi::class)
    public val json: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    public fun encode(manifest: VCaseManifest): String = json.encodeToString(VCaseManifest.serializer(), manifest) + "\n"

    public fun decodeManifest(text: String): VCaseManifest {
        val manifest = json.decodeFromString(VCaseManifest.serializer(), text)
        require(manifest.schema == VCaseManifest.SCHEMA) {
            "unsupported manifest schema '${manifest.schema}' (expected ${VCaseManifest.SCHEMA})"
        }
        return manifest
    }

    public fun read(file: File): VCaseManifest = decodeManifest(file.readText())

    public fun write(manifest: VCaseManifest, file: File) {
        file.absoluteFile.parentFile?.mkdirs()
        file.writeText(encode(manifest))
    }
}
