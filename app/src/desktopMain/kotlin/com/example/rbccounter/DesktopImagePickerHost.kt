package com.example.rbccounter

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

class DesktopImagePickerHost(private val parent: Frame?) : ImagePickerHost {
    override fun pickSingleImage(callback: (PlatformImage?) -> Unit) {
        SwingUtilities.invokeLater {
            val dialog = FileDialog(parent, "Выберите изображение", FileDialog.LOAD).apply {
                setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") } }
            }
            dialog.isVisible = true
            val file = dialog.directory?.let { d -> dialog.file?.let { f -> File(d, f) } }
            val image = file?.takeIf { it.exists() }?.readBytes()?.let { decodePlatformImage(it) }
            callback(image)
        }
    }

    override fun pickMultipleImages(callback: (List<PlatformImage>) -> Unit) {
        SwingUtilities.invokeLater {
            val dialog = FileDialog(parent, "Выберите изображения (JPG, PNG)", FileDialog.LOAD).apply {
                setMultipleMode(true)
                setFilenameFilter { _, name ->
                    name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") }
                }
            }
            dialog.isVisible = true
            val files = (dialog.getFiles() ?: emptyArray()).toList()
            val images = files.mapNotNull { f -> f.takeIf { it.exists() }?.readBytes()?.let { decodePlatformImage(it) } }
            callback(images)
        }
    }
}
