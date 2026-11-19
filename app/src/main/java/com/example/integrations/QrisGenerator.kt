package com.example.integrations

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.roundToLong

object QrisGenerator {

    /**
     * Generates a standard EMVCo / QRIS dynamic payload string with CRC16-CCITT checksum.
     */
    fun buildQrisPayload(
        merchantId: String,
        merchantName: String,
        branchCity: String,
        amountIdr: Double,
        referenceId: String
    ): String {
        val cleanName = merchantName.take(25).ifBlank { "KASIRPRO MERCHANT" }.uppercase()
        val cleanCity = branchCity.take(15).ifBlank { "JAKARTA" }.uppercase()
        val cleanMerchantId = merchantId.take(32).ifBlank { "ID1020304050607" }
        val amountStr = amountIdr.roundToLong().coerceAtLeast(1L).toString()
        val cleanRef = referenceId.take(20).ifBlank { "TRX001" }

        val merchantAccountInfo = tlv("00", "ID.CO.QRIS.WWW") +
            tlv("01", cleanMerchantId) +
            tlv("02", "03") +
            tlv("03", "URE")

        val additionalData = tlv("01", cleanRef) + tlv("07", "KASIRPRO")

        val bodyWithoutCrc = StringBuilder().apply {
            append(tlv("00", "01")) // Payload Format Indicator
            append(tlv("01", "12")) // Dynamic QRIS
            append(tlv("26", merchantAccountInfo))
            append(tlv("52", "5812")) // Merchant Category Code (Retail/F&B)
            append(tlv("53", "360"))  // Currency Code IDR (360)
            append(tlv("54", amountStr))
            append(tlv("58", "ID"))   // Country Code
            append(tlv("59", cleanName))
            append(tlv("60", cleanCity))
            append(tlv("62", additionalData))
            append("6304")
        }.toString()

        val crc = crc16Ccitt(bodyWithoutCrc.toByteArray(Charsets.UTF_8))
        return bodyWithoutCrc + "%04X".format(crc)
    }

    fun renderQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) Color.parseColor("#0B3B3C") else Color.WHITE
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tlv(tag: String, value: String): String {
        val len = "%02d".format(value.length.coerceAtMost(99))
        return "$tag$len$value"
    }

    private fun crc16Ccitt(bytes: ByteArray): Int {
        var crc = 0xFFFF
        for (b in bytes) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            for (i in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc and 0xFFFF
    }
}
