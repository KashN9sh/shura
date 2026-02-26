package com.example.rbccounter

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.skiko.toBitmap

@Composable
actual fun PlatformImageContent(
    image: PlatformImage?,
    contentDescription: String?,
    modifier: Modifier
) {
    if (image != null) {
        val bitmap = image.getBufferedImage().toBitmap().asComposeImageBitmap()
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}
