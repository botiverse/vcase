package dev.vcase.compose.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.vcase.core.ManifestCase
import dev.vcase.core.ManifestDefaults
import dev.vcase.core.VCaseVariant

/**
 * What a project plugs into the desktop adapter. The framework knows nothing about the
 * project's theme or its "freeze" switches; the host applies them per variant.
 *
 * Typical host: wraps content in the app theme for `variant.axes.theme`, maps
 * `variant.axes.pointer` to the theme's pointer mode, pins any window-size driven scale, and
 * provides the project's still-animation / fixed-clock CompositionLocals.
 */
public interface VCaseHost {
    /** `project` in the manifest. */
    public val project: String

    /** Project axis defaults (`defaults` in the manifest). */
    public val defaults: ManifestDefaults

    /** Renderer id, also the golden subdirectory. Change it when the rendering stack changes. */
    public val renderer: String get() = RENDERER

    /**
     * Frames rendered before the captured one (popups lay themselves out a frame late). A case's
     * `@VCase(settleFrames = ...)` replaces it for that case's capture pass; the measure pass of
     * a wrap-content capture always renders this many.
     */
    public val settleFrames: Int get() = 0

    /**
     * Frame time of frame [index] (0 = first). Every frame time is fixed, so animations and
     * `withFrameNanos` clocks are deterministic; the default steps 16 ms per frame from 0.
     * A case's `@VCase(frameIntervalMs = ...)` replaces it for that case.
     */
    public fun frameTimeNanos(index: Int): Long = index * 16_000_000L

    /** Hash of the fonts the host loads, written to capture metadata; null if not tracked. */
    public val fontsHash: String? get() = null

    /**
     * How big the capture is; default: components at content size, screens at the viewport.
     * A case's `@VCase(maxWidth = ..., maxHeight = ...)` replaces the [CaptureSize.WrapContent]
     * bounds returned here.
     */
    public fun captureSize(variant: VCaseVariant): CaptureSize =
        if (variant.case.kind == ManifestCase.KIND_SCREEN) CaptureSize.Viewport else CaptureSize.WrapContent()

    /** Called before rendering a variant, e.g. `Locale.setDefault(...)`, a fixed clock. */
    public fun beforeCapture(variant: VCaseVariant) {}

    /** Called after rendering a variant (also after a failure). */
    public fun afterCapture(variant: VCaseVariant) {}

    /**
     * Wraps every case: theme for the axis values, background, freeze settings. Pin the
     * layout mode from `variant.axes.viewport.sizeClass` (e.g. a wide type scale) and the input
     * mode from `variant.axes.pointer`, not from the capture size.
     */
    @Composable
    public fun Wrap(
        variant: VCaseVariant,
        content: @Composable () -> Unit,
    )

    public companion object {
        public const val RENDERER: String = "desktop-skiko"
    }
}

/** Capture size policy. */
public sealed interface CaptureSize {
    /** Exactly the viewport (`w` x `h` dp at its density). */
    public data object Viewport : CaptureSize

    /**
     * The wrapped content's measured size, bounded by [maxWidthDp] x [maxHeightDp]
     * (null = viewport width, 4x viewport height). Content reaching a bound gets a warning.
     */
    public data class WrapContent(
        val maxWidthDp: Int? = null,
        val maxHeightDp: Int? = null,
    ) : CaptureSize
}

/** The variant being rendered: case code can read its axes and fixture name. */
public val LocalVCaseVariant: androidx.compose.runtime.ProvidableCompositionLocal<VCaseVariant?> =
    staticCompositionLocalOf { null }
