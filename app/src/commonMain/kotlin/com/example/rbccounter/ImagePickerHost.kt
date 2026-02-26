package com.example.rbccounter

/**
 * Платформенный выбор изображений (Android: Activity result, Desktop: FileDialog).
 * Реализации передаются в RbcCounterApp из MainActivity / main().
 */
interface ImagePickerHost {
    fun pickSingleImage(callback: (PlatformImage?) -> Unit)
    fun pickMultipleImages(callback: (List<PlatformImage>) -> Unit)
}
