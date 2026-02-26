package com.example.rbccounter

/**
 * Ожидаемое объявление платформенного изображения (Android: Bitmap, Desktop: BufferedImage).
 */
expect class PlatformImage(width: Int, height: Int) {
    val width: Int
    val height: Int
    fun getPixels(pixels: IntArray)
    fun setPixels(pixels: IntArray)
    fun copy(): PlatformImage
    fun encodeToJpegBytes(quality: Int = 90): ByteArray
}

expect fun decodePlatformImage(bytes: ByteArray): PlatformImage?

expect fun createPlatformImage(width: Int, height: Int): PlatformImage
