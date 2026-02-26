package com.example.rbccounter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    val colorImage = remember(hueMin, hueMax, saturationMin, valueMin, includeRed) {
        createHueGradientImage(width, height, hueMin, hueMax, saturationMin, valueMin, includeRed)
    }
    Column(modifier = modifier) {
        Text(
            text = "Выбранный цветовой диапазон",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        PlatformImageContent(
            image = colorImage,
            contentDescription = "Color range",
            modifier = Modifier.fillMaxWidth().height(60.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Верхняя часть = яркие цвета, нижняя = с учетом настроек",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

private fun createHueGradientImage(
    width: Int,
    height: Int,
    hueMin: Float,
    hueMax: Float,
    saturationMin: Float,
    valueMin: Float,
    includeRed: Boolean
): PlatformImage {
    val image = createPlatformImage(width, height)
    val pixels = IntArray(width * height)
    for (x in 0 until width) {
        val hue = (x.toFloat() / width) * 360f
        val isInRange = when {
            includeRed && hue <= 30f -> true
            hue in hueMin..hueMax -> true
            else -> false
        }
        for (y in 0 until height) {
            val yRatio = y.toFloat() / height
            val saturation = if (yRatio < 0.5f) 1f else saturationMin
            val value = if (yRatio < 0.5f) 1f else valueMin
            val color = if (isInRange) HsvUtils.hsvToArgb(hue, saturation, value)
            else HsvUtils.hsvToArgb(hue, 0.2f, 0.2f)
            pixels[y * width + x] = color
        }
    }
    image.setPixels(pixels)
    return image
}
