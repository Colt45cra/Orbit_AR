package com.orbitar.nativeapp

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.*

internal data class PlacementPoint(val x: Float, val y: Float, val z: Float)
internal fun placementCenter(height: Float, tilt: Float, x: Float, z: Float): PlacementPoint {
    val radians = Math.toRadians(tilt.toDouble())
    return PlacementPoint(x, 0.001f + height / 2f * sin(radians).toFloat(), z - height / 2f * cos(radians).toFloat())
}
internal fun popupCorners(width: Float, height: Float, tilt: Float, x: Float, z: Float): List<PlacementPoint> {
    val c = placementCenter(height, tilt, x, z)
    val r = Math.toRadians(tilt.toDouble())
    val y = height / 2f * sin(r).toFloat()
    val depth = height / 2f * cos(r).toFloat()
    return listOf(PlacementPoint(x-width/2, c.y+y, c.z-depth), PlacementPoint(x+width/2, c.y+y, c.z-depth),
        PlacementPoint(x+width/2, c.y-y, c.z+depth), PlacementPoint(x-width/2, c.y-y, c.z+depth))
}

@Composable
internal fun PlacementPreview(trigger: Bitmap, popup: Bitmap, targetWidth: Float, popupWidth: Float,
    tilt: Float, offsetX: Float, offsetZ: Float, onMove: (Float, Float) -> Unit) {
    var moveMode by rememberSaveable { mutableStateOf(false) }
    var topView by rememberSaveable { mutableStateOf(false) }
    val currentX by rememberUpdatedState(offsetX)
    val currentZ by rememberUpdatedState(offsetZ)
    val currentMove by rememberUpdatedState(onMove)
    var pixelsPerMeter by remember { mutableFloatStateOf(1f) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Place your AR image", style = MaterialTheme.typography.titleLarge)
        Text("Green: trigger · Blue: AR image · Dot: bottom attachment point", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !topView, onClick = { topView = false; moveMode = false }, label = { Text("3D view") })
            FilterChip(selected = topView, onClick = { topView = true; moveMode = false }, label = { Text("Top view") })
        }
        // Scrolling owns every gesture by default. Dragging is enabled only explicitly.
        Canvas(Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF1B211F))
            .testTag("placement-preview").semantics { contentDescription = "Trigger and AR image placement preview" }
            .pointerInput(moveMode, topView) {
                if (moveMode) detectDragGestures { change, delta ->
                    change.consume()
                    currentMove((currentX + delta.x / pixelsPerMeter).coerceIn(-0.6f, 0.6f),
                        (currentZ + delta.y / pixelsPerMeter).coerceIn(-0.6f, 0.6f))
                }
            }) {
            val targetHeight = targetWidth * trigger.height / trigger.width
            val popupHeight = popupWidth * popup.height / popup.width
            val triggerPoints = listOf(PlacementPoint(-targetWidth/2, 0f, -targetHeight/2), PlacementPoint(targetWidth/2, 0f, -targetHeight/2),
                PlacementPoint(targetWidth/2, 0f, targetHeight/2), PlacementPoint(-targetWidth/2, 0f, targetHeight/2))
            val popupPoints = popupCorners(popupWidth, popupHeight, tilt, offsetX, offsetZ)
            fun project(p: PlacementPoint): Pair<Float, Float> = if (topView) p.x to p.z else (p.x + p.z * 0.25f) to (p.z * 0.5f - p.y * 0.866f)
            // Bounds depend on image sizes, not position: dragging does not move the camera.
            val span = maxOf(targetWidth, targetHeight, popupWidth, popupHeight, 0.12f) * 2.6f
            val scale = minOf(size.width, size.height) / span
            pixelsPerMeter = scale
            fun screen(points: List<PlacementPoint>): FloatArray = points.flatMap { p ->
                val (x,y) = project(p); listOf(size.width/2 + x*scale, size.height*0.60f + y*scale)
            }.toFloatArray()
            val canvas = drawContext.canvas.nativeCanvas
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            fun image(bitmap: Bitmap, corners: FloatArray, color: Int, opacity: Int) {
                val source = floatArrayOf(0f,0f,bitmap.width.toFloat(),0f,bitmap.width.toFloat(),bitmap.height.toFloat(),0f,bitmap.height.toFloat())
                val matrix = Matrix()
                paint.alpha = opacity
                if (matrix.setPolyToPoly(source,0,corners,0,4)) canvas.drawBitmap(bitmap,matrix,paint)
                paint.alpha = 255; paint.color = color; paint.strokeWidth = 3f
                for (i in 0..3) { val next=(i+1)%4; canvas.drawLine(corners[i*2],corners[i*2+1],corners[next*2],corners[next*2+1],paint) }
            }
            image(trigger,screen(triggerPoints),android.graphics.Color.rgb(184,255,61),210)
            image(popup,screen(popupPoints),android.graphics.Color.rgb(85,195,255),235)
            val bottom = screen(listOf(PlacementPoint(offsetX,0.001f,offsetZ)))
            paint.color=android.graphics.Color.rgb(85,195,255); canvas.drawCircle(bottom[0],bottom[1],7f,paint)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { moveMode = !moveMode; if (moveMode) topView = true }, modifier = Modifier.weight(1f)) {
                Text(if (moveMode) "Done moving" else "Move image")
            }
            TextButton(onClick = { onMove(0f,0f) }, modifier = Modifier.weight(1f)) { Text("Center") }
        }
        Text(if (moveMode) "Drag in the preview to move the blue attachment point. Tap Done moving to scroll here."
            else if (topView && tilt > 85f) "Upright images look like a line from above. Use 3D view to see the image. Swipe up to reach all controls."
            else "Swipe up to reach size, tilt and position controls. Tap Move image to drag its attachment point.", style = MaterialTheme.typography.bodySmall)
    }
}
