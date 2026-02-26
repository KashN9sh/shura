package com.example.rbccounter

import kotlin.math.min

fun scaleToMax(image: PlatformImage, maxSide: Int): PlatformImage {
    val w = image.width
    val h = image.height
    val scale = if (w >= h) maxSide.toFloat() / w else maxSide.toFloat() / h
    if (scale >= 1f) return image.copy()
    val newW = (w * scale).toInt().coerceAtLeast(1)
    val newH = (h * scale).toInt().coerceAtLeast(1)
    val result = createPlatformImage(newW, newH)
    val srcPixels = IntArray(w * h)
    image.getPixels(srcPixels)
    val dstPixels = IntArray(newW * newH)
    for (y in 0 until newH) {
        for (x in 0 until newW) {
            val srcX = (x / scale).toInt().coerceIn(0, w - 1)
            val srcY = (y / scale).toInt().coerceIn(0, h - 1)
            dstPixels[y * newW + x] = srcPixels[srcY * w + srcX]
        }
    }
    result.setPixels(dstPixels)
    return result
}
