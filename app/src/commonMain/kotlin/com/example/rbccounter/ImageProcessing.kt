package com.example.rbccounter

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object ImageProcessing {
    /**
     * Параметры для настройки цветовой детекции
     */
    data class ColorParams(
        val hueMin: Float = 250f,      // Минимальный оттенок (0-360)
        val hueMax: Float = 360f,      // Максимальный оттенок (0-360)
        val saturationMin: Float = 0.2f,  // Минимальная насыщенность (0-1)
        val valueMin: Float = 0.12f,      // Минимальная яркость (0-1)
        val includeRed: Boolean = true,   // Включать ли красный диапазон (0-30)
        val forceUniformMode: Boolean = false,  // Принудительно использовать режим однородных клеток
        val roiVThreshold: Float = 0.6f,  // Порог яркости для ROI (0-1)
        val roiMarginFraction: Float = 0.04f  // Отступ ROI от края (0-1)
    )

    /**
     * Подсчёт фиолетовых ядер эритроцитов.
     * Этапы: HSV-маска (фиолетовый диапазон), морфологическое открытие, фильтрация по площади и вытянутости.
     */
    fun countPurpleNuclei(image: PlatformImage): Int {
        val boxes = detectPurpleNucleiBoxes(image)
        return boxes.size
    }

    /**
     * Улучшенный подсчёт эритроцитов с поддержкой разных типов изображений.
     * Автоматически определяет тип изображения и применяет соответствующий алгоритм.
     */
    fun countRedBloodCells(image: PlatformImage): Int {
        val boxes = detectRedBloodCellsBoxes(image)
        return boxes.size
    }

    /**
     * Подсчёт эритроцитов с настраиваемыми цветовыми параметрами
     */
    fun countRedBloodCellsWithParams(image: PlatformImage, params: ColorParams): Int {
        val boxes = detectRedBloodCellsBoxesWithParams(image, params)
        return boxes.size
    }

    /**
     * Возвращает изображение с белыми обводками вокруг найденных ядер и их количество.
     */
    fun annotatePurpleNuclei(image: PlatformImage): Pair<PlatformImage, Int> {
        val boxes = detectPurpleNucleiBoxes(image)
        val annotated = drawBoxes(image, boxes)
        return annotated to boxes.size
    }

    /**
     * Улучшенная аннотация с поддержкой разных типов эритроцитов.
     */
    fun annotateRedBloodCells(image: PlatformImage): Pair<PlatformImage, Int> {
        val boxes = detectRedBloodCellsBoxes(image)
        val annotated = drawBoxes(image, boxes)
        return annotated to boxes.size
    }

    /**
     * Аннотация с настраиваемыми цветовыми параметрами
     */
    fun annotateRedBloodCellsWithParams(image: PlatformImage, params: ColorParams): Pair<PlatformImage, Int> {
        val boxes = detectRedBloodCellsBoxesWithParams(image, params)
        val annotated = drawBoxes(image, boxes)
        return annotated to boxes.size
    }

    /**
     * Отладочная функция для визуализации цветовой маски
     */
    fun debugColorMask(image: PlatformImage): PlatformImage {
        val width = image.width
        val height = image.height
        val result = image.copy()
        val pixels = IntArray(width * height)
        result.getPixels(pixels)

        val mask = maskUniformCells(image)

        for (i in pixels.indices) {
            if (mask[i]) {
                // Подсвечиваем детектированные пиксели красным
                pixels[i] = HsvUtils.redColor()
            }
        }

        result.setPixels(pixels)
        return result
    }

    /**
     * Отладочная функция с настраиваемыми параметрами
     */
    fun debugColorMaskWithParams(image: PlatformImage, params: ColorParams): PlatformImage {
        val width = image.width
        val height = image.height
        val result = image.copy()
        val pixels = IntArray(width * height)
        result.getPixels(pixels)

        val mask = maskUniformCellsWithParams(image, params)

        for (i in pixels.indices) {
            if (mask[i]) {
                // Подсвечиваем детектированные пиксели красным
                pixels[i] = HsvUtils.redColor()
            }
        }

        result.setPixels(pixels)
        return result
    }

    /**
     * Создает изображение с визуализацией ROI (области интереса)
     */
    fun visualizeRoi(image: PlatformImage, params: ColorParams): PlatformImage {
        val width = image.width
        val height = image.height
        val result = image.copy()
        val pixels = IntArray(width * height)
        result.getPixels(pixels)

        val roi = createCircularRoi(image, params.roiVThreshold, params.roiMarginFraction)

        // Находим центр и радиус ROI
        var centerX = 0.0
        var centerY = 0.0
        var count = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (roi[idx]) {
                    centerX += x
                    centerY += y
                    count++
                }
            }
        }

        val cx: Double
        val cy: Double
        val radius: Double
        if (count > 0) {
            cx = centerX / count
            cy = centerY / count
            var maxDist = 0.0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val idx = y * width + x
                    if (roi[idx]) {
                        val dx = x - cx
                        val dy = y - cy
                        val distance = sqrt(dx * dx + dy * dy)
                        if (distance > maxDist) {
                            maxDist = distance
                        }
                    }
                }
            }
            radius = maxDist
        } else {
            cx = width / 2.0
            cy = height / 2.0
            radius = min(width, height) / 2.0
        }

        val semiTransparentGreen = (100 shl 24 or 0x00FF00).toInt()
        val green = 0xFF00FF00.toInt()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - cx
                val dy = y - cy
                val d = sqrt(dx * dx + dy * dy)
                val idx = y * width + x
                when {
                    d <= radius - 1 -> pixels[idx] = semiTransparentGreen
                    d <= radius + 2 -> pixels[idx] = green
                }
            }
        }

        result.setPixels(pixels)
        return result
    }

    private fun toGrayscale(image: PlatformImage): ByteArray {
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getPixels(pixels)
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

    private fun detectPurpleNucleiBoxes(image: PlatformImage): List<BBox> {
        val width = image.width
        val height = image.height
        // 1) Маска фиолетовых пикселей
        val mask = maskPurpleHSV(image)
        // 2) Закрытие, чтобы замкнуть «кольца» ядер
        var refined = close3x3(mask, width, height, iterations = 1)
        // 3) Заливка дыр (превращаем кольца в сплошные эллипсы)
        refined = fillHoles(refined, width, height)
        // 4) Лёгкое открытие для удаления мелкого мусора
        val opened = open3x3(refined, width, height, iterations = 1)
        val roi = createCircularRoi(image, vThreshold = 0.6f, marginFraction = 0.04f)

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

    /**
     * Улучшенный детектор эритроцитов с поддержкой разных типов изображений.
     * Автоматически определяет тип изображения и применяет соответствующий алгоритм.
     */
    private fun detectRedBloodCellsBoxes(image: PlatformImage): List<BBox> {
        // Определяем тип изображения по анализу цветов
        val imageType = analyzeImageType(image)

        return when (imageType) {
            ImageType.PURPLE_NUCLEI -> detectPurpleNucleiBoxes(image)
            ImageType.UNIFORM_CELLS -> detectUniformCellsBoxes(image)
            ImageType.MIXED -> detectMixedCellsBoxes(image)
        }
    }

    /**
     * Улучшенный детектор эритроцитов с поддержкой разных типов изображений.
     * Автоматически определяет тип изображения и применяет соответствующий алгоритм.
     */
    private fun detectRedBloodCellsBoxesWithParams(image: PlatformImage, params: ColorParams): List<BBox> {
        // Если включен принудительный режим, используем настраиваемые параметры
        if (params.forceUniformMode) {
            return detectUniformCellsBoxesWithParams(image, params)
        }

        // Определяем тип изображения по анализу цветов с учетом параметров
        val imageType = analyzeImageTypeWithParams(image, params)

        return when (imageType) {
            ImageType.PURPLE_NUCLEI -> detectPurpleNucleiBoxes(image)
            ImageType.UNIFORM_CELLS -> detectUniformCellsBoxesWithParams(image, params)
            ImageType.MIXED -> detectMixedCellsBoxesWithParams(image, params)
        }
    }

    private enum class ImageType {
        PURPLE_NUCLEI,    // Эритроциты с фиолетовыми ядрами
        UNIFORM_CELLS,    // Однородные эритроциты (как на ваших фото)
        MIXED            // Смешанный тип
    }

    private fun analyzeImageType(image: PlatformImage): ImageType {
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getPixels(pixels)

        var purpleCount = 0
        var uniformCount = 0
        var totalSampled = 0

        val step = max(1, min(width, height) / 100) // Сэмплируем каждый 100-й пиксель

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val idx = y * width + x
                val c = pixels[idx]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val hsv = HsvUtils.rgbToHsv(r, g, b)

                val h = hsv[0]
                val s = hsv[1]
                val v = hsv[2]

                // Фиолетовые ядра
                if (h in 250f..270f && s >= 0.35f && v >= 0.15f) {
                    purpleCount++
                }

                // Однородные эритроциты (пурпурные/малиновые)
                if ((h in 250f..360f) && s >= 0.25f && v >= 0.15f) {
                    uniformCount++
                }

                totalSampled++
            }
        }

        val purpleRatio = purpleCount.toFloat() / totalSampled
        val uniformRatio = uniformCount.toFloat() / totalSampled

        return when {
            purpleRatio > 0.01f -> ImageType.PURPLE_NUCLEI
            uniformRatio > 0.01f -> ImageType.UNIFORM_CELLS
            else -> ImageType.MIXED
        }
    }

    /**
     * Анализ типа изображения с настраиваемыми параметрами
     */
    private fun analyzeImageTypeWithParams(image: PlatformImage, params: ColorParams): ImageType {
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getPixels(pixels)

        var purpleCount = 0
        var uniformCount = 0
        var totalSampled = 0

        val step = max(1, min(width, height) / 100) // Сэмплируем каждый 100-й пиксель

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val idx = y * width + x
                val c = pixels[idx]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val hsv = HsvUtils.rgbToHsv(r, g, b)

                val h = hsv[0]
                val s = hsv[1]
                val v = hsv[2]

                // Фиолетовые ядра (фиксированные параметры)
                if (h in 250f..270f && s >= 0.35f && v >= 0.15f) {
                    purpleCount++
                }

                // Однородные эритроциты с настраиваемыми параметрами
                val isPurpleHue = h in params.hueMin..params.hueMax
                val isRedHue = if (params.includeRed) h in 0f..30f else false
                val hasGoodSaturation = s >= params.saturationMin
                val hasGoodValue = v >= params.valueMin

                if ((isPurpleHue || isRedHue) && hasGoodSaturation && hasGoodValue) {
                    uniformCount++
                }

                totalSampled++
            }
        }

        val purpleRatio = purpleCount.toFloat() / totalSampled
        val uniformRatio = uniformCount.toFloat() / totalSampled

        return when {
            purpleRatio > 0.01f -> ImageType.PURPLE_NUCLEI
            uniformRatio > 0.01f -> ImageType.UNIFORM_CELLS
            else -> ImageType.MIXED
        }
    }

    /**
     * Детектор для однородных эритроцитов (как на ваших фото)
     */
    private fun detectUniformCellsBoxes(image: PlatformImage): List<BBox> {
        val width = image.width
        val height = image.height

        // 1) Маска пурпурных/малиновых эритроцитов
        val mask = maskUniformCells(image)

        // 2) Морфологические операции для очистки
        var refined = close3x3(mask, width, height, iterations = 2)
        refined = fillHoles(refined, width, height)
        val opened = open3x3(refined, width, height, iterations = 1)

        // 3) ROI - круговая область интереса
        val roi = createCircularRoi(image, vThreshold = 0.5f, marginFraction = 0.05f)

        // 4) Параметры фильтрации для однородных эритроцитов
        val totalPx = width * height
        val minArea = max(10, totalPx / 250_000)  // Еще меньше минимальная площадь
        val maxArea = max(250, totalPx / 1_000)   // Еще больше максимальная площадь
        val minAspect = 1.0
        val maxAspect = 5.0  // Еще менее строгие требования к форме

        return findConnectedComponents(opened, roi, width, height, minArea, maxArea, minAspect, maxAspect)
    }

    /**
     * Детектор для однородных эритроцитов с настраиваемыми цветовыми параметрами
     */
    private fun detectUniformCellsBoxesWithParams(image: PlatformImage, params: ColorParams): List<BBox> {
        val width = image.width
        val height = image.height

        // 1) Маска пурпурных/малиновых эритроцитов
        val mask = maskUniformCellsWithParams(image, params)

        // 2) Морфологические операции для очистки
        var refined = close3x3(mask, width, height, iterations = 2)
        refined = fillHoles(refined, width, height)
        val opened = open3x3(refined, width, height, iterations = 1)

        // 3) ROI - круговая область интереса
        val roi = createCircularRoi(image, vThreshold = params.roiVThreshold, marginFraction = params.roiMarginFraction)

        // 4) Параметры фильтрации для однородных эритроцитов
        val totalPx = width * height
        val minArea = max(10, totalPx / 250_000)  // Еще меньше минимальная площадь
        val maxArea = max(250, totalPx / 1_000)   // Еще больше максимальная площадь
        val minAspect = 1.0
        val maxAspect = 5.0  // Еще менее строгие требования к форме

        return findConnectedComponents(opened, roi, width, height, minArea, maxArea, minAspect, maxAspect)
    }

    /**
     * Детектор для смешанных типов изображений
     */
    private fun detectMixedCellsBoxes(image: PlatformImage): List<BBox> {
        val width = image.width
        val height = image.height

        // Комбинируем маски разных типов
        val purpleMask = maskPurpleHSV(image)
        val uniformMask = maskUniformCells(image)

        // Объединяем маски
        val combinedMask = BooleanArray(width * height) { i ->
            purpleMask[i] || uniformMask[i]
        }

        // Морфологические операции
        var refined = close3x3(combinedMask, width, height, iterations = 1)
        refined = fillHoles(refined, width, height)
        val opened = open3x3(refined, width, height, iterations = 1)

        val roi = createCircularRoi(image, vThreshold = 0.55f, marginFraction = 0.04f)

        val totalPx = width * height
        val minArea = max(12, totalPx / 180_000)
        val maxArea = max(180, totalPx / 1_400)
        val minAspect = 1.0
        val maxAspect = 5.0

        return findConnectedComponents(opened, roi, width, height, minArea, maxArea, minAspect, maxAspect)
    }

    /**
     * Детектор для смешанных типов изображений с настраиваемыми цветовыми параметрами
     */
    private fun detectMixedCellsBoxesWithParams(image: PlatformImage, params: ColorParams): List<BBox> {
        val width = image.width
        val height = image.height

        // Комбинируем маски разных типов
        val purpleMask = maskPurpleHSV(image)
        val uniformMask = maskUniformCellsWithParams(image, params)

        // Объединяем маски
        val combinedMask = BooleanArray(width * height) { i ->
            purpleMask[i] || uniformMask[i]
        }

        // Морфологические операции
        var refined = close3x3(combinedMask, width, height, iterations = 1)
        refined = fillHoles(refined, width, height)
        val opened = open3x3(refined, width, height, iterations = 1)

        val roi = createCircularRoi(image, vThreshold = 0.55f, marginFraction = 0.04f)

        val totalPx = width * height
        val minArea = max(12, totalPx / 180_000)
        val maxArea = max(180, totalPx / 1_400)
        val minAspect = 1.0
        val maxAspect = 5.0

        return findConnectedComponents(opened, roi, width, height, minArea, maxArea, minAspect, maxAspect)
    }

    /**
     * Маска для однородных эритроцитов (пурпурные/малиновые)
     */
    private fun maskUniformCells(image: PlatformImage): BooleanArray {
        val width = image.width
        val height = image.height
        val out = BooleanArray(width * height)
        val pixels = IntArray(width * height)
        image.getPixels(pixels)

        var i = 0
        while (i < pixels.size) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val hsv = HsvUtils.rgbToHsv(r, g, b)

            val h = hsv[0] // [0..360)
            val s = hsv[1] // [0..1]
            val v = hsv[2] // [0..1]

            // Расширенный диапазон для пурпурных/малиновых эритроцитов
            // Охватываем весь фиолетовый-пурпурный-малиновый спектр
            val isPurpleHue = h in 250f..360f  // Фиолетовый-пурпурный-малиновый
            val isRedHue = h in 0f..30f       // Красный (для малиновых оттенков)

            // Более мягкие требования к насыщенности и яркости
            val hasGoodSaturation = s >= 0.2f
            val hasGoodValue = v >= 0.12f

            out[i] = (isPurpleHue || isRedHue) && hasGoodSaturation && hasGoodValue
            i++
        }
        return out
    }

    /**
     * Маска для однородных эритроцитов с настраиваемыми цветовыми параметрами
     */
    private fun maskUniformCellsWithParams(image: PlatformImage, params: ColorParams): BooleanArray {
        val width = image.width
        val height = image.height
        val out = BooleanArray(width * height)
        val pixels = IntArray(width * height)
        image.getPixels(pixels)

        var i = 0
        while (i < pixels.size) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val hsv = HsvUtils.rgbToHsv(r, g, b)

            val h = hsv[0] // [0..360)
            val s = hsv[1] // [0..1]
            val v = hsv[2] // [0..1]

            // Расширенный диапазон для пурпурных/малиновых эритроцитов
            // Охватываем весь фиолетовый-пурпурный-малиновый спектр
            val isPurpleHue = h in params.hueMin..params.hueMax  // Фиолетовый-пурпурный-малиновый
            val isRedHue = if (params.includeRed) h in 0f..30f else false  // Красный (для малиновых оттенков)

            // Более мягкие требования к насыщенности и яркости
            val hasGoodSaturation = s >= params.saturationMin
            val hasGoodValue = v >= params.valueMin

            out[i] = (isPurpleHue || isRedHue) && hasGoodSaturation && hasGoodValue
            i++
        }
        return out
    }

    /**
     * Общая функция поиска связанных компонентов
     */
    private fun findConnectedComponents(
        binary: BooleanArray,
        roi: BooleanArray,
        width: Int,
        height: Int,
        minArea: Int,
        maxArea: Int,
        minAspect: Double,
        maxAspect: Double
    ): List<BBox> {
        val visited = BooleanArray(width * height)
        val boxes = ArrayList<BBox>()
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
                            if (binary[nidx] && !visited[nidx]) {
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

    private fun drawBoxes(src: PlatformImage, boxes: List<BBox>): PlatformImage {
        val result = src.copy()
        val width = result.width
        val height = result.height
        val pixels = IntArray(width * height)
        result.getPixels(pixels)

        val white = 0xFFFFFFFF.toInt()

        for (b in boxes) {
            val cx = (b.minX + b.maxX) / 2.0
            val cy = (b.minY + b.maxY) / 2.0
            val a = max(1.0, (b.maxX - b.minX + 1) / 2.0)
            val bAxis = max(1.0, (b.maxY - b.minY + 1) / 2.0)
            val left = max(0, b.minX - 3)
            val right = min(width - 1, b.maxX + 3)
            val top = max(0, b.minY - 3)
            val bottom = min(height - 1, b.maxY + 3)

            for (y in top..bottom) {
                for (x in left..right) {
                    val dx = (x - cx) / a
                    val dy = (y - cy) / bAxis
                    val f = dx * dx + dy * dy
                    val distToEllipse = abs(sqrt(f) - 1.0) * min(a, bAxis)
                    if (distToEllipse < 2) {
                        pixels[y * width + x] = white
                    }
                }
            }
        }

        result.setPixels(pixels)
        return result
    }

    private fun maskPurpleHSV(image: PlatformImage): BooleanArray {
        val width = image.width
        val height = image.height
        val out = BooleanArray(width * height)
        val pixels = IntArray(width * height)
        image.getPixels(pixels)
        var i = 0
        while (i < pixels.size) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val hsv = HsvUtils.rgbToHsv(r, g, b)
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
    private fun createCircularRoi(image: PlatformImage, vThreshold: Float, marginFraction: Float): BooleanArray {
        val width = image.width
        val height = image.height
        val pixels = IntArray(width * height)
        image.getPixels(pixels)

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
                val hsv = HsvUtils.rgbToHsv(r, g, b)
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
                val hsv = HsvUtils.rgbToHsv(r, g, b)
                if (hsv[2] >= vThreshold) {
                    val dx = x - cx
                    val dy = y - cy
                    distances.add(sqrt(dx * dx + dy * dy))
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
            val d = sqrt(dx * dx + dy * dy)
            mask[i] = d <= (radius - margin)
            i++
        }
        return mask
    }
}
