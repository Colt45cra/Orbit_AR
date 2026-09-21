package com.orbitar.nativeapp

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun TriggerCropDialog(source: Bitmap, onDismiss: () -> Unit, onApply: (Bitmap, Float) -> Unit) {
    var left by remember { mutableFloatStateOf(0f) }
    var top by remember { mutableFloatStateOf(0f) }
    var right by remember { mutableFloatStateOf(0f) }
    var bottom by remember { mutableFloatStateOf(0f) }
    // Preview uses a small bitmap; the full resolution crop is only allocated on Apply.
    val previewSource = remember(source) {
        val scale = minOf(1f, 480f / maxOf(source.width, source.height))
        Bitmap.createScaledBitmap(source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true)
    }
    val bounds = triggerCrop(source.width, source.height, left, top, right, bottom)
    val preview = remember(previewSource, left, top, right, bottom) {
        val b = triggerCrop(previewSource.width, previewSource.height, left, top, right, bottom)
        Bitmap.createBitmap(previewSource, b.left, b.top, b.width, b.height)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trim trigger margins") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(preview.asImageBitmap(), "Cropped trigger preview", Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Fit)
                Text("Remove blank surroundings. Keep distinctive details and the whole artwork. This changes the trigger used in placement.")
                CropSlider("Top", top) { top = it }
                CropSlider("Bottom", bottom) { bottom = it }
                CropSlider("Left", left) { left = it }
                CropSlider("Right", right) { right = it }
                Text("After applying, measure the physical width of this cropped area. Cropping cannot add detail to repeating stripes.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(Bitmap.createBitmap(source, bounds.left, bounds.top, bounds.width, bounds.height), bounds.width.toFloat() / source.width)
            }, modifier = Modifier.testTag("apply-trigger-crop")) { Text("Apply crop") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CropSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Text("$label · ${(value * 100).toInt()}%")
    Slider(value, onChange, valueRange = 0f..0.45f, modifier = Modifier.testTag("crop-${label.lowercase()}"))
}
