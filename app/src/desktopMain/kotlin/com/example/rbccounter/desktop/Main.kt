package com.example.rbccounter.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.rbccounter.DesktopImagePickerHost
import com.example.rbccounter.DesktopStorageManager
import com.example.rbccounter.RbcCounterApp
import java.io.File

fun main() = application {
    val storageManager = DesktopStorageManager(File(System.getProperty("user.home"), ".rbccounter"))
    val imagePickerHost = DesktopImagePickerHost(null)
    Window(
        onCloseRequest = ::exitApplication,
        title = "RBC Counter",
        state = rememberWindowState(width = 800.dp, height = 600.dp)
    ) {
        RbcCounterApp(storageManager = storageManager, imagePickerHost = imagePickerHost)
    }
}
