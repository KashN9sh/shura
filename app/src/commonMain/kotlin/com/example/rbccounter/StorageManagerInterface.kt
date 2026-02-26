package com.example.rbccounter

/**
 * Общий интерфейс хранилища (реализации — actual в android/desktop).
 */
interface StorageManager {
    suspend fun saveProcessedImage(
        processedImage: ProcessedImage,
        original: PlatformImage,
        annotated: PlatformImage,
        allImages: List<ProcessedImage>
    ): Boolean
    suspend fun loadProcessedImages(): List<ProcessedImage>
    suspend fun loadAnnotatedImage(id: String): PlatformImage?
    suspend fun loadOriginalImage(id: String): PlatformImage?
    suspend fun deleteProcessedImage(imageId: String, allImages: List<ProcessedImage>): Boolean
    suspend fun updateProcessedImage(
        processedImage: ProcessedImage,
        annotated: PlatformImage,
        allImages: List<ProcessedImage>
    ): Boolean
}
