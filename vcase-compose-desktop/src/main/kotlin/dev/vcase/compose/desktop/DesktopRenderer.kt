package dev.vcase.compose.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.use
import dev.vcase.core.CaptureMeta
import dev.vcase.core.CaptureOutcome
import dev.vcase.core.VCaseVariant
import org.jetbrains.skia.EncodedImageFormat
import kotlin.math.roundToInt

/** Renders one variant offscreen with skiko and encodes it as PNG. */
public object DesktopRenderer {
    public const val PLATFORM: String = "desktop"

    public fun render(
        host: VCaseHost,
        variant: VCaseVariant,
        content: @Composable () -> Unit,
    ): CaptureOutcome {
        val started = System.nanoTime()
        host.beforeCapture(variant)
        return try {
            val warnings = mutableListOf<String>()
            val png = renderPng(host, variant, content, warnings)
            CaptureOutcome.Image(
                png,
                CaptureMeta(
                    id = variant.id,
                    axes = variant.axisMap(),
                    platform = PLATFORM,
                    renderer = host.renderer,
                    fontsHash = host.fontsHash,
                    durationMs = (System.nanoTime() - started) / 1_000_000,
                    warnings = warnings,
                ),
            )
        } catch (t: Throwable) {
            val root = generateSequence(t) { it.cause }.last()
            val why = if (root === t) "$t" else "$t (cause: $root)"
            CaptureOutcome.Error(why, t)
        } finally {
            host.afterCapture(variant)
        }
    }

    private fun renderPng(
        host: VCaseHost,
        variant: VCaseVariant,
        content: @Composable () -> Unit,
        warnings: MutableList<String>,
    ): ByteArray {
        val vp = variant.axes.viewport
        val density = Density(vp.density, variant.axes.fontScale)
        val framed: @Composable (Modifier) -> Unit = { modifier ->
            CompositionLocalProvider(LocalVCaseVariant provides variant) {
                Box(modifier) { host.Wrap(variant) { content() } }
            }
        }
        val frames = Frames.of(host, variant)
        val size =
            when (val policy = Frames.captureSize(host, variant)) {
                CaptureSize.Viewport -> IntSize(px(vp.width, vp.density), px(vp.height, vp.density))
                is CaptureSize.WrapContent -> {
                    val maxW = px(policy.maxWidthDp ?: vp.width, vp.density)
                    val maxH = px(policy.maxHeightDp ?: (vp.height * 4), vp.density)
                    var measured = IntSize.Zero
                    // Pass 1: measure at natural size within the bounds.
                    ImageComposeScene(width = maxW, height = maxH, density = density) {
                        Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = false)) {
                            framed(Modifier.onSizeChanged { measured = it })
                        }
                    }.use { scene -> frames.render(scene, frames.measureSettle) }
                    check(measured.width > 0 && measured.height > 0) { "${variant.fileStem} rendered nothing" }
                    if (measured.width >= maxW) warnings += "content reaches the max width ($maxW px)"
                    if (measured.height >= maxH) warnings += "content reaches the max height ($maxH px)"
                    measured
                }
            }
        // Pass 2: render at exactly that size.
        return ImageComposeScene(width = size.width, height = size.height, density = density) {
            framed(Modifier)
        }.use { scene ->
            val image = frames.render(scene, frames.captureSettle)
            image.encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("PNG encode failed for ${variant.fileStem}")
        }
    }

    /**
     * Frame plan of one variant. The measure pass (pass 1 of a wrap-content capture) renders the
     * host's [VCaseHost.settleFrames]; the capture pass renders the case's `settleFrames` when it
     * sets one, else the host's. Frame times come from the case's `frameIntervalMs` when it sets
     * one, else from [VCaseHost.frameTimeNanos]. A case that sets neither renders exactly as
     * before per-case settings existed.
     */
    internal class Frames(
        val measureSettle: Int,
        val captureSettle: Int,
        private val time: (Int) -> Long,
    ) {
        /** Frame times of a pass with [settle] settle frames: the settle frames, then the captured one. */
        fun times(settle: Int): List<Long> = (0..settle).map(time)

        fun render(
            scene: ImageComposeScene,
            settle: Int,
        ): org.jetbrains.skia.Image {
            repeat(settle) { scene.render(time(it)) }
            return scene.render(time(settle))
        }

        companion object {
            fun of(
                host: VCaseHost,
                variant: VCaseVariant,
            ): Frames {
                val capture = variant.case.capture
                return Frames(
                    measureSettle = host.settleFrames,
                    captureSettle = capture?.settleFrames ?: host.settleFrames,
                    time = { i -> capture?.frameTimeNanos(i) ?: host.frameTimeNanos(i) },
                )
            }

            /** The host's policy with the case's `maxWidth` / `maxHeight` in place of its bounds. */
            fun captureSize(
                host: VCaseHost,
                variant: VCaseVariant,
            ): CaptureSize {
                val policy = host.captureSize(variant)
                val capture = variant.case.capture ?: return policy
                return when (policy) {
                    CaptureSize.Viewport -> policy
                    is CaptureSize.WrapContent ->
                        policy.copy(
                            maxWidthDp = capture.maxWidth ?: policy.maxWidthDp,
                            maxHeightDp = capture.maxHeight ?: policy.maxHeightDp,
                        )
                }
            }
        }
    }

    private fun px(
        dp: Int,
        density: Float,
    ): Int = (dp * density).roundToInt()
}
