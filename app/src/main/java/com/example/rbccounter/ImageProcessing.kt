package com.example.rbccounter

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

object ImageProcessing {
    fun countCells(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height

        val gray = toGrayscale(bitmap)
        val blurred = boxBlur3x3(gray, width, height, 1)
        val threshold = otsuThreshold(blurred)
        val binary = thresholdBinary(blurred, width, height, threshold, foregroundIsDark = true)
        val opened = open3x3(binary, width, height, iterations = 1)

        val minArea = 50
        val maxArea = (width * height) / 5 // отсечь крупные слипшиеся области
        return countConnected(opened, width, height, minArea, maxArea)
    }

    private fun toGrayscale(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val out = ByteArray(width * height)
        var i = 0
        while (i < pixels.size) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            // luma Rec. 601
            val y = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            out[i] = y.toByte()
            i++
        }
        return out
    }

    private fun boxBlur3x3(src: ByteArray, width: Int, height: Int, iterations: Int): ByteArray {
        var current = src
        repeat(max(1, iterations)) {
            val tmp = ByteArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    var sum = 0
                    var cnt = 0
                    val y0 = max(0, y - 1)
                    val y1 = min(height - 1, y + 1)
                    val x0 = max(0, x - 1)
                    val x1 = min(width - 1, x + 1)
                    var yy = y0
                    while (yy <= y1) {
                        var xx = x0
                        val row = yy * width
                        while (xx <= x1) {
                            sum += (current[row + xx].toInt() and 0xFF)
                            cnt++
                            xx++
                        }
                        yy++
                    }
                    tmp[y * width + x] = (sum / cnt).toByte()
                }
            }
            current = tmp
        }
        return current
    }

    private fun otsuThreshold(gray: ByteArray): Int {
        val hist = IntArray(256)
        for (v in gray) hist[v.toInt() and 0xFF]++
        val total = gray.size
        var sum = 0L
        for (i in 0..255) sum += i * hist[i]

        var sumB = 0L
        var wB = 0
        var wF: Int
        var maxVar = -1.0
        var threshold = 0
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            wF = total - wB
            if (wF == 0) break
            sumB += t.toLong() * hist[t]
            val mB = sumB.toDouble() / wB
            val mF = (sum - sumB).toDouble() / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > maxVar) {
                maxVar = between
                threshold = t
            }
        }
        return threshold
    }

    private fun thresholdBinary(
        gray: ByteArray,
        width: Int,
        height: Int,
        threshold: Int,
        foregroundIsDark: Boolean
    ): BooleanArray {
        val out = BooleanArray(width * height)
        val thr = threshold and 0xFF
        var i = 0
        if (foregroundIsDark) {
            while (i < gray.size) {
                out[i] = (gray[i].toInt() and 0xFF) < thr
                i++
            }
        } else {
            while (i < gray.size) {
                out[i] = (gray[i].toInt() and 0xFF) > thr
                i++
            }
        }
        return out
    }

    private fun erode3x3(binary: BooleanArray, width: Int, height: Int): BooleanArray {
        val out = BooleanArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var keep = true
                loop@ for (dy in -1..1) {
                    val yy = y + dy
                    if (yy !in 0 until height) { keep = false; break }
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx !in 0 until width || !binary[yy * width + xx]) { keep = false; break@loop }
                    }
                }
                out[y * width + x] = keep
            }
        }
        return out
    }

    private fun dilate3x3(binary: BooleanArray, width: Int, height: Int): BooleanArray {
        val out = BooleanArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var any = false
                loop@ for (dy in -1..1) {
                    val yy = y + dy
                    if (yy !in 0 until height) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx !in 0 until width) continue
                        if (binary[yy * width + xx]) { any = true; break@loop }
                    }
                }
                out[y * width + x] = any
            }
        }
        return out
    }

    private fun open3x3(binary: BooleanArray, width: Int, height: Int, iterations: Int): BooleanArray {
        var out = binary
        repeat(max(1, iterations)) {
            out = erode3x3(out, width, height)
            out = dilate3x3(out, width, height)
        }
        return out
    }

    private fun countConnected(
        binary: BooleanArray,
        width: Int,
        height: Int,
        minArea: Int,
        maxArea: Int
    ): Int {
        val visited = BooleanArray(width * height)
        var count = 0
        val stack = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (!binary[idx] || visited[idx]) continue
                var area = 0
                var sp = 0
                stack[sp++] = idx
                visited[idx] = true
                while (sp > 0) {
                    val cur = stack[--sp]
                    area++
                    val cy = cur / width
                    val cx = cur % width
                    // 8-связность
                    for (dy in -1..1) {
                        val ny = cy + dy
                        if (ny !in 0 until height) continue
                        for (dx in -1..1) {
                            val nx = cx + dx
                            if (nx !in 0 until width) continue
                            if (dx == 0 && dy == 0) continue
                            val nidx = ny * width + nx
                            if (binary[nidx] && !visited[nidx]) {
                                visited[nidx] = true
                                stack[sp++] = nidx
                            }
                        }
                    }
                }
                if (area in minArea..maxArea) count++
            }
        }
        return count
    }
}


