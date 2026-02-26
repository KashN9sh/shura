package com.example.rbccounter

import android.graphics.Bitmap
import android.graphics.BitmapFactory

actual class PlatformImage actual constructor(
    width: Int,
    height: Int
) {
    private val bitmap: Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    companion object {
        fun fromBitmap(b: Bitmap): PlatformImage {
            val wrapper = PlatformImage(b.width, b.height)
            val pixels = IntArray(b.width * b.height)
            b.getPixels(pixels, 0, b.width, 0, 0, b.width, b.height)
            wrapper.bitmap.setPixels(pixels, 0, b.width, 0, 0, b.width, b.height)
            return wrapper
        }
    }

    actual val width: Int get() = bitmap.width
    actual val height: Int get() = bitmap.height

    actual fun getPixels(pixels: IntArray) {
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    }

    actual fun setPixels(pixels: IntArray) {
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    actual fun copy(): PlatformImage {
        return PlatformImage.fromBitmap(bitmap.copy(Bitmap.Config.ARGB_8888, true))
    }

    actual fun encodeToJpegBytes(quality: Int): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    fun getBitmap(): Bitmap = bitmap
}

actual fun decodePlatformImage(bytes: ByteArray): PlatformImage? {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    return PlatformImage.fromBitmap(bitmap)
}

actual fun createPlatformImage(width: Int, height: Int): PlatformImage = PlatformImage(width, height)
