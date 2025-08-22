package com.example.rbccounter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class StorageManager(private val context: Context) {

    companion object {
        private const val GALLERY_DATA_FILE = "gallery_data.json"
        private const val IMAGES_DIR = "processed_images"
        private const val TAG = "StorageManager"
    }

    private val imagesDir = File(context.filesDir, IMAGES_DIR)
    private val galleryDataFile = File(context.filesDir, GALLERY_DATA_FILE)

    init {
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
    }

    /**
     * Сохраняет обработанное изображение в постоянную память
     */
    suspend fun saveProcessedImage(processedImage: ProcessedImage, allImages: List<ProcessedImage>): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val imageId = processedImage.id

                // Сохраняем оригинальное изображение
                val originalFile = File(imagesDir, "${imageId}_original.jpg")
                processedImage.originalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, FileOutputStream(originalFile))

                // Сохраняем обработанное изображение
                val annotatedFile = File(imagesDir, "${imageId}_annotated.jpg")
                processedImage.annotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, FileOutputStream(annotatedFile))

                // Сохраняем метаданные
                saveGalleryData(allImages)

                Log.d(TAG, "Изображение сохранено: $imageId")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сохранения изображения", e)
            false
        }
    }

    /**
     * Загружает все сохраненные изображения
     */
    suspend fun loadProcessedImages(): List<ProcessedImage> {
        return try {
            withContext(Dispatchers.IO) {
                val galleryData = loadGalleryData()
                val processedImages = mutableListOf<ProcessedImage>()

                for (i in 0 until galleryData.length()) {
                    val item = galleryData.getJSONObject(i)
                    val imageId = item.getString("id")

                    // Загружаем файлы изображений
                    val originalFile = File(imagesDir, "${imageId}_original.jpg")
                    val annotatedFile = File(imagesDir, "${imageId}_annotated.jpg")

                    if (originalFile.exists() && annotatedFile.exists()) {
                        val originalBitmap = BitmapFactory.decodeFile(originalFile.absolutePath)
                        val annotatedBitmap = BitmapFactory.decodeFile(annotatedFile.absolutePath)

                        if (originalBitmap != null && annotatedBitmap != null) {
                            val processedImage = ProcessedImage(
                                id = imageId,
                                uri = Uri.parse(item.getString("uri")),
                                originalBitmap = originalBitmap,
                                annotatedBitmap = annotatedBitmap,
                                cellCount = item.getInt("cellCount"),
                                timestamp = item.getLong("timestamp"),
                                colorParams = ImageProcessing.ColorParams(
                                    hueMin = item.getDouble("hueMin").toFloat(),
                                    hueMax = item.getDouble("hueMax").toFloat(),
                                    saturationMin = item.getDouble("saturationMin").toFloat(),
                                    valueMin = item.getDouble("valueMin").toFloat(),
                                    includeRed = item.getBoolean("includeRed"),
                                    forceUniformMode = item.getBoolean("forceUniformMode")
                                )
                            )
                            processedImages.add(processedImage)
                        }
                    }
                }

                Log.d(TAG, "Загружено изображений: ${processedImages.size}")
                processedImages
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки изображений", e)
            emptyList()
        }
    }

    /**
     * Удаляет изображение из постоянной памяти
     */
    suspend fun deleteProcessedImage(imageId: String, allImages: List<ProcessedImage>): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                // Удаляем файлы изображений
                val originalFile = File(imagesDir, "${imageId}_original.jpg")
                val annotatedFile = File(imagesDir, "${imageId}_annotated.jpg")

                originalFile.delete()
                annotatedFile.delete()

                // Обновляем метаданные
                saveGalleryData(allImages)

                Log.d(TAG, "Изображение удалено: $imageId")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка удаления изображения", e)
            false
        }
    }

    /**
     * Обновляет изображение в постоянной памяти
     */
    suspend fun updateProcessedImage(processedImage: ProcessedImage, allImages: List<ProcessedImage>): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val imageId = processedImage.id

                // Обновляем обработанное изображение
                val annotatedFile = File(imagesDir, "${imageId}_annotated.jpg")
                processedImage.annotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, FileOutputStream(annotatedFile))

                // Обновляем метаданные
                saveGalleryData(allImages)

                Log.d(TAG, "Изображение обновлено: $imageId")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обновления изображения", e)
            false
        }
    }

    /**
     * Сохраняет метаданные галереи в JSON файл
     */
    private suspend fun saveGalleryData(allImages: List<ProcessedImage>) {
        withContext(Dispatchers.IO) {
            try {
                val jsonArray = JSONArray()

                for (image in allImages) {
                    val jsonObject = JSONObject().apply {
                        put("id", image.id)
                        put("uri", image.uri.toString())
                        put("cellCount", image.cellCount)
                        put("timestamp", image.timestamp)
                        put("hueMin", image.colorParams.hueMin)
                        put("hueMax", image.colorParams.hueMax)
                        put("saturationMin", image.colorParams.saturationMin)
                        put("valueMin", image.colorParams.valueMin)
                        put("includeRed", image.colorParams.includeRed)
                        put("forceUniformMode", image.colorParams.forceUniformMode)
                    }
                    jsonArray.put(jsonObject)
                }

                galleryDataFile.writeText(jsonArray.toString())
                Log.d(TAG, "Метаданные сохранены")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка сохранения метаданных", e)
            }
        }
    }

    /**
     * Загружает метаданные галереи из JSON файла
     */
    private suspend fun loadGalleryData(): JSONArray {
        return withContext(Dispatchers.IO) {
            try {
                if (galleryDataFile.exists()) {
                    val content = galleryDataFile.readText()
                    JSONArray(content)
                } else {
                    JSONArray()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки метаданных", e)
                JSONArray()
            }
        }
    }



    /**
     * Очищает все сохраненные данные
     */
    suspend fun clearAllData(): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                // Удаляем все файлы изображений
                imagesDir.listFiles()?.forEach { it.delete() }

                // Удаляем файл метаданных
                galleryDataFile.delete()

                Log.d(TAG, "Все данные очищены")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка очистки данных", e)
            false
        }
    }
}
