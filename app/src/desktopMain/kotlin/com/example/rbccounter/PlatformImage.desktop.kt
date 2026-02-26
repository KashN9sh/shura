package com.example.rbccounter

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

actual class PlatformImage actual constructor(
    width: Int,
    height: Int
) {
    private val bufferedImage: BufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    constructor(source: BufferedImage) : this(source.width, source.height) {
        val pixels = IntArray(source.width * source.height)
        source.getRGB(0, 0, source.width, source.height, pixels, 0, source.width)
        bufferedImage.setRGB(0, 0, source.width, source.height, pixels, 0, source.width)
    }

    actual val width: Int get() = bufferedImage.width
    actual val height: Int get() = bufferedImage.height

    actual fun getPixels(pixels: IntArray) {
        bufferedImage.getRGB(0, 0, width, height, pixels, 0, width)
    }

    actual fun setPixels(pixels: IntArray) {
        bufferedImage.setRGB(0, 0, width, height, pixels, 0, width)
    }

    actual fun copy(): PlatformImage {
        val copy = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        copy.graphics.drawImage(bufferedImage, 0, 0, null)
        return PlatformImage(copy)
    }

    actual fun encodeToJpegBytes(quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val rgb = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        rgb.graphics.drawImage(bufferedImage, 0, 0, null)
        ImageIO.write(rgb, "jpg", out)
        return out.toByteArray()
    }

    fun getBufferedImage(): BufferedImage = bufferedImage
}

actual fun decodePlatformImage(bytes: ByteArray): PlatformImage? {
    return try {
        val img = ImageIO.read(ByteArrayInputStream(bytes)) ?: return null
        PlatformImage(img)
    } catch (e: Exception) {
        null
    }
}

actual fun createPlatformImage(width: Int, height: Int): PlatformImage = PlatformImage(width, height)
