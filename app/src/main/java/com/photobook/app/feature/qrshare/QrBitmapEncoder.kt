package com.photobook.app.feature.qrshare

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrBitmapEncoder {
    fun encode(payload: String, sizePx: Int = 760): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "ISO-8859-1",
        )
        val matrix = MultiFormatWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            hints,
        )

        val pixels = IntArray(sizePx * sizePx)
        var index = 0
        for (y in 0 until sizePx) {
            for (x in 0 until sizePx) {
                pixels[index] = if (matrix[x, y]) Color.BLACK else Color.WHITE
                index += 1
            }
        }

        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        return bitmap
    }
}
