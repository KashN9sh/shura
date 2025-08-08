package com.example.rbccounter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RbcCounterScreen()
                }
            }
        }
    }
}

@Composable
private fun RbcCounterScreen() {
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var count by remember { mutableStateOf<Int?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

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
                        bitmap = b?.let { scaleToMax(it, 1024) }
                        count = bitmap?.let { withContext(Dispatchers.Default) { ImageProcessing.countCells(it) } }
                    } finally {
                        isProcessing = false
                    }
                }
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(onClick = {
            picker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }) {
            Text(text = LocalContext.current.getString(R.string.pick_photo))
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            )
        }

        Text(
            text = when {
                isProcessing -> "Обработка..."
                count != null -> context.getString(R.string.count_prefix) + count
                imageUri != null -> "Не удалось обработать изображение"
                else -> ""
            }
        )
    }
}

private fun scaleToMax(src: Bitmap, maxSide: Int): Bitmap {
    val w = src.width
    val h = src.height
    val scale = if (w >= h) maxSide.toFloat() / w else maxSide.toFloat() / h
    return if (scale >= 1f) src else Bitmap.createScaledBitmap(src, (w * scale).toInt(), (h * scale).toInt(), true)
}


