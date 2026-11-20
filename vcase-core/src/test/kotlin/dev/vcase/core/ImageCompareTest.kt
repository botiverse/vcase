package dev.vcase.core

import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageCompareTest {
    private fun img(
        w: Int = 8,
        h: Int = 6,
        argb: Int = 0xFF336699.toInt(),
    ) = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).apply {
        for (y in 0 until h) for (x in 0 until w) setRGB(x, y, argb)
    }

    @Test
    fun identicalImagesMatch() {
        val r = ImageCompare.compare(img(), img())
        assertTrue(r.matches)
        assertEquals(0, r.mismatchedPixels)
        assertNull(r.diffImage)
        assertNull(r.mismatchBounds)
    }

    @Test
    fun oneChannelOffByOneFails() {
        val actual = img().apply { setRGB(3, 2, 0xFF33669A.toInt()) }
        val r = ImageCompare.compare(img(), actual)
        assertFalse(r.matches)
        assertEquals(1, r.mismatchedPixels)
        assertEquals(PixelRect(3, 2, 1, 1), r.mismatchBounds)
        assertEquals(0xFFFF0000.toInt(), assertNotNull(r.diffImage).getRGB(3, 2))
    }

    @Test
    fun alphaOnlyDifferenceFails() {
        val actual = img().apply { setRGB(0, 0, 0xFE336699.toInt()) }
        assertFalse(ImageCompare.compare(img(), actual).matches)
    }

    @Test
    fun sizeMismatchFails() {
        val r = ImageCompare.compare(img(8, 6), img(8, 7))
        assertFalse(r.sizeMatches)
        assertFalse(r.matches)
        assertEquals(8, r.mismatchedPixels)
        assertEquals(PixelRect(0, 6, 8, 1), r.mismatchBounds)
    }

    @Test
    fun toleranceHooks() {
        val actual = img().apply { setRGB(1, 1, 0xFF34669A.toInt()) }
        assertTrue(ImageCompare.compare(img(), actual, PixelTolerance.channelDelta(1)).matches)
        assertFalse(ImageCompare.compare(img(), actual, PixelTolerance.channelDelta(0)).matches)
        val inRegion = PixelTolerance.byRegion(listOf(RegionTolerance("header", 0, 0, 2, 2, RegionTolerance.IGNORE)))
        assertTrue(ImageCompare.compare(img(), actual, inRegion).matches)
        val elsewhere = PixelTolerance.byRegion(listOf(RegionTolerance("header", 4, 4, 2, 2, RegionTolerance.IGNORE)))
        assertFalse(ImageCompare.compare(img(), actual, elsewhere).matches)
    }

    @Test
    fun pngFileRoundTripIsExact() {
        val dir = kotlin.io.path.createTempDirectory("vcase").toFile()
        val a = dir.resolve("a.png")
        val src = img().apply { setRGB(2, 2, 0x80FF0000.toInt()) }
        javax.imageio.ImageIO.write(src, "png", a)
        assertTrue(ImageCompare.compare(src, ImageCompare.readPng(a)).matches)
    }
}
