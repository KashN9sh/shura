package com.example.rbccounter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.rbccounter.ui.theme.RbcTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val COUNT_PREFIX = "Эритроцитов: "

@Composable
fun RbcCounterApp(
    storageManager: StorageManager,
    imagePickerHost: ImagePickerHost? = null
) {
    RbcTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val processedImages = remember { mutableStateListOf<ProcessedImage>() }
            var currentScreen by remember { mutableStateOf("main") }
            var backStack by remember { mutableStateOf<List<String>>(emptyList()) }

            LaunchedEffect(Unit) {
                val list = storageManager.loadProcessedImages()
                processedImages.clear()
                processedImages.addAll(list)
            }

            when (currentScreen) {
                "main" -> RbcCounterScreen(
                    storageManager = storageManager,
                    imagePickerHost = imagePickerHost,
                    processedImages = processedImages,
                    onNavigateToGallery = { currentScreen = "gallery"; backStack = backStack + "main" },
                    onNavigateToBatch = { currentScreen = "batch"; backStack = backStack + "main" }
                )
                "batch" -> BatchPlaceholderScreen(
                    onBack = { currentScreen = backStack.last(); backStack = backStack.dropLast(1) },
                    imagePickerHost = imagePickerHost,
                    storageManager = storageManager,
                    processedImages = processedImages
                )
                "gallery" -> GalleryScreen(
                    processedImages = processedImages,
                    storageManager = storageManager,
                    onBack = { currentScreen = backStack.last(); backStack = backStack.dropLast(1) },
                    onEdit = { id -> currentScreen = "edit/$id"; backStack = backStack + "gallery" }
                )
                else -> {
                    if (currentScreen.startsWith("edit/")) {
                        val id = currentScreen.removePrefix("edit/")
                        val item = processedImages.find { it.id == id }
                        if (item != null) {
                            EditImageScreen(
                                processedImage = item,
                                processedImages = processedImages,
                                storageManager = storageManager,
                                onBack = { currentScreen = backStack.last(); backStack = backStack.dropLast(1) }
                            )
                        } else {
                            currentScreen = backStack.last()
                            backStack = backStack.dropLast(1)
                        }
                    } else {
                        currentScreen = "main"
                        backStack = emptyList()
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RbcCounterScreen(
    storageManager: StorageManager,
    imagePickerHost: ImagePickerHost?,
    processedImages: MutableList<ProcessedImage>,
    onNavigateToGallery: () -> Unit,
    onNavigateToBatch: () -> Unit
) {
    var image by remember { mutableStateOf<PlatformImage?>(null) }
    var annotated by remember { mutableStateOf<PlatformImage?>(null) }
    var count by remember { mutableStateOf<Int?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var imageSourceId by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var hueMin by remember { mutableStateOf(250f) }
    var hueMax by remember { mutableStateOf(360f) }
    var saturationMin by remember { mutableStateOf(0.2f) }
    var valueMin by remember { mutableStateOf(0.12f) }
    var includeRed by remember { mutableStateOf(true) }
    var roiVThreshold by remember { mutableStateOf(0.6f) }
    var roiMarginFraction by remember { mutableStateOf(0.04f) }
    var showRoiPreview by remember { mutableStateOf(false) }
    var roiPreview by remember { mutableStateOf<PlatformImage?>(null) }
    val colorParams = remember(hueMin, hueMax, saturationMin, valueMin, includeRed, roiVThreshold, roiMarginFraction) {
        ImageProcessing.ColorParams(hueMin = hueMin, hueMax = hueMax, saturationMin = saturationMin, valueMin = valueMin, includeRed = includeRed, forceUniformMode = true, roiVThreshold = roiVThreshold, roiMarginFraction = roiMarginFraction)
    }

    fun processImage(input: PlatformImage) {
        scope.launch {
            isProcessing = true
            try {
                val scaled = scaleToMax(input, 1280)
                val (ann, n) = withContext(Dispatchers.Default) {
                    ImageProcessing.annotateRedBloodCellsWithParams(scaled, colorParams)
                }
                annotated = ann
                count = n
            } finally {
                isProcessing = false
            }
        }
    }

    LaunchedEffect(hueMin, hueMax, saturationMin, valueMin, includeRed, roiVThreshold, roiMarginFraction) {
        val img = image ?: return@LaunchedEffect
        delay(400)
        processImage(img)
    }

    LaunchedEffect(image, showRoiPreview, hueMin, hueMax, saturationMin, valueMin, includeRed, roiVThreshold, roiMarginFraction) {
        if (!showRoiPreview || image == null) {
            roiPreview = null
            return@LaunchedEffect
        }
        roiPreview = withContext(Dispatchers.Default) {
            ImageProcessing.visualizeRoi(image!!, colorParams)
        }
    }

    fun showDebugMask() {
        val img = image ?: return
        scope.launch {
            isProcessing = true
            try {
                annotated = withContext(Dispatchers.Default) {
                    ImageProcessing.debugColorMaskWithParams(img, colorParams)
                }
                count = null
            } finally {
                isProcessing = false
            }
        }
    }

    fun saveToGallery() {
        if (image != null && annotated != null && count != null) {
            val id = randomId()
            val item = ProcessedImage(
                id = id,
                imageSourceId = imageSourceId.ifEmpty { "saved" },
                cellCount = count!!,
                timestamp = System.currentTimeMillis(),
                colorParams = colorParams
            )
            scope.launch {
                storageManager.saveProcessedImage(item, image!!, annotated!!, processedImages + item)
                processedImages.add(item)
            }
        }
    }

    Scaffold(topBar = {}) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Улучшенный RBC Counter", style = MaterialTheme.typography.titleLarge)
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 480.dp)
                    .clickable(enabled = imagePickerHost != null) {
                        imagePickerHost?.pickSingleImage { picked ->
                            picked?.let {
                                imageSourceId = "picked"
                                image = it
                                count = null
                                annotated = null
                                processImage(it)
                            }
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        showRoiPreview && roiPreview != null -> PlatformImageContent(
                            image = roiPreview,
                            contentDescription = "ROI превью",
                            modifier = Modifier.fillMaxWidth()
                        )
                        annotated != null -> PlatformImageContent(
                            image = annotated,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        else -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Text("Выберите фото", style = MaterialTheme.typography.bodyMedium)
                            if (imagePickerHost != null) {
                                OutlinedButton(onClick = {
                                    imagePickerHost.pickSingleImage { picked ->
                                        picked?.let {
                                            imageSourceId = "picked"
                                            image = it
                                            count = null
                                            annotated = null
                                            processImage(it)
                                        }
                                    }
                                }) { Text("Выбрать фото") }
                            }
                        }
                    }
                }
            }
            Text("Настройка цветового диапазона", style = MaterialTheme.typography.titleSmall)
            ColorRangeIndicator(hueMin, hueMax, saturationMin, valueMin, includeRed, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Hue мин: ${hueMin.toInt()}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                Slider(value = hueMin, onValueChange = { hueMin = it }, valueRange = 0f..360f, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Hue макс: ${hueMax.toInt()}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                Slider(value = hueMax, onValueChange = { hueMax = it }, valueRange = 0f..360f, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Насыщ. мин: ${"%.2f".format(saturationMin)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                Slider(value = saturationMin, onValueChange = { saturationMin = it }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Яркость мин: ${"%.2f".format(valueMin)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                Slider(value = valueMin, onValueChange = { valueMin = it }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Включить красный (0–30°)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(checked = includeRed, onCheckedChange = { includeRed = it })
            }
            Text("Область интереса (ROI)", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Показать ROI на превью", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(checked = showRoiPreview, onCheckedChange = { showRoiPreview = it })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Порог яркости ROI: ${(roiVThreshold * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                Slider(value = roiVThreshold, onValueChange = { roiVThreshold = it }, valueRange = 0.1f..1f, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Отступ ROI: ${(roiMarginFraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                Slider(value = roiMarginFraction, onValueChange = { roiMarginFraction = it }, valueRange = 0.01f..0.2f, modifier = Modifier.weight(1f))
            }
            if (image != null && !isProcessing) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDebugMask() }, modifier = Modifier.weight(1f)) { Text("Отладка маски") }
                }
            }
            if (isProcessing) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isProcessing -> "Обработка..."
                        count != null -> COUNT_PREFIX + count
                        image != null -> "Не удалось обработать"
                        else -> "Готов к работе"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onNavigateToBatch) { Text("Массовая") }
                    OutlinedButton(onClick = onNavigateToGallery) { Text("Галерея") }
                }
            }
            if (count != null) {
                Button(onClick = { saveToGallery() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Сохранить результат")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun BatchPlaceholderScreen(
    onBack: () -> Unit,
    imagePickerHost: ImagePickerHost?,
    storageManager: StorageManager,
    processedImages: MutableList<ProcessedImage>
) {
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Массовая обработка") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Выберите несколько изображений", style = MaterialTheme.typography.bodyLarge)
            imagePickerHost?.let { host ->
                Button(onClick = {
                    host.pickMultipleImages { images ->
                        scope.launch {
                            images.forEach { img ->
                                val scaled = withContext(Dispatchers.Default) { scaleToMax(img, 1280) }
                                val params = ImageProcessing.ColorParams(forceUniformMode = true)
                                val (annotated, n) = withContext(Dispatchers.Default) {
                                    ImageProcessing.annotateRedBloodCellsWithParams(scaled, params)
                                }
                                val item = ProcessedImage(
                                    id = randomId(),
                                    imageSourceId = "batch",
                                    cellCount = n,
                                    timestamp = System.currentTimeMillis(),
                                    colorParams = params
                                )
                                processedImages.add(item)
                                storageManager.saveProcessedImage(item, scaled, annotated, processedImages.toList())
                            }
                        }
                    }
                }) { Text("Выбрать изображения") }
            }
            OutlinedButton(onClick = onBack) { Text("Назад") }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun GalleryScreen(
    processedImages: MutableList<ProcessedImage>,
    storageManager: StorageManager,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Галерея") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        if (processedImages.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, Modifier.padding(16.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("Галерея пуста", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.outline)
                    OutlinedButton(onClick = onBack) { Text("К обработке") }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(processedImages) { item ->
                    var thumb by remember(item.id) { mutableStateOf<PlatformImage?>(null) }
                    LaunchedEffect(item.id) {
                        thumb = storageManager.loadAnnotatedImage(item.id)
                    }
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Эритроцитов: ${item.cellCount}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text("ID: ${item.id.take(8)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(8.dp))
                            PlatformImageContent(
                                image = thumb,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    processedImages.remove(item)
                                    scope.launch { storageManager.deleteProcessedImage(item.id, processedImages.toList()) }
                                }) { Text("Удалить") }
                                Button(onClick = { onEdit(item.id) }) { Text("Редактировать") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EditImageScreen(
    processedImage: ProcessedImage,
    processedImages: MutableList<ProcessedImage>,
    storageManager: StorageManager,
    onBack: () -> Unit
) {
    var currentAnnotated by remember { mutableStateOf<PlatformImage?>(null) }
    var currentCount by remember { mutableStateOf(processedImage.cellCount) }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var hueMin by remember(processedImage.id) { mutableStateOf(processedImage.colorParams.hueMin) }
    var hueMax by remember(processedImage.id) { mutableStateOf(processedImage.colorParams.hueMax) }
    var saturationMin by remember(processedImage.id) { mutableStateOf(processedImage.colorParams.saturationMin) }
    var valueMin by remember(processedImage.id) { mutableStateOf(processedImage.colorParams.valueMin) }
    var includeRed by remember(processedImage.id) { mutableStateOf(processedImage.colorParams.includeRed) }
    var roiVThreshold by remember(processedImage.id) { mutableStateOf(processedImage.colorParams.roiVThreshold) }
    var roiMarginFraction by remember(processedImage.id) { mutableStateOf(processedImage.colorParams.roiMarginFraction) }
    val colorParams = remember(hueMin, hueMax, saturationMin, valueMin, includeRed, roiVThreshold, roiMarginFraction) {
        processedImage.colorParams.copy(hueMin = hueMin, hueMax = hueMax, saturationMin = saturationMin, valueMin = valueMin, includeRed = includeRed, roiVThreshold = roiVThreshold, roiMarginFraction = roiMarginFraction)
    }

    LaunchedEffect(processedImage.id) {
        currentAnnotated = storageManager.loadAnnotatedImage(processedImage.id)
    }

    fun recalculate() {
        scope.launch {
            val original = storageManager.loadOriginalImage(processedImage.id) ?: return@launch
            isProcessing = true
            try {
                val (ann, n) = withContext(Dispatchers.Default) {
                    ImageProcessing.annotateRedBloodCellsWithParams(original, colorParams)
                }
                currentAnnotated = ann
                currentCount = n
            } finally {
                isProcessing = false
            }
        }
    }

    fun save() {
        val ann = currentAnnotated ?: return
        val updated = processedImage.copy(cellCount = currentCount, colorParams = colorParams)
        val idx = processedImages.indexOfFirst { it.id == processedImage.id }
        if (idx >= 0) {
            processedImages[idx] = updated
            scope.launch { storageManager.updateProcessedImage(updated, ann, processedImages) }
        }
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактирование") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Назад") } },
                actions = {
                    if (!isProcessing) {
                        TextButton(onClick = { recalculate() }) { Text("Пересчитать") }
                        TextButton(onClick = { save() }) { Text("Сохранить") }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Найдено эритроцитов: $currentCount", style = MaterialTheme.typography.titleMedium)
            PlatformImageContent(
                image = currentAnnotated,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
            )
            Text("Настройка цветового диапазона", style = MaterialTheme.typography.titleSmall)
            ColorRangeIndicator(hueMin, hueMax, saturationMin, valueMin, includeRed, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Hue мин: ${hueMin.toInt()}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                Slider(value = hueMin, onValueChange = { hueMin = it }, valueRange = 0f..360f, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Hue макс: ${hueMax.toInt()}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                Slider(value = hueMax, onValueChange = { hueMax = it }, valueRange = 0f..360f, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Насыщ. мин: ${"%.2f".format(saturationMin)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                Slider(value = saturationMin, onValueChange = { saturationMin = it }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Яркость мин: ${"%.2f".format(valueMin)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                Slider(value = valueMin, onValueChange = { valueMin = it }, valueRange = 0f..1f, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Включить красный (0–30°)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(checked = includeRed, onCheckedChange = { includeRed = it })
            }
            Text("Область интереса (ROI)", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Порог яркости ROI: ${(roiVThreshold * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                Slider(value = roiVThreshold, onValueChange = { roiVThreshold = it }, valueRange = 0.1f..1f, modifier = Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Отступ ROI: ${(roiMarginFraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                Slider(value = roiMarginFraction, onValueChange = { roiMarginFraction = it }, valueRange = 0.01f..0.2f, modifier = Modifier.weight(1f))
            }
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
        }
    }
}
