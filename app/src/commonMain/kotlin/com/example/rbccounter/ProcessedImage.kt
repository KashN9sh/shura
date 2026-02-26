package com.example.rbccounter

/**
 * Общая модель обработанного изображения без платформенных типов.
 */
data class ProcessedImage(
    val id: String,
    val imageSourceId: String,
    val cellCount: Int,
    val timestamp: Long,
    val colorParams: ImageProcessing.ColorParams
)
