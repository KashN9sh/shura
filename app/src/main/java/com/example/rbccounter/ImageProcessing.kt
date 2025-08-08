package com.example.rbccounter

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

object ImageProcessing {
    /**
     * Подсчёт фиолетовых ядер эритроцитов.
     * Этапы: HSV-маска (фиолетовый диапазон), морфологическое открытие, фильтрация по площади и вытянутости.
     */
    fun countPurpleNuclei(bitmap: Bitmap): Int {
        val boxes = detectPurpleNucleiBoxes(bitmap)
        return boxes.size
    }

    /**
     * Возвращает изображение с белыми обводками вокруг найденных ядер и их количество.
     */
    fun annotatePurpleNuclei(bitmap: Bitmap): Pair<Bitmap, Int> {
        val boxes = detectPurpleNucleiBoxes(bitmap)
        val annotated = drawBoxes(bitmap, boxes)
        return annotated to boxes.size
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

    private fun close3x3(binary: BooleanArray, width: Int, height: Int, iterations: Int): BooleanArray {
        var out = binary
        repeat(max(1, iterations)) {
            out = dilate3x3(out, width, height)
            out = erode3x3(out, width, height)
        }
        return out
    }

    // Заполнение дыр внутри объектов: инверсия → заполнение внешнего фона → инверсия
    private fun fillHoles(binary: BooleanArray, width: Int, height: Int): BooleanArray {
        val inv = BooleanArray(width * height) { !binary[it] }
        // помечаем внешнее пространство начиная с границ
        val queue = IntArray(width * height)
        var qh = 0
        var qt = 0
        val visited = BooleanArray(width * height)
        fun enqueue(i: Int) { if (!visited[i] && inv[i]) { visited[i] = true; queue[qt++] = i } }
        for (x in 0 until width) { enqueue(x); enqueue((height - 1) * width + x) }
        for (y in 0 until height) { enqueue(y * width); enqueue(y * width + (width - 1)) }
        while (qh < qt) {
            val idx = queue[qh++]
            val y = idx / width
            val x = idx % width
            if (y > 0) enqueue(idx - width)
            if (y < height - 1) enqueue(idx + width)
            if (x > 0) enqueue(idx - 1)
            if (x < width - 1) enqueue(idx + 1)
        }
        // клетки inv, не достигнутые из внешнего фона → это дырки, заливаем их
        val out = BooleanArray(width * height)
        for (i in 0 until width * height) {
            // если это объект или это инвертированный фон, достигнутый снаружи
            out[i] = binary[i] || (!visited[i] && inv[i])
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

    private fun countConnectedFiltered(
        binary: BooleanArray,
        width: Int,
        height: Int,
        minArea: Int,
        maxArea: Int,
        minAspect: Double,
        maxAspect: Double
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
                var minX = x
                var maxX = x
                var minY = y
                var maxY = y
                stack[sp++] = idx
                visited[idx] = true
                while (sp > 0) {
                    val cur = stack[--sp]
                    area++
                    val cy = cur / width
                    val cx = cur % width
                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy
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
                val boxW = (maxX - minX + 1).coerceAtLeast(1)
                val boxH = (maxY - minY + 1).coerceAtLeast(1)
                val aspect = max(boxW, boxH).toDouble() / min(boxW, boxH).toDouble()
                if (area in minArea..maxArea && aspect in minAspect..maxAspect) {
                    count++
                }
            }
        }
        return count
    }

    private data class BBox(
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int,
        val area: Int
    )

    private fun detectPurpleNucleiBoxes(bitmap: Bitmap): List<BBox> {
        val width = bitmap.width
        val height = bitmap.height
        // 1) Маска фиолетовых пикселей
        val mask = maskPurpleHSV(bitmap)
        // 2) Закрытие, чтобы замкнуть «кольца» ядер
        var refined = close3x3(mask, width, height, iterations = 1)
        // 3) Заливка дыр (превращаем кольца в сплошные эллипсы)
        refined = fillHoles(refined, width, height)
        // 4) Лёгкое открытие для удаления мелкого мусора
        val opened = open3x3(refined, width, height, iterations = 1)
        val roi = createCircularRoi(bitmap, vThreshold = 0.6f, marginFraction = 0.04f)

        val totalPx = width * height
        val minArea = max(10, totalPx / 180_000)
        val maxArea = max(150, totalPx / 1_500)
        val minAspect = 1.0
        val maxAspect = 6.0

        val visited = BooleanArray(width * height)
        val boxes = ArrayList<BBox>()
        val stack = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (!opened[idx] || visited[idx]) continue
                var area = 0
                var sp = 0
                var minX = x
                var maxX = x
                var minY = y
                var maxY = y
                stack[sp++] = idx
                visited[idx] = true
                var fullyInsideRoi = true
                while (sp > 0) {
                    val cur = stack[--sp]
                    area++
                    val cy = cur / width
                    val cx = cur % width
                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy
                    if (!roi[cur]) fullyInsideRoi = false
                    for (dy in -1..1) {
                        val ny = cy + dy
                        if (ny !in 0 until height) continue
                        for (dx in -1..1) {
                            val nx = cx + dx
                            if (nx !in 0 until width) continue
                            if (dx == 0 && dy == 0) continue
                            val nidx = ny * width + nx
                            if (opened[nidx] && !visited[nidx]) {
                                visited[nidx] = true
                                stack[sp++] = nidx
                            }
                        }
                    }
                }
                val touchesBorder = (minX == 0 || minY == 0 || maxX == width - 1 || maxY == height - 1)
                val boxW = (maxX - minX + 1).coerceAtLeast(1)
                val boxH = (maxY - minY + 1).coerceAtLeast(1)
                val aspect = max(boxW, boxH).toDouble() / min(boxW, boxH).toDouble()
                if (!touchesBorder && fullyInsideRoi && area in minArea..maxArea && aspect in minAspect..maxAspect) {
                    boxes.add(BBox(minX, minY, maxX, maxY, area))
                }
            }
        }
        return boxes
    }

