package com.example.rbccounter.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.rbccounter.DesktopImagePickerHost
import com.example.rbccounter.DesktopStorageManager
import com.example.rbccounter.RbcCounterApp
import com.example.rbccounter.PlatformImage
import com.example.rbccounter.decodePlatformImage
import java.awt.Window as AwtWindow
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import javax.swing.SwingUtilities

fun main() = application {
    val storageManager = DesktopStorageManager(File(System.getProperty("user.home"), ".rbccounter"))
    val imagePickerHost = DesktopImagePickerHost(null)
    var dropCallback by remember { mutableStateOf<((List<PlatformImage>) -> Unit)?>(null) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "BIMBO RBCcounter",
        state = rememberWindowState(width = 1000.dp, height = 720.dp)
    ) {
        DisposableEffect(dropCallback) {
            val window: AwtWindow? = AwtWindow.getWindows().filter { it.isVisible }.firstOrNull()
            val callback = dropCallback
            if (window != null && callback != null) {
                window.dropTarget = DropTarget(
                    window,
                    DnDConstants.ACTION_COPY,
                    object : DropTargetAdapter() {
                        override fun drop(dtde: DropTargetDropEvent) {
                            try {
                                dtde.acceptDrop(DnDConstants.ACTION_COPY)
                                @Suppress("UNCHECKED_CAST")
                                val fileList = dtde.transferable.getTransferData(
                                    java.awt.datatransfer.DataFlavor.javaFileListFlavor
                                ) as? List<File> ?: emptyList()
                                val images = fileList
                                    .filter { f -> f.isFile && f.name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") } }
                                    .mapNotNull { f -> f.readBytes().let { decodePlatformImage(it) } }
                                SwingUtilities.invokeLater { callback(images) }
                                dtde.dropComplete(true)
                            } catch (e: Exception) {
                                dtde.dropComplete(false)
                            }
                        }
                    }
                )
            } else {
                window?.dropTarget = null
            }
            onDispose {
                window?.dropTarget = null
            }
        }

        RbcCounterApp(
            storageManager = storageManager,
            imagePickerHost = imagePickerHost,
            setDropCallback = { callback -> dropCallback = callback }
        )
    }
}
