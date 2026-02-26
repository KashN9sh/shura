package com.example.rbccounter

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Отрисовка платформенного изображения в Compose (actual: Android asImageBitmap, Desktop ImageBitmap из BufferedImage).
 */
@Composable
expect fun PlatformImageContent(
    image: PlatformImage?,
    contentDescription: String?,
    modifier: Modifier
)
