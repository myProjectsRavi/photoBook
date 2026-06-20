import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File

fun main() {
    try {
        val payload = "A".repeat(2500)
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "ISO-8859-1"
        )
        MultiFormatWriter().encode(payload, BarcodeFormat.QR_CODE, 600, 600, hints)
        println("Success")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
