package com.example.rbccounter

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            val storageManager = remember { AndroidStorageManager(context) }
            var singleCallback by remember { mutableStateOf<((PlatformImage?) -> Unit)?>(null) }
            var multipleCallback by remember { mutableStateOf<((List<PlatformImage>) -> Unit)?>(null) }
            val singleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                scope.launch {
                    val image = uri?.let { u ->
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(u)?.use { it.readBytes() }?.let { decodePlatformImage(it) }
                        }
                    }
                    singleCallback?.invoke(image)
                    singleCallback = null
                }
            }
            val multipleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
                scope.launch {
                    val images = uris.mapNotNull { u ->
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(u)?.use { it.readBytes() }?.let { decodePlatformImage(it) }
                        }
                    }
                    multipleCallback?.invoke(images)
                    multipleCallback = null
                }
            }
            val host = remember {
                object : ImagePickerHost {
                    override fun pickSingleImage(callback: (PlatformImage?) -> Unit) {
                        singleCallback = callback
                        singleLauncher.launch("image/*")
                    }
                    override fun pickMultipleImages(callback: (List<PlatformImage>) -> Unit) {
                        multipleCallback = callback
                        multipleLauncher.launch("image/*")
                    }
                }
            }
            RbcCounterApp(storageManager = storageManager, imagePickerHost = host)
        }
    }
}
