package com.example.rbccounter

import android.graphics.Bitmap as AndroidBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EditImageScreen(
    navController: NavController,
    processedImage: ProcessedImage,
    processedImages: MutableList<ProcessedImage>,
    storageManager: StorageManager
) {
    var isProcessing by remember { mutableStateOf(false) }
    var currentAnnotatedBitmap by remember { mutableStateOf(processedImage.annotatedBitmap) }
    var currentCount by remember { mutableStateOf(processedImage.cellCount) }

    // Цветовые параметры с ползунками
    var hueMin by remember { mutableStateOf(processedImage.colorParams.hueMin) }
    var hueMax by remember { mutableStateOf(processedImage.colorParams.hueMax) }
    var saturationMin by remember { mutableStateOf(processedImage.colorParams.saturationMin) }
    var valueMin by remember { mutableStateOf(processedImage.colorParams.valueMin) }
    var includeRed by remember { mutableStateOf(processedImage.colorParams.includeRed) }

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

    // Функция пересчета
    fun recalculateImage() {
        scope.launch {
            isProcessing = true
            try {
                val (annotatedBitmap, count) = withContext(Dispatchers.Default) {
                    ImageProcessing.annotateRedBloodCellsWithParams(processedImage.originalBitmap, colorParams)
                }
                currentAnnotatedBitmap = annotatedBitmap
                currentCount = count
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isProcessing = false
            }
        }
    }

        // Функция сохранения изменений
    fun saveChanges() {
        val updatedProcessedImage = processedImage.copy(
            annotatedBitmap = currentAnnotatedBitmap,
            cellCount = currentCount,
            colorParams = colorParams
        )

        val index = processedImages.indexOfFirst { it.id == processedImage.id }
        if (index != -1) {
            processedImages[index] = updatedProcessedImage

            // Сохраняем в постоянную память
            scope.launch {
                storageManager.updateProcessedImage(updatedProcessedImage, processedImages.toList())
            }
        }

        navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактирование изображения") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        IconButton(onClick = { recalculateImage() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Пересчитать")
                        }
                        IconButton(onClick = { saveChanges() }) {
                            Icon(Icons.Default.Check, contentDescription = "Сохранить")
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
            // Информация о текущем результате
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Найдено эритроцитов: $currentCount",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = "ID: ${processedImage.id.take(8)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Обработанное изображение
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Результат обработки",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Image(
                        bitmap = currentAnnotatedBitmap.asImageBitmap(),
                        contentDescription = "Обработанное изображение",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    )
                }
            }

            // Настройки цветов
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
                                onValueChangeFinished = { recalculateImage() },
                                valueRange = 0f..360f,
                                modifier = Modifier.weight(1f)
                            )
                            Slider(
                                value = hueMax,
                                onValueChange = {
                                    hueMax = it
                                    if (hueMax < hueMin) hueMin = hueMax
                                },
                                onValueChangeFinished = { recalculateImage() },
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
                            onValueChangeFinished = { recalculateImage() },
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
                            onValueChangeFinished = { recalculateImage() },
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
                                    recalculateImage()
                                }
                            )
                        }
                    }
                }
            }

            // Кнопки действий
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { recalculateImage() },
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
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Пересчитать")
                    }
                }

                Button(
                    onClick = { saveChanges() },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Сохранить")
                }
            }
        }
    }
}
