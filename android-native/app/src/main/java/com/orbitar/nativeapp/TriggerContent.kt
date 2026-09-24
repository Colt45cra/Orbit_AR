package com.orbitar.nativeapp

import android.graphics.Bitmap
import androidx.compose.runtime.*
import io.github.sceneview.SceneScope
import io.github.sceneview.math.*
import io.github.sceneview.node.ImageNode as ImageNodeImpl

/** Fixed geometry with reactive transforms: width changes resize, without re-uploading the texture. */
@Suppress("RestrictedApi")
@Composable
internal fun SceneScope.TriggerContent(bitmap:Bitmap,width:Float,tilt:Float,offsetX:Float,offsetZ:Float,heightOffset:Float = 0f) {
    val aspect=bitmap.width.toFloat()/bitmap.height.coerceAtLeast(1)
    val height=width/aspect
    val center=placementCenter(height,tilt,offsetX,offsetZ)
    Node(position=Position(center.x,center.y + heightOffset,center.z),rotation=Rotation(x=tilt-90f),scale=Scale(width)) {
        val node=remember(engine,materialLoader,bitmap) {
            ImageNodeImpl(materialLoader,bitmap,size=Size(1f,1f/aspect)).apply {isEditable=false}
        }
        NodeLifecycle(node,null)
    }
}
