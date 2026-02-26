package com.example.rbccounter

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

@Composable
actual fun PlatformImageContent(
    image: PlatformImage?,
    contentDescription: String?,
    modifier: Modifier
) {
    if (image != null) {
        Image(
            bitmap = image.getBitmap().asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    }
}
