package com.example.rbccounter

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

class DesktopImagePickerHost(private val parent: Frame?) : ImagePickerHost {
    override fun pickSingleImage(callback: (PlatformImage?) -> Unit) {
        SwingUtilities.invokeLater {
            val dialog = FileDialog(parent, "Выберите изображение", FileDialog.LOAD)
            dialog.setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") } }
            dialog.isVisible = true
            val file = dialog.directory?.let { d -> dialog.file?.let { f -> File(d, f) } }
            val image = file?.takeIf { it.exists() }?.readBytes()?.let { decodePlatformImage(it) }
            callback(image)
        }
    }

    override fun pickMultipleImages(callback: (List<PlatformImage>) -> Unit) {
        SwingUtilities.invokeLater {
            val chooser = JFileChooser().apply {
                isMultiSelectionEnabled = true
                fileSelectionMode = JFileChooser.FILES_ONLY
                fileFilter = FileNameExtensionFilter("Изображения (JPG, PNG)", "jpg", "jpeg", "png")
            }
            if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
                val files = chooser.selectedFiles?.toList().orEmpty()
                val images = files.mapNotNull { it.takeIf { f -> f.exists() }?.readBytes()?.let { decodePlatformImage(it) } }
                callback(images)
            } else {
                callback(emptyList())
            }
        }
    }
}
