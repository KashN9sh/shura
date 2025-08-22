package com.example.rbccounter

import android.net.Uri
import android.graphics.Bitmap as AndroidBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

/**
 * Модель данных для результата обработки изображения
 */
data class ProcessedImage(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val originalBitmap: AndroidBitmap,
    val annotatedBitmap: AndroidBitmap,
    val cellCount: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val colorParams: ImageProcessing.ColorParams
)

@Composable
fun ProcessedImageCard(
    processedImage: ProcessedImage,
    dateFormatter: SimpleDateFormat,
    onDelete: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Заголовок с информацией
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Эритроцитов: ${processedImage.cellCount}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = dateFormatter.format(Date(processedImage.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Параметры обработки
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "H: ${processedImage.colorParams.hueMin.toInt()}-${processedImage.colorParams.hueMax.toInt()}°",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "S: ${(processedImage.colorParams.saturationMin * 100).toInt()}%, V: ${(processedImage.colorParams.valueMin * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Обработанное изображение
            Image(
                bitmap = processedImage.annotatedBitmap.asImageBitmap(),
                contentDescription = "Обработанное изображение",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            )

            // Действия
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ID: ${processedImage.id.take(8)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                TextButton(onClick = onDelete) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
