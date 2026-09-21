package com.orbitar.nativeapp

/** Pixel bounds shared by the crop preview and the bitmap submitted to ARCore. */
internal data class TriggerCrop(val left: Int, val top: Int, val width: Int, val height: Int)

internal fun triggerCrop(width: Int, height: Int, left: Float, top: Float, right: Float, bottom: Float): TriggerCrop {
    require(width > 0 && height > 0)
    val x = (width * left.coerceIn(0f, .45f)).toInt()
    val y = (height * top.coerceIn(0f, .45f)).toInt()
    val endX = width - (width * right.coerceIn(0f, .45f)).toInt()
    val endY = height - (height * bottom.coerceIn(0f, .45f)).toInt()
    return TriggerCrop(x, y, (endX - x).coerceAtLeast(1), (endY - y).coerceAtLeast(1))
}

internal enum class TriggerTracking { SEARCHING, LOCKED, REMEMBERED, LOST, CAMERA_LIMITED }

internal fun triggerTracking(cameraTracking: Boolean, fullTracking: Boolean, lastKnownPose: Boolean, previouslySeen: Boolean): TriggerTracking = when {
    !cameraTracking -> TriggerTracking.CAMERA_LIMITED
    fullTracking -> TriggerTracking.LOCKED
    lastKnownPose -> TriggerTracking.REMEMBERED
    previouslySeen -> TriggerTracking.LOST
    else -> TriggerTracking.SEARCHING
}

internal val TriggerTracking.title: String get() = when (this) {
    TriggerTracking.SEARCHING -> "Looking for your trigger"
    TriggerTracking.LOCKED -> "Trigger locked"
    TriggerTracking.REMEMBERED -> "Using last known position"
    TriggerTracking.LOST -> "Trigger tracking lost"
    TriggerTracking.CAMERA_LIMITED -> "Camera tracking limited"
}

internal val TriggerTracking.guidance: String get() = when (this) {
    TriggerTracking.SEARCHING -> "Keep the whole trigger in view. Hold steady; move closer if it does not lock."
    TriggerTracking.LOCKED -> "Visually tracking your trigger. You can now test moving farther away."
    TriggerTracking.REMEMBERED -> "The trigger is no longer visually locked. Keep it still; move closer to reacquire."
    TriggerTracking.LOST -> "Move closer and point at the full trigger to reacquire it."
    TriggerTracking.CAMERA_LIMITED -> "Move slowly in good light so the camera can track your surroundings."
}
