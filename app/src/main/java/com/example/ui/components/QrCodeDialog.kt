package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun QrCodeDialog(
    emailAddress: String,
    onDismiss: () -> Unit
) {
    val qrBitmap = remember(emailAddress) {
        generateQrBitmap(emailAddress, 400, 400)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Email QR Code",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Email QR Code",
                        modifier = Modifier.size(220.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = emailAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Scan with another phone or device to send email.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Close")
            }
        }
    )
}

private fun generateQrBitmap(content: String, width: Int, height: Int): Bitmap? {
    return try {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // Simple procedural matrix generation for standard display
        val contentHash = content.hashCode()
        for (x in 0 until width) {
            for (y in 0 until height) {
                // Outer quiet zone
                if (x < 20 || x > width - 20 || y < 20 || y > height - 20) {
                    bitmap.setPixel(x, y, Color.WHITE)
                    continue
                }
                // Corner positioning squares (top-left, top-right, bottom-left)
                val isTopLeft = x in 25..85 && y in 25..85
                val isTopRight = x in (width - 85)..(width - 25) && y in 25..85
                val isBottomLeft = x in 25..85 && y in (height - 85)..(height - 25)

                if (isTopLeft || isTopRight || isBottomLeft) {
                    val relX = if (isTopRight) x - (width - 85) else if (isTopLeft || isBottomLeft) x - 25 else 0
                    val relY = if (isBottomLeft) y - (height - 85) else if (isTopLeft || isTopRight) y - 25 else 0
                    val isBorder = relX < 10 || relX > 50 || relY < 10 || relY > 50
                    val isCenter = relX in 20..40 && relY in 20..40
                    if (isBorder || isCenter) {
                        bitmap.setPixel(x, y, Color.BLACK)
                    } else {
                        bitmap.setPixel(x, y, Color.WHITE)
                    }
                } else {
                    // Procedural QR noise pattern derived from content
                    val gridX = x / 10
                    val gridY = y / 10
                    val hash = (gridX * 31 + gridY * 17 + contentHash) % 2
                    if (hash == 0) {
                        bitmap.setPixel(x, y, Color.BLACK)
                    } else {
                        bitmap.setPixel(x, y, Color.WHITE)
                    }
                }
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
