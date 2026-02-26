package com.example.rbccounter

import kotlin.math.max
import kotlin.math.min

object HsvUtils {
    private const val RED = 0xFFFF0000.toInt()

    fun rgbToHsv(r: Int, g: Int, b: Int): FloatArray {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val maxC = max(rf, max(gf, bf))
        val minC = min(rf, min(gf, bf))
        val delta = maxC - minC
        var h = 0f
        val s = if (maxC == 0f) 0f else delta / maxC
        val v = maxC
        if (delta != 0f) {
            h = when (maxC) {
                rf -> ((gf - bf) / delta) % 6f
                gf -> (bf - rf) / delta + 2f
                else -> (rf - gf) / delta + 4f
            }
            if (h < 0) h += 6f
            h *= 60f
        }
        return floatArrayOf(h, s, v)
    }

    fun hsvToArgb(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2f - 1))
        val m = v - c
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return (0xFF shl 24) or
            ((r + m).coerceIn(0f, 1f) * 255).toInt() shl 16 or
            ((g + m).coerceIn(0f, 1f) * 255).toInt() shl 8 or
            (b + m).coerceIn(0f, 1f).toInt() * 255
    }

    fun redColor(): Int = RED
}
