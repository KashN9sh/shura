package com.example.rbccounter

import android.graphics.BitmapFactory
import android.net.Uri
import android.graphics.Bitmap as AndroidBitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BatchProcessingScreen(
    navController: NavController,
    processedImages: MutableList<ProcessedImage>,
    storageManager: StorageManager
) {
    val context = LocalContext.current
    var selectedImages by remember { mutableStateOf<List<Pair<Uri, AndroidBitmap>>>(emptyList()) }
    var isProcessing by remember { mutableStateOf(false) }
    var showColorSettings by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var previewResults by remember { mutableStateOf<List<Pair<AndroidBitmap, Int>>>(emptyList()) }

    // Цветовые параметры с ползунками
    var hueMin by remember { mutableStateOf(250f) }
    var hueMax by remember { mutableStateOf(360f) }
    var saturationMin by remember { mutableStateOf(0.2f) }
    var valueMin by remember { mutableStateOf(0.12f) }
    var includeRed by remember { mutableStateOf(true) }

    val colorParams = remember(hueMin, hueMax, saturationMin, valueMin, includeRed) {
        ImageProcessing.ColorParams(
            hueMin = hueMin,
            hueMax = hueMax,
            saturationMin = saturationMin,
            valueMin = valueMin,
            includeRed = includeRed,
            forceUniformMode = true
        )
    }

    val scope = rememberCoroutineScope()

    // Выбор изображений
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val newImages = mutableListOf<Pair<Uri, AndroidBitmap>>()
                for (uri in uris) {
                    try {
                        val bitmap = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri).use { stream ->
                                BitmapFactory.decodeStream(stream)
                            }
                        }
                        if (bitmap != null) {
                            val scaledBitmap = scaleToMax(bitmap, 1280)
                            newImages.add(uri to scaledBitmap)
                        }
                    } catch (e: Exception) {
                        // Игнорируем ошибки загрузки
                    }
                }
                selectedImages = selectedImages + newImages
            }
        }
    }

            // Функция создания предпросмотра
    fun createPreview() {
        if (selectedImages.isEmpty()) return

        scope.launch {
            isProcessing = true
            try {
                val results = mutableListOf<Pair<AndroidBitmap, Int>>()

                // Обрабатываем только первые 3 изображения для предпросмотра
                val imagesToPreview = selectedImages.take(3)

                for ((_, bitmap) in imagesToPreview) {
                    try {
                        val (annotatedBitmap, count) = withContext(Dispatchers.Default) {
                            ImageProcessing.annotateRedBloodCellsWithParams(bitmap, colorParams)
                        }
                        results.add(annotatedBitmap to count)
                    } catch (e: Exception) {
                        // Игнорируем ошибки обработки отдельных изображений
                        e.printStackTrace()
                    }
                }

                if (results.isNotEmpty()) {
                    previewResults = results
                    showPreview = true
                }
            } catch (e: Exception) {
                // Обработка общих ошибок
                e.printStackTrace()
            } finally {
                isProcessing = false
            }
        }
    }

    // Функция массовой обработки
    fun processAllImages() {
        if (selectedImages.isEmpty()) return

        scope.launch {
            isProcessing = true
            try {
                val results = mutableListOf<ProcessedImage>()

                                for ((uri, bitmap) in selectedImages) {
                    try {
                        val (annotatedBitmap, count) = withContext(Dispatchers.Default) {
                            ImageProcessing.annotateRedBloodCellsWithParams(bitmap, colorParams)
                        }

                        val processedImage = ProcessedImage(
                            uri = uri,
                            originalBitmap = bitmap,
                            annotatedBitmap = annotatedBitmap,
                            cellCount = count,
                            colorParams = colorParams
                        )
                        results.add(processedImage)
                    } catch (e: Exception) {
                        // Игнорируем ошибки обработки отдельных изображений
                        e.printStackTrace()
                    }
                }

                                // Добавляем все результаты в галерею
                processedImages.addAll(0, results)

                // Сохраняем все в постоянную память
                for (result in results) {
                    storageManager.saveProcessedImage(result, processedImages.toList())
                }

                // Возвращаемся в галерею
                navController.navigate("gallery") {
                    popUpTo("batch") { inclusive = true }
                }
            } finally {
                isProcessing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Массовая обработка") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (selectedImages.isNotEmpty()) {
                        TextButton(onClick = { showColorSettings = !showColorSettings }) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Настройки")
                        }
                        TextButton(onClick = { createPreview() }) {
                            Icon(Icons.Default.Preview, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Предпросмотр")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Кнопка выбора изображений
            OutlinedButton(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Выбрать изображения")
            }

            // Информация о выбранных изображениях
            if (selectedImages.isNotEmpty()) {
                Text(
                    text = "Выбрано изображений: ${selectedImages.size}",
                    style = MaterialTheme.typography.titleMedium
                )

                // Список выбранных изображений
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedImages.size) { index ->
                        val (uri, bitmap) = selectedImages[index]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Изображение ${index + 1}",
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                selectedImages = selectedImages.toMutableList().apply {
                                    removeAt(index)
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Удалить")
                            }
                        }
                    }
                }
            }

            // Настройки цветов
            if (showColorSettings && selectedImages.isNotEmpty()) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Настройки детекции цветов",
                            style = MaterialTheme.typography.titleMedium
                        )

                        // Отладочная информация
                        Text(
                            text = "Текущие параметры: H${hueMin.toInt()}-${hueMax.toInt()}°, S${(saturationMin*100).toInt()}%, V${(valueMin*100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        // Цветовой индикатор
                        ColorRangeIndicator(
                            hueMin = hueMin,
                            hueMax = hueMax,
                            saturationMin = saturationMin,
                            valueMin = valueMin,
                            includeRed = includeRed,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Оттенок (Hue)
                        Column {
                            Text("Диапазон оттенков", style = MaterialTheme.typography.bodyMedium)
                            Text("${hueMin.toInt()}° - ${hueMax.toInt()}° (фиолетовый-пурпурный)",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.outline)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Slider(
                                    value = hueMin,
                                    onValueChange = {
                                        hueMin = it
                                        if (hueMin > hueMax) hueMax = hueMin
                                    },
                                    onValueChangeFinished = {
                                        if (showPreview) createPreview()
                                    },
                                    valueRange = 0f..360f,
                                    modifier = Modifier.weight(1f)
                                )
                                Slider(
                                    value = hueMax,
                                    onValueChange = {
                                        hueMax = it
                                        if (hueMax < hueMin) hueMin = hueMax
                                    },
                                    onValueChangeFinished = {
                                        if (showPreview) createPreview()
                                    },
                                    valueRange = 0f..360f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Насыщенность (Saturation)
                        Column {
                            Text("Минимальная насыщенность", style = MaterialTheme.typography.bodyMedium)
                            Text("${(saturationMin * 100).toInt()}% (яркость цвета)",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.outline)
                            Slider(
                                value = saturationMin,
                                onValueChange = { saturationMin = it },
                                onValueChangeFinished = {
                                    if (showPreview) createPreview()
                                },
                                valueRange = 0f..1f
                            )
                        }

                        // Яркость (Value)
                        Column {
                            Text("Минимальная яркость", style = MaterialTheme.typography.bodyMedium)
                            Text("${(valueMin * 100).toInt()}% (светлота цвета)",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.outline)
                            Slider(
                                value = valueMin,
                                onValueChange = { valueMin = it },
                                onValueChangeFinished = {
                                    if (showPreview) createPreview()
                                },
                                valueRange = 0f..1f
                            )
                        }

                        // Включение красного диапазона
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Красный диапазон", style = MaterialTheme.typography.bodyMedium)
                                    Text("Включает малиновые оттенки (0-30°)",
                                         style = MaterialTheme.typography.bodySmall,
                                         color = MaterialTheme.colorScheme.outline)
                                }
                                Switch(
                                    checked = includeRed,
                                    onCheckedChange = {
                                        includeRed = it
                                        if (showPreview) createPreview()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Предпросмотр результатов
            if (showPreview && previewResults.isNotEmpty()) {
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Предпросмотр результатов",
                                style = MaterialTheme.typography.titleMedium
                            )
                            TextButton(onClick = { showPreview = false }) {
                                Text("Скрыть")
                            }
                        }

                        Text(
                            text = "Показаны первые ${previewResults.size} изображения из ${selectedImages.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        // Список предпросмотра
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            previewResults.forEachIndexed { index, (annotatedBitmap, count) ->
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Изображение ${index + 1}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Найдено: $count эритроцитов",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Image(
                                        bitmap = annotatedBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Кнопки действий
            if (selectedImages.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { createPreview() },
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Обработка...")
                        } else {
                            Icon(Icons.Default.Preview, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Предпросмотр")
                        }
                    }

                    Button(
                        onClick = { processAllImages() },
                        modifier = Modifier.weight(1f),
                        enabled = !isProcessing
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Обработка...")
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Обработать все")
                        }
                    }
                }
            }
        }
    }
}

private fun scaleToMax(src: AndroidBitmap, maxSide: Int): AndroidBitmap {
    val w = src.width
    val h = src.height
    val scale = if (w >= h) maxSide.toFloat() / w else maxSide.toFloat() / h
    return if (scale >= 1f) src else AndroidBitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
}
