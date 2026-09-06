package com.example.myapplicationtest.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun compressAndEncodeToBase64(context: Context, uri: Uri, maxWidth: Int = 800): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val original = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        val scaled = if (original.width > maxWidth) {
            val ratio = maxWidth.toFloat() / original.width
            Bitmap.createScaledBitmap(original, maxWidth, (original.height * ratio).toInt(), true)
        } else {
            original
        }

        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
        Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
    } catch (_: Exception) {
        null
    }
}

fun decodeBase64ToBytes(base64: String): ByteArray? {
    return try {
        Base64.decode(base64, Base64.DEFAULT)
    } catch (_: Exception) {
        null
    }
}

fun formatCreatedAt(createdAt: String?): String {
    if (createdAt == null) return ""
    return try {
        val dt = LocalDateTime.parse(createdAt, DateTimeFormatter.ISO_DATE_TIME)
        dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    } catch (_: Exception) {
        ""
    }
}
