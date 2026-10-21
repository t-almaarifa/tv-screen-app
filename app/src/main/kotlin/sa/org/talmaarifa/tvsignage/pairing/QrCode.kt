package sa.org.talmaarifa.tvsignage.pairing

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Renders the pairing code as a QR bitmap so an operator can scan it with a phone instead of
 * retyping six digits into the admin panel. docs/tv-device-api.md does not define any richer payload
 * for this (no deep-link format, no server-side QR), so the QR simply encodes the plain [code] text —
 * scanning it is a convenience for accurate entry, not a separate pairing channel.
 */
object QrCode {
    fun encode(code: String, sizePx: Int = 360): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
        )
        val matrix = QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        return bitmap
    }
}
