package com.example.rbccounter

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class AndroidStorageManager(private val context: Context) : StorageManager {

    companion object {
        private const val GALLERY_DATA_FILE = "gallery_data.json"
        private const val IMAGES_DIR = "processed_images"
        private const val TAG = "StorageManager"
    }

    private val imagesDir = File(context.filesDir, IMAGES_DIR)
    private val galleryDataFile = File(context.filesDir, GALLERY_DATA_FILE)

    init {
        if (!imagesDir.exists()) imagesDir.mkdirs()
    }

    override suspend fun saveProcessedImage(
        processedImage: ProcessedImage,
        original: PlatformImage,
        annotated: PlatformImage,
        allImages: List<ProcessedImage>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val imageId = processedImage.id
            FileOutputStream(File(imagesDir, "${imageId}_original.jpg")).use {
                it.write(original.encodeToJpegBytes(90))
            }
            FileOutputStream(File(imagesDir, "${imageId}_annotated.jpg")).use {
                it.write(annotated.encodeToJpegBytes(90))
            }
            saveGalleryData(allImages)
            Log.d(TAG, "Изображение сохранено: $imageId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сохранения изображения", e)
            false
        }
    }

    override suspend fun loadProcessedImages(): List<ProcessedImage> = withContext(Dispatchers.IO) {
        try {
            val galleryData = loadGalleryData()
            val list = mutableListOf<ProcessedImage>()
            for (i in 0 until galleryData.length()) {
                val item = galleryData.getJSONObject(i)
                val imageId = item.getString("id")
                val originalFile = File(imagesDir, "${imageId}_original.jpg")
                val annotatedFile = File(imagesDir, "${imageId}_annotated.jpg")
                if (originalFile.exists() && annotatedFile.exists()) {
                    list.add(
                        ProcessedImage(
                            id = imageId,
                            imageSourceId = item.getString("uri"),
                            cellCount = item.getInt("cellCount"),
                            timestamp = item.getLong("timestamp"),
                            colorParams = ImageProcessing.ColorParams(
                                hueMin = item.getDouble("hueMin").toFloat(),
                                hueMax = item.getDouble("hueMax").toFloat(),
                                saturationMin = item.getDouble("saturationMin").toFloat(),
                                valueMin = item.getDouble("valueMin").toFloat(),
                                includeRed = item.getBoolean("includeRed"),
                                forceUniformMode = item.optBoolean("forceUniformMode", false),
                                roiVThreshold = item.optDouble("roiVThreshold", 0.6).toFloat(),
                                roiMarginFraction = item.optDouble("roiMarginFraction", 0.04).toFloat()
                            )
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки изображений", e)
            emptyList()
        }
    }

    override suspend fun loadAnnotatedImage(id: String): PlatformImage? = withContext(Dispatchers.IO) {
        val file = File(imagesDir, "${id}_annotated.jpg")
        if (!file.exists()) return@withContext null
        val bytes = file.readBytes()
        decodePlatformImage(bytes)
    }

    override suspend fun loadOriginalImage(id: String): PlatformImage? = withContext(Dispatchers.IO) {
        val file = File(imagesDir, "${id}_original.jpg")
        if (!file.exists()) return@withContext null
        val bytes = file.readBytes()
        decodePlatformImage(bytes)
    }

    override suspend fun deleteProcessedImage(imageId: String, allImages: List<ProcessedImage>): Boolean =
        withContext(Dispatchers.IO) {
            File(imagesDir, "${imageId}_original.jpg").delete()
            File(imagesDir, "${imageId}_annotated.jpg").delete()
            saveGalleryData(allImages)
            true
        }

    override suspend fun updateProcessedImage(
        processedImage: ProcessedImage,
        annotated: PlatformImage,
        allImages: List<ProcessedImage>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            FileOutputStream(File(imagesDir, "${processedImage.id}_annotated.jpg")).use {
                it.write(annotated.encodeToJpegBytes(90))
            }
            saveGalleryData(allImages)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обновления изображения", e)
            false
        }
    }

    private suspend fun saveGalleryData(allImages: List<ProcessedImage>) {
        withContext(Dispatchers.IO) {
            val jsonArray = JSONArray()
            for (image in allImages) {
                jsonArray.put(JSONObject().apply {
                    put("id", image.id)
                    put("uri", image.imageSourceId)
                    put("cellCount", image.cellCount)
                    put("timestamp", image.timestamp)
                    put("hueMin", image.colorParams.hueMin)
                    put("hueMax", image.colorParams.hueMax)
                    put("saturationMin", image.colorParams.saturationMin)
                    put("valueMin", image.colorParams.valueMin)
                    put("includeRed", image.colorParams.includeRed)
                    put("forceUniformMode", image.colorParams.forceUniformMode)
                    put("roiVThreshold", image.colorParams.roiVThreshold)
                    put("roiMarginFraction", image.colorParams.roiMarginFraction)
                })
            }
            galleryDataFile.writeText(jsonArray.toString())
        }
    }

    private suspend fun loadGalleryData(): JSONArray = withContext(Dispatchers.IO) {
        if (galleryDataFile.exists()) JSONArray(galleryDataFile.readText()) else JSONArray()
    }
}
