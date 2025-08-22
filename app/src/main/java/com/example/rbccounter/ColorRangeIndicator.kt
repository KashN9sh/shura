package com.example.rbccounter

import android.graphics.Bitmap as AndroidBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

/**
 * Создает визуальный индикатор выбранного цветового диапазона
 */
@Composable
fun ColorRangeIndicator(
    hueMin: Float,
    hueMax: Float,
    saturationMin: Float,
    valueMin: Float,
    includeRed: Boolean,
    modifier: Modifier = Modifier
) {
    val width = 300
    val height = 60

    val colorBitmap = remember(hueMin, hueMax, saturationMin, valueMin, includeRed) {
        createHueGradientBitmap(width, height, hueMin, hueMax, saturationMin, valueMin, includeRed)
    }

    Column(modifier = modifier) {
        Text(
            text = "Выбранный цветовой диапазон",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Image(
            bitmap = colorBitmap.asImageBitmap(),
            contentDescription = "Color range indicator",
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Верхняя часть = яркие цвета, нижняя = с учетом настроек",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

private fun createHueGradientBitmap(
    width: Int,
    height: Int,
    hueMin: Float,
    hueMax: Float,
    saturationMin: Float,
    valueMin: Float,
    includeRed: Boolean
): AndroidBitmap {
    val bitmap = AndroidBitmap.createBitmap(width, height, AndroidBitmap.Config.ARGB_8888)
    val pixels = IntArray(width * height)

    for (x in 0 until width) {
        val hue = (x.toFloat() / width) * 360f

        // Проверяем, попадает ли цвет в выбранный диапазон
        val isInRange = when {
            includeRed && hue <= 30f -> true  // Красный диапазон
            hue >= hueMin && hue <= hueMax -> true  // Основной диапазон
            else -> false
        }

        if (isInRange) {
            // Создаем градиент по высоте для показа влияния насыщенности и яркости
            for (y in 0 until height) {
                val yRatio = y.toFloat() / height

                // Верхняя половина - показывает цвета с максимальной насыщенностью
                // Нижняя половина - показывает цвета с минимальной насыщенностью
                val saturation = if (yRatio < 0.5f) {
                    1.0f  // Максимальная насыщенность
                } else {
                    saturationMin  // Минимальная насыщенность из настроек
                }

                val value = if (yRatio < 0.5f) {
                    1.0f  // Максимальная яркость
                } else {
                    valueMin  // Минимальная яркость из настроек
                }

                val color = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
                pixels[y * width + x] = color
            }
        } else {
            // Темный цвет для недетектируемого диапазона
            val darkColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.2f, 0.2f))
            for (y in 0 until height) {
                pixels[y * width + x] = darkColor
            }
        }
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    return bitmap
}
