package com.xxx.carelorie.ui.components.food

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Camera or gallery.
 *
 * Asked rather than assumed: reviewing a meal you photographed earlier is as common as
 * photographing the one in front of you.
 */
@Composable
fun PhotoSourceDialog(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan food") },
        text = {
            Text(
                "Take a photo of your meal and the AI will identify what is on the plate and " +
                    "estimate the portion.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onCamera() }) { Text("Take photo") }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss(); onGallery() }) { Text("Choose photo") }
        }
    )
}
