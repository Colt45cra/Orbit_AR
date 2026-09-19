package com.orbitar.nativeapp.room

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import java.util.concurrent.atomic.AtomicLong

private val roomPlacementIds = AtomicLong(1L)

@Composable
fun RoomModeScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val placements = remember { mutableStateListOf<RoomPlacement>() }
    val latestFrame = remember { arrayOfNulls<Frame>(1) }

    var currentAsset by remember { mutableStateOf<RoomAsset?>(null) }
    var placementArmed by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var surfacesFound by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Move around to map the room") }
    var sessionFailure by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bitmap = decodeRoomBitmap(context, uri)
        if (bitmap != null) {
            currentAsset = RoomAsset(RoomAssetType.IMAGE, "Image", bitmap = bitmap)
            placementArmed = true
            status = "Tap a detected surface to place the image"
        } else {
            status = "Couldn't open that image"
        }
    }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        currentAsset = RoomAsset(RoomAssetType.MODEL_GLB, "3D model", modelUri = uri.toString())
        placementArmed = true
        status = "Tap a detected surface to place the 3D model"
    }

    DisposableEffect(Unit) {
        onDispose {
            placements.forEach { runCatching { it.anchor.detach() } }
        }
    }

    val selected = placements.firstOrNull { it.id == selectedId }

    Box(Modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            sessionCameraConfig = null,
            planeRenderer = true,
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE,
            focusMode = Config.FocusMode.AUTO,
            onSessionCreated = {
                sessionFailure = null
                status = "Move around to map the room"
            },
            onSessionFailed = { exception ->
                val detail = exception.message?.takeIf { it.isNotBlank() }
                    ?: "No additional details were provided."
                sessionFailure = "${exception.javaClass.simpleName}: §detail"
                status = "Couldn't start Room Mode"
            },
            onSessionUpdated = { _, frame ->
                latestFrame[0] = frame
                if (!surfacesFound) {
                    val found = frame.getUpdatedTrackables(Plane::class.java).any {
                        it.trackingState == TrackingState.TRACKING
                    }
                    if (found) {
                        surfacesFound = true
                        status = if (currentAsset == null) {
                            "Surfaces ready — choose something to place"
                        } else {
                            "Tap a surface to place ${currentAsset?.label?.lowercase()}"
                        }
                    }
                }
            },
            onTrackingFailureChanged = { reason ->
                if (reason != null) status = reason.name.replace('_', ' ')
            },
            onTouchEvent = { event, _ ->
                if (
                    event.action == MotionEvent.ACTION_UP &&
                    placementArmed &&
                    currentAsset != null &&
                    sessionFailure == null
                ) {
                    val frame = latestFrame[0]
                    val hit = frame?.hitTest(event.x, event.y)?.firstOrNull { candidate ->
                        val plane = candidate.trackable as? Plane
                        plane != null &&
                            plane.trackingState == TrackingState.TRACKING &&
                            plane.isPoseInPolygon(candidate.hitPose)
                    }

                    if (hit != null) {
                        val anchor = runCatching { hit.createAnchor() }.getOrNull()
                        if (anchor != null) {
                            val placement = RoomPlacement(
                                id = roomPlacementIds.getAndIncrement(),
                                anchor = anchor,
                                asset = currentAsset!!
                            )
                            placements.add(placement)
                            selectedId = placement.id
                            placementArmed = false
                            status = "Placed • ${placements.size} object${if (placements.size == 1) "" else "s"} in room"
                            true
                        } else {
                            status = "Couldn't create an anchor there — try another spot"
                            false
                        }
                    } else {
                        status = "No mapped surface there yet — move the phone around and try again"
                        false
                    }
                } else false
            }
        ) {
            placements.forEach { placement ->
                key(placement.id) {
                    when (placement.asset.type) {
                        RoomAssetType.IMAGE -> {
                            val bitmap = placement.asset.bitmap
                            if (bitmap != null) {
                                val aspect = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
                                val width = 0.24f * placement.scale
                                val height = width / aspect
                                AnchorNode(anchor = placement.anchor) {
                                    ImageNode(
                                        bitmap = bitmap,
                                        size = Size(x = width, y = height),
                                        position = Position(y = height / 2f),
                                        rotation = Rotation(y = placement.rotationY),
                                        apply = {
                                            name = "room-${placement.id}"
                                            isTouchable = true
                                        }
                                    )
                                }
                            }
                        }

                        RoomAssetType.MODEL_GLB -> {
                            val uri = placement.asset.modelUri
                            if (uri != null) {
                                val instance = rememberModelInstance(
                                    modelLoader = modelLoader,
                                    fileLocation = uri
                                )
                                AnchorNode(anchor = placement.anchor) {
                                    instance?.let {
                                        ModelNode(
                                            modelInstance = it,
                                            autoAnimate = true,
                                            scaleToUnits = 0.28f * placement.scale,
                                            centerOrigin = Position(0f, -1f, 0f),
                                            rotation = Rotation(y = placement.rotationY),
                                            isEditable = false,
                                            apply = {
                                                name = "room-${placement.id}"
                                                isTouchable = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(14.dp),
            color = Color(0xDD0A0D08),
            shape = RoundedCornerShape(22.dp)
        ) {
            Column(
                Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("ROOM MODE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                sessionFailure?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
            color = Color(0xF2111511),
            shape = RoundedCornerShape(26.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 430.dp).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.weight(1f)) {
                        Text("Add image")
                    }
                    Button(onClick = { modelPicker.launch("*/*") }, modifier = Modifier.weight(1f)) {
                        Text("Add GLB")
                    }
                }

                currentAsset?.let { asset ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Ready: ${asset.label}", modifier = Modifier.weight(1f))
                        FilterChip(
                            selected = placementArmed,
                            onClick = { placementArmed = !placementArmed },
                            label = { Text(if (placementArmed) "Tap to place" else "Place another") }
                        )
                    }
                }

                if (placements.isNotEmpty()) {
                    Text("Objects in room: ${placements.size}", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        placements.takeLast(4).forEach { placement ->
                            FilterChip(
                                selected = selectedId == placement.id,
                                onClick = { selectedId = placement.id },
                                label = { Text("#${placements.indexOf(placement) + 1}") }
                            )
                        }
                    }
                }

                selected?.let { objectToEdit ->
                    Text("Selected object", fontWeight = FontWeight.Bold)
                    RoomSlider(
                        "Rotation",
                        objectToEdit.rotationY,
                        -180f..180f,
                        "${objectToEdit.rotationY.toInt()}°"
                    ) { value ->
                        replacePlacement(placements, objectToEdit.copy(rotationY = value))
                    }
                    RoomSlider(
                        "Scale",
                        objectToEdit.scale,
                        0.25f..3f,
                        String.format("%.2f×", objectToEdit.scale)
                    ) { value ->
                        replacePlacement(placements, objectToEdit.copy(scale = value))
                    }
                    OutlinedButton(
                        onClick = {
                            runCatching { objectToEdit.anchor.detach() }
                            placements.removeAll { it.id == objectToEdit.id }
                            selectedId = placements.lastOrNull()?.id
                            status = "Object deleted"
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete selected object")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text("Exit Room Mode")
                    }
                    if (placements.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                placements.forEach { runCatching { it.anchor.detach() } }
                                placements.clear()
                                selectedId = null
                                status = "Room cleared"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear room")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f))
            Text(valueText, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

private fun replacePlacement(
    placements: androidx.compose.runtime.snapshots.SnapshotStateList<RoomPlacement>,
    replacement: RoomPlacement
) {
    val index = placements.indexOfFirst { it.id == replacement.id }
    if (index >= 0) placements[index] = replacement
}

private fun decodeRoomBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > 2048) decoder.setTargetSampleSize((longest + 2047) / 2048)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }
    } catch (_: Exception) {
        null
    }
}