    private fun drawBoxes(src: Bitmap, boxes: List<BBox>): Bitmap {
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = (max(result.width, result.height) / 300f).coerceAtLeast(2f)
        }
        for (b in boxes) {
            val rect = RectF(
                b.minX.toFloat() - 2f,
                b.minY.toFloat() - 2f,
                b.maxX.toFloat() + 2f,
                b.maxY.toFloat() + 2f
            )
            canvas.drawOval(rect, paint)
        }
        return result
    }

    private fun maskPurpleHSV(bitmap: Bitmap): BooleanArray {
        val width = bitmap.width
        val height = bitmap.height
        val out = BooleanArray(width * height)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val hsv = FloatArray(3)
        var i = 0
        while (i < pixels.size) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            Color.RGBToHSV(r, g, b, hsv)
            val h = hsv[0] // [0..360)
            val s = hsv[1] // [0..1]
            val v = hsv[2] // [0..1]
            // Фиолетовый/пурпурный диапазон. Немного расширен для устойчивости.
            val isPurpleHue = (h in 250f..270f)
            out[i] = isPurpleHue && s >= 0.35f && v >= 0.15f
            i++
        }
        return out
    }

    /**
     * Маска области обзора (круглая светлая зона). Чуть эродируем, чтобы исключить пограничные клетки.
     */
    private fun createCircularRoi(bitmap: Bitmap, vThreshold: Float, marginFraction: Float): BooleanArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val hsv = FloatArray(3)

        // Оценим центр и радиус по ярким точкам (внутри круга фон светлый)
        var sumX = 0.0
        var sumY = 0.0
        var count = 0
        val step = max(1, min(width, height) / 256)
        val distances = ArrayList<Double>(4096)
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val idx = y * width + x
                val c = pixels[idx]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                Color.RGBToHSV(r, g, b, hsv)
                if (hsv[2] >= vThreshold) {
                    sumX += x
                    sumY += y
                    count++
                }
                x += step
            }
            y += step
        }
        val cx = if (count > 0) sumX / count else width / 2.0
        val cy = if (count > 0) sumY / count else height / 2.0

        // Соберём распределение дистанций ярких пикселей до центра
        y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val idx = y * width + x
                val c = pixels[idx]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                Color.RGBToHSV(r, g, b, hsv)
                if (hsv[2] >= vThreshold) {
                    val dx = x - cx
                    val dy = y - cy
                    distances.add(kotlin.math.sqrt(dx * dx + dy * dy))
                }
                x += step
            }
            y += step
        }
        distances.sort()
        val qIndex = if (distances.isNotEmpty()) (distances.size * 0.98).toInt().coerceAtMost(distances.size - 1) else 0
        val radius = if (distances.isNotEmpty()) distances[qIndex] else (min(width, height) / 2.0)
        val margin = (radius * marginFraction).coerceAtLeast(3.0)

        val mask = BooleanArray(width * height)
        var i = 0
        while (i < pixels.size) {
            val x = i % width
            val y2 = i / width
            val dx = x - cx
            val dy = y2 - cy
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            mask[i] = d <= (radius - margin)
            i++
        }
        return mask
    }
}


