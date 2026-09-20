package com.orbitar.nativeapp.room

import android.graphics.Bitmap
import com.google.ar.core.Anchor

enum class RoomAssetType {
    IMAGE,
    MODEL_GLB
}

data class RoomAsset(
    val type: RoomAssetType,
    val label: String,
    val bitmap: Bitmap? = null,
    val modelUri: String? = null
)

data class RoomPlacement(
    val id: Long,
    val anchor: Anchor,
    val asset: RoomAsset,
    val rotationY: Float = 0f,
    val scale: Float = 1f,
    val elevation: Float = 0f,
    val flat: Boolean = false,
    val surfaceLabel: String = "Surface",
    val alignmentYaw: Float = 0f
)
