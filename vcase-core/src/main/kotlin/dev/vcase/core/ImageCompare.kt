package dev.vcase.core

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Decides whether one pixel of the actual image matches the expected one. Colors are
 * non-premultiplied ARGB ints ([BufferedImage.getRGB]).
 */
public fun interface PixelTolerance {
    public fun matches(
        x: Int,
        y: Int,
        expected: Int,
        actual: Int,
    ): Boolean

    public companion object {
        /** Same-platform regression rule: every channel identical. */
        public val Exact: PixelTolerance = PixelTolerance { _, _, e, a -> e == a }

        /** Every ARGB channel within [delta]. For cross-platform comparison, not regression. */
        public fun channelDelta(delta: Int): PixelTolerance =
            PixelTolerance { _, _, e, a ->
                var shift = 0
                var ok = true
                while (shift < 32 && ok) {
                    ok = kotlin.math.abs(((e ushr shift) and 0xFF) - ((a ushr shift) and 0xFF)) <= delta
                    shift += 8
                }
                ok
            }

        /**
         * Hook for region rules: the first region containing the pixel decides, others fall
         * back to [default]. Regions come from capture metadata (`regions`) or divergences.
         */
        public fun byRegion(
            regions: List<RegionTolerance>,
            default: PixelTolerance = Exact,
        ): PixelTolerance =
            PixelTolerance { x, y, e, a ->
                (regions.firstOrNull { it.contains(x, y) }?.tolerance ?: default).matches(x, y, e, a)
            }
    }
}

/** A rectangle in image pixels with its own [tolerance]. */
public data class RegionTolerance(
    val name: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val tolerance: PixelTolerance,
) {
    public fun contains(
        px: Int,
        py: Int,
    ): Boolean = px >= x && py >= y && px < x + width && py < y + height

    public companion object {
        /** Differences inside the region never fail (a declared divergence). */
        public val IGNORE: PixelTolerance = PixelTolerance { _, _, _, _ -> true }
    }
}

public data class PixelRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    override fun toString(): String = "[$x,$y ${width}x$height]"
}

public class CompareResult(
    public val expectedWidth: Int,
    public val expectedHeight: Int,
    public val actualWidth: Int,
    public val actualHeight: Int,
    /** Pixels that differ, counting every pixel outside the overlap of two differently sized images. */
    public val mismatchedPixels: Long,
    public val totalPixels: Long,
    /** Bounding box of the mismatched pixels, null when none. */
    public val mismatchBounds: PixelRect?,
    /** Actual image faded to grey with mismatched pixels in red; null when [matches] or not requested. */
    public val diffImage: BufferedImage?,
) {
    public val sizeMatches: Boolean get() = expectedWidth == actualWidth && expectedHeight == actualHeight
    public val matches: Boolean get() = sizeMatches && mismatchedPixels == 0L

    public fun describe(): String =
        when {
            matches -> "identical"
            !sizeMatches -> "size ${expectedWidth}x$expectedHeight -> ${actualWidth}x$actualHeight, $mismatchedPixels px differ"
            else -> "$mismatchedPixels of $totalPixels px differ in $mismatchBounds"
        }
}

/** Image comparison. The default is the zero-tolerance rule: identical ARGB, identical size. */
public object ImageCompare {
    private const val DIFF_RED = 0xFFFF0000.toInt()

    public fun compare(
        expected: BufferedImage,
        actual: BufferedImage,
        tolerance: PixelTolerance = PixelTolerance.Exact,
        withDiffImage: Boolean = true,
    ): CompareResult {
        val w = maxOf(expected.width, actual.width)
        val h = maxOf(expected.height, actual.height)
        val diff = if (withDiffImage) BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB) else null
        var mismatched = 0L
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = -1
        var maxY = -1
        val expRow = IntArray(w)
        val actRow = IntArray(w)
        for (y in 0 until h) {
            val inE = y < expected.height
            val inA = y < actual.height
            if (inE) expected.getRGB(0, y, expected.width, 1, expRow, 0, w)
            if (inA) actual.getRGB(0, y, actual.width, 1, actRow, 0, w)
            for (x in 0 until w) {
                val hasE = inE && x < expected.width
                val hasA = inA && x < actual.width
                val same = hasE && hasA && tolerance.matches(x, y, expRow[x], actRow[x])
                if (!same) {
                    mismatched++
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
                if (diff != null) {
                    diff.setRGB(x, y, if (same) faded(actRow[x]) else DIFF_RED)
                }
            }
        }
        val bounds = if (maxX < 0) null else PixelRect(minX, minY, maxX - minX + 1, maxY - minY + 1)
        val result =
            CompareResult(
                expected.width, expected.height, actual.width, actual.height,
                mismatched, w.toLong() * h, bounds, null,
            )
        return if (result.matches || diff == null) {
            result
        } else {
            CompareResult(expected.width, expected.height, actual.width, actual.height, mismatched, w.toLong() * h, bounds, diff)
        }
    }

    public fun compare(
        expected: File,
        actual: File,
        tolerance: PixelTolerance = PixelTolerance.Exact,
    ): CompareResult = compare(readPng(expected), readPng(actual), tolerance)

    public fun readPng(file: File): BufferedImage = ImageIO.read(file) ?: throw IllegalArgumentException("not an image: $file")

    public fun readPng(bytes: ByteArray): BufferedImage =
        ImageIO.read(ByteArrayInputStream(bytes)) ?: throw IllegalArgumentException("not an image (${bytes.size} bytes)")

    /** Grey at a quarter contrast over white, so red mismatches stand out. */
    private fun faded(argb: Int): Int {
        val a = (argb ushr 24) and 0xFF
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        val lum = (r * 299 + g * 587 + b * 114) / 1000
        val onWhite = (lum * a + 255 * (255 - a)) / 255
        val v = 255 - (255 - onWhite) / 4
        return (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
}
