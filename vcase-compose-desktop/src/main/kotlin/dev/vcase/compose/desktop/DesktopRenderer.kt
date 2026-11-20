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
        val size =
            when (val policy = host.captureSize(variant)) {
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
                    }.use { scene -> renderFrames(host, scene) }
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
            val image = renderFrames(host, scene)
            image.encodeToData(EncodedImageFormat.PNG)?.bytes ?: error("PNG encode failed for ${variant.fileStem}")
        }
    }

    private fun renderFrames(
        host: VCaseHost,
        scene: ImageComposeScene,
    ): org.jetbrains.skia.Image {
        repeat(host.settleFrames) { scene.render(host.frameTimeNanos(it)) }
        return scene.render(host.frameTimeNanos(host.settleFrames))
    }

    private fun px(
        dp: Int,
        density: Float,
    ): Int = (dp * density).roundToInt()
}
