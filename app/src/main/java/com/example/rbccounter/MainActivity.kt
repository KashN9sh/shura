package com.example.rbccounter

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap as AndroidBitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.RectF

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            com.example.rbccounter.ui.theme.RbcTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    RbcCounterScreen()
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RbcCounterScreen() {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var count by remember { mutableStateOf<Int?>(null) }
    var annotated by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var showColorSettings by remember { mutableStateOf(false) }

    // Цветовые параметры с ползунками
    var hueMin by remember { mutableStateOf(250f) }
    var hueMax by remember { mutableStateOf(360f) }
    var saturationMin by remember { mutableStateOf(0.2f) }
    var valueMin by remember { mutableStateOf(0.12f) }
        var includeRed by remember { mutableStateOf(true) }
    var forceUniformMode by remember { mutableStateOf(true) }

    val colorParams = remember(hueMin, hueMax, saturationMin, valueMin, includeRed) {
        ImageProcessing.ColorParams(
            hueMin = hueMin,
            hueMax = hueMax,
            saturationMin = saturationMin,
            valueMin = valueMin,
            includeRed = includeRed,
            forceUniformMode = forceUniformMode
        )
    }

    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            imageUri = uri
            count = null
            if (uri != null) {
                scope.launch {
                    isProcessing = true
                    try {
                        val b = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri).use { stream ->
                                BitmapFactory.decodeStream(stream)
                            }
                        }
                        bitmap = b?.let { scaleToMax(it, 1280) }
                        val (img, n) = bitmap?.let { withContext(Dispatchers.Default) {
                            ImageProcessing.annotateRedBloodCellsWithParams(it, colorParams)
                        } } ?: (null to null)
                        annotated = img
                        count = n
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }
    )

    // Альтернативный picker с более широким доступом к альбомам
    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            imageUri = uri
            count = null
            if (uri != null) {
                scope.launch {
                    isProcessing = true
                    try {
                        val b = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri).use { stream ->
                                BitmapFactory.decodeStream(stream)
                            }
                        }
                        bitmap = b?.let { scaleToMax(it, 1280) }
                        val (img, n) = bitmap?.let { withContext(Dispatchers.Default) {
                            ImageProcessing.annotateRedBloodCellsWithParams(it, colorParams)
                        } } ?: (null to null)
                        annotated = img
                        count = n
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }
    )

    // Функция для пересчета с новыми параметрами
    fun recalculateWithParams() {
        if (bitmap != null) {
            scope.launch {
                isProcessing = true
                try {
                    val (img, n) = withContext(Dispatchers.Default) {
                        ImageProcessing.annotateRedBloodCellsWithParams(bitmap!!, colorParams)
                    }
                    annotated = img
                    count = n
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    // Проверка разрешений
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Разрешение получено, можно открыть галерею
            try {
                getContentLauncher.launch("image/*")
            } catch (e: Exception) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }

    // Для Android 14+ может потребоваться запрос нескольких разрешений
    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            // Все разрешения получены, можно открыть галерею
            try {
                getContentLauncher.launch("image/*")
            } catch (e: Exception) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }

        // Функция для проверки разрешений и открытия галереи
    fun openGallery() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                // Android 14+ - запрашиваем все медиа разрешения для полного доступа
                val permissions = arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
                val allGranted = permissions.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                if (allGranted) {
                    // Сначала пробуем GetContent, если не работает - используем PickVisualMedia
                    try {
                        getContentLauncher.launch("image/*")
                    } catch (e: Exception) {
                        // Fallback к PickVisualMedia
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                } else {
                    multiplePermissionsLauncher.launch(permissions)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                // Android 13+ - запрашиваем все медиа разрешения
                val permissions = arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO
                )
                val allGranted = permissions.all {
                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                }
                if (allGranted) {
                    // Сначала пробуем GetContent, если не работает - используем PickVisualMedia
                    try {
                        getContentLauncher.launch("image/*")
                    } catch (e: Exception) {
                        // Fallback к PickVisualMedia
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                } else {
                    multiplePermissionsLauncher.launch(permissions)
                }
            }
            else -> {
                // Android 12 и ниже - используем общее разрешение на чтение внешнего хранилища
                val permission = Manifest.permission.READ_EXTERNAL_STORAGE
                if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                    // Сначала пробуем GetContent, если не работает - используем PickVisualMedia
                    try {
                        getContentLauncher.launch("image/*")
                    } catch (e: Exception) {
                        // Fallback к PickVisualMedia
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                } else {
                    permissionLauncher.launch(permission)
                }
            }
        }
    }

    // Без верхней панели: заголовок уже в контенте
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
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = "Улучшенный RBC Counter",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 480.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable {
                            openGallery()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        annotated != null -> Image(
                            bitmap = annotated!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        else -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null)
                            Text("Перетащите или выберите фото", style = MaterialTheme.typography.bodyMedium)
                            OutlinedButton(onClick = {
                                openGallery()
                            }) { Text("Выбрать фото") }
                        }
                    }
                }
            }

            if (isProcessing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isProcessing -> "Обработка..."
                        count != null -> context.getString(R.string.count_prefix) + count
                        imageUri != null -> "Не удалось обработать"
                        else -> "Готов к работе"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Column(horizontalAlignment = Alignment.End) {
                    OutlinedButton(onClick = {
                        openGallery()
                    }) { Text("Сменить фото") }
                    TextButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Text("Быстрый выбор", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Кнопка настроек цветов
            if (imageUri != null) {
                OutlinedButton(
                    onClick = { showColorSettings = !showColorSettings },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (showColorSettings) "Скрыть настройки" else "Настройки цветов")
                }
            }

            // Панель настроек цветов
            if (showColorSettings && imageUri != null) {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
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
                                        recalculateWithParams()
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
                                        recalculateWithParams()
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
                                    recalculateWithParams()
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
                                    recalculateWithParams()
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
                                Text("Красный диапазон", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = includeRed,
                                    onCheckedChange = {
                                        includeRed = it
                                        recalculateWithParams()
                                    }
                                )
                            }
                            Text("Включает малиновые оттенки (0-30°)",
                                 style = MaterialTheme.typography.bodySmall,
                                 color = MaterialTheme.colorScheme.outline)
                        }



                        // Кнопки управления
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { recalculateWithParams() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Применить")
                            }
                            OutlinedButton(
                                onClick = {
                                    if (bitmap != null) {
                                        scope.launch {
                                            isProcessing = true
                                            try {
                                                val debugImg = withContext(Dispatchers.Default) {
                                                    ImageProcessing.debugColorMaskWithParams(bitmap!!, colorParams)
                                                }
                                                annotated = debugImg
                                                count = null
                                            } finally {
                                                isProcessing = false
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Отладка")
                            }
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

/**
 * Создает визуальный индикатор выбранного цветового диапазона
 */
@Composable
private fun ColorRangeIndicator(
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


