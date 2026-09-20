package com.orbitar.nativeapp.room

import android.graphics.Bitmap
import androidx.compose.runtime.*
import io.github.sceneview.SceneScope
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.*
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.model.model
import io.github.sceneview.node.ImageNode as ImageNodeImpl
import kotlinx.coroutines.CancellationException

@Composable
internal fun SceneScope.RoomObjectContent(placement: RoomPlacement, onModelState: (Long, String?) -> Unit) {
    // Geometry and GLB normalization are constructed ONCE. The parent transform scales
    // all axes around a bottom origin; elevation is a separate, unscaled translation.
    Node(position = Position(y = placement.elevation + 0.002f), rotation = Rotation(y = placement.rotationY),
        scale = Scale(placement.scale), apply = { name="room-${placement.id}"; isEditable=false }) {
        when(placement.asset.type) {
            RoomAssetType.IMAGE -> placement.asset.bitmap?.let { bitmap ->
                Node(rotation = Rotation(x = if(placement.flat) -90f else 0f)) { FixedRoomImage(bitmap,placement.id) }
            }
            RoomAssetType.MODEL_GLB -> placement.asset.modelUri?.let { uri ->
                val instance = roomModel(modelLoader,uri,placement.id,onModelState)
                instance?.let {
                    ModelNode(modelInstance=it,autoAnimate=true,scaleToUnits=MODEL_BASE_SIZE,
                        centerOrigin=Position(0f,-1f,0f),isEditable=false,
                        apply={ name="room-${placement.id}"; isTouchable=true })
                }
            }
        }
    }
}

@Suppress("RestrictedApi")
@Composable
private fun SceneScope.FixedRoomImage(bitmap: Bitmap, id: Long) {
    val height=IMAGE_BASE_WIDTH*bitmap.height/bitmap.width
    // The library's ImageNode composable re-uploads the texture on every recomposition.
    // A room scan refresh must never re-upload every object's bitmap.
    val node=remember(engine,materialLoader,bitmap) {
        ImageNodeImpl(materialLoader,bitmap,size=Size(IMAGE_BASE_WIDTH,height),center=Position(y=height/2)).apply {
            name="room-$id";isTouchable=true;isEditable=false
        }
    }
    NodeLifecycle(node,null)
}

@Composable
private fun roomModel(loader: ModelLoader, uri: String, id: Long, onState: (Long,String?)->Unit): ModelInstance? {
    val callback by rememberUpdatedState(onState)
    val instance by produceState<ModelInstance?>(null,loader,uri) {
        callback(id,"Loading model…")
        try {
            value=loader.loadModelInstance(uri)
            callback(id,if(value==null) "Couldn't load this GLB. Choose another file." else null)
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { callback(id,"Couldn't load this GLB: ${e.message?.take(100) ?: "invalid file"}") }
    }
    DisposableEffect(instance) { onDispose { instance?.let { loader.destroyModel(it.model) } } }
    return instance
}
