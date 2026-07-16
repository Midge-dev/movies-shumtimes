package com.moviesshumtimes.tv.ui.auth

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun QrCodeImage(content: String, modifier: Modifier = Modifier, sizePx: Int = 300) {
    val bitmap = remember(content, sizePx) { encodeQrCode(content, sizePx) }
    Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = modifier)
}

private fun encodeQrCode(content: String, sizePx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            val color = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            bitmap.setPixel(x, y, color)
        }
    }
    return bitmap
}
