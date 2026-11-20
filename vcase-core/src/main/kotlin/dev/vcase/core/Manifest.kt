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
}

/** A named viewport: [w] x [h] dp at [density] px per dp. */
@Serializable
public data class ManifestViewport(
    val name: String,
    val w: Int,
    val h: Int,
    val density: Float,
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
) {
    public companion object {
        public const val KIND_COMPONENT: String = "component"
        public const val KIND_SCREEN: String = "screen"
    }
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
