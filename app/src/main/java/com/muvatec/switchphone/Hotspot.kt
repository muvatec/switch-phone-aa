package com.muvatec.switchphone

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.security.SecureRandom

/**
 * Helpers for the "internet for passengers" card.
 *
 * Android does not let a normal app turn the hotspot on or change its name/password, so the app
 * (1) stores the name and password you want, (2) shows a Wi-Fi QR that passengers can scan, and
 * (3) opens the system hotspot screen in one tap. The real hotspot must be configured once with the same data.
 */
object Hotspot {
    private val ALPHABET = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generatePassword(length: Int = 10): String {
        val r = SecureRandom()
        return (1..length).map { ALPHABET[r.nextInt(ALPHABET.length)] }.joinToString("")
    }

    fun validName(name: String) = name.isNotBlank() && name.toByteArray().size <= 32
    fun validPassword(pass: String) = pass.length in 8..63

    private fun esc(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"")

    /** Standard Wi-Fi QR payload understood by Android and iOS cameras. */
    fun wifiQrText(name: String, secure: Boolean, pass: String): String =
        if (secure) "WIFI:T:WPA;S:${esc(name)};P:${esc(pass)};;" else "WIFI:T:nopass;S:${esc(name)};;"

    fun qrBitmap(text: String, size: Int = 512): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }

    /** Opens the system hotspot screen (public intent). */
    fun openSettings(ctx: Context) {
        val i = Intent("android.settings.TETHER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }.onFailure {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun copy(ctx: Context, label: String, text: String) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
