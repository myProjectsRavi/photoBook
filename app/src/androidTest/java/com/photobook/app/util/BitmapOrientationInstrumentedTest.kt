package com.photobook.app.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BitmapOrientationInstrumentedTest {

    @Test
    fun everyExifOrientation_mapsPixelsToExpectedUprightGrid() {
        val source = Bitmap.createBitmap(2, 3, Bitmap.Config.ARGB_8888)
        val a = Color.rgb(255, 0, 0)
        val b = Color.rgb(0, 255, 0)
        val c = Color.rgb(0, 0, 255)
        val d = Color.rgb(255, 255, 0)
        val e = Color.rgb(255, 0, 255)
        val f = Color.rgb(0, 255, 255)
        source.setPixels(
            intArrayOf(a, b, c, d, e, f),
            0,
            2,
            0,
            0,
            2,
            3,
        )

        try {
            assertPixels(
                BitmapOrientation.upright(source, ExifInterface.ORIENTATION_NORMAL)!!,
                2,
                3,
                intArrayOf(a, b, c, d, e, f),
                recycleIfDistinctFrom = source,
            )
            assertPixels(
                BitmapOrientation.upright(source, ExifInterface.ORIENTATION_FLIP_HORIZONTAL)!!,
                2,
                3,
                intArrayOf(b, a, d, c, f, e),
                recycleIfDistinctFrom = source,
            )
            assertPixels(
                BitmapOrientation.upright(source, ExifInterface.ORIENTATION_ROTATE_180)!!,
                2,
                3,
                intArrayOf(f, e, d, c, b, a),
                recycleIfDistinctFrom = source,
            )
            assertPixels(
                BitmapOrientation.upright(source, ExifInterface.ORIENTATION_FLIP_VERTICAL)!!,
                2,
                3,
                intArrayOf(e, f, c, d, a, b),
                recycleIfDistinctFrom = source,
            )
            assertPixels(
                BitmapOrientation.upright(source, ExifInterface.ORIENTATION_TRANSPOSE)!!,
                3,
                2,
                intArrayOf(a, c, e, b, d, f),
                recycleIfDistinctFrom = source,
            )
            assertPixels(
                BitmapOrientation.upright(source, ExifInterface.ORIENTATION_ROTATE_90)!!,
                3,
                2,
                intArrayOf(e, c, a, f, d, b),
                recycleIfDistinctFrom = source,
            )
            assertPixels(
                BitmapOrientation.upright(source, ExifInterface.ORIENTATION_TRANSVERSE)!!,
                3,
                2,
                intArrayOf(f, d, b, e, c, a),
                recycleIfDistinctFrom = source,
            )
            assertPixels(
                BitmapOrientation.upright(source, ExifInterface.ORIENTATION_ROTATE_270)!!,
                3,
                2,
                intArrayOf(b, d, f, a, c, e),
                recycleIfDistinctFrom = source,
            )
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    private fun assertPixels(
        bitmap: Bitmap,
        expectedWidth: Int,
        expectedHeight: Int,
        expected: IntArray,
        recycleIfDistinctFrom: Bitmap,
    ) {
        try {
            assertEquals(expectedWidth, bitmap.width)
            assertEquals(expectedHeight, bitmap.height)
            val actual = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(actual, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertArrayEquals(expected, actual)
        } finally {
            if (bitmap !== recycleIfDistinctFrom && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }
}
