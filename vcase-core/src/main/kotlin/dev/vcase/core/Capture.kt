package dev.vcase.core

import kotlinx.serialization.Serializable

/**
 * Metadata an adapter writes next to every capture (`<id>@<axes>.json`), the adapter
 * contract shared by all platforms.
 */
@Serializable
public data class CaptureMeta(
    val id: String,
    val axes: Map<String, String>,
    /** `desktop`, `android`, `web`, `ios`. */
    val platform: String,
    /** Renderer the image came from, also the golden directory name: `desktop-skiko`. */
    val renderer: String,
    val fontsHash: String? = null,
    val durationMs: Long,
    /** Named regions in image pixels, `[x, y, w, h]`. */
    val regions: Map<String, List<Int>> = emptyMap(),
    val warnings: List<String> = emptyList(),
) {
    public fun toJson(): String = VCaseJson.json.encodeToString(serializer(), this) + "\n"

    public companion object {
        public fun fromJson(text: String): CaptureMeta = VCaseJson.json.decodeFromString(serializer(), text)
    }
}

/** What an adapter hands back for one variant: the PNG and its metadata, or an error. */
public sealed interface CaptureOutcome {
    public class Image(
        public val png: ByteArray,
        public val meta: CaptureMeta,
    ) : CaptureOutcome

    /** Render threw, timed out, or hit an unmocked request: no image for this variant. */
    public class Error(
        public val message: String,
        public val cause: Throwable? = null,
    ) : CaptureOutcome

    /** The adapter does not render this case (e.g. `needsDevice` on a JVM adapter). */
    public class Skipped(
        public val reason: String,
    ) : CaptureOutcome
}
