package com.orbitar.nativeapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ar.core.AugmentedImage
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AugmentedImageNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size
import java.util.EnumSet

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OrbitTheme {
                OrbitNativeApp()
            }
        }
    }
}

private val OrbitColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFB8FF3D),
    onPrimary = Color(0xFF0A0D08),
    background = Color(0xFF070907),
    surface = Color(0xFF111511),
    onBackground = Color(0xFFF5F7F2),
    onSurface = Color(0xFFF5F7F2)
)

@Composable
private fun OrbitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OrbitColors, content = content)
}

@Composable
private fun OrbitNativeApp() {
    val context = LocalContext.current
    var triggerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var popupBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var targetWidthCm by remember { mutableFloatStateOf(10f) }
    var popupWidthCm by remember { mutableFloatStateOf(8f) }
    var tiltDegrees by remember { mutableFloatStateOf(90f) }
    var runningAr by remember { mutableStateOf(false) }

    val triggerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        triggerBitmap = uri?.let { decodeBitmap(context, it) }
    }
    val popupPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        popupBitmap = uri?.let { decodeBitmap(context, it) }
    }

    if (runningAr && triggerBitmap != null && popupBitmap != null) {
        NativeARScreen(
            triggerBitmap = triggerBitmap!!,
            popupBitmap = popupBitmap!!,
            targetWidthMeters = targetWidthCm / 100f,
            popupWidthMeters = popupWidthCm / 100f,
            tiltDegrees = tiltDegrees,
            onBack = { runningAr = false }
        )
        return
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "ORBIT AR • NATIVE",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Professional ARCore image tracking",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Choose a trigger and popup image, enter the real trigger width, then test native tracking."
            )

            PickerCard(
                title = "Trigger image",
                bitmap = triggerBitmap,
                buttonText = "Choose trigger",
                onPick = { triggerPicker.launch("image/*") }
            )
            PickerCard(
                title = "Popup image",
                bitmap = popupBitmap,
                buttonText = "Choose popup",
                onPick = { popupPicker.launch("image/*") }
            )

            LabeledSlider(
                label = "Real trigger width",
                value = targetWidthCm,
                valueText = "\${targetWidthCm.toInt()} cm",
                range = 4f..60f,
                onChange = { targetWidthCm = it }
            )
            LabeledSlider(
                label = "Popup width",
                value = popupWidthCm,
                valueText = "\${popupWidthCm.toInt()} cm",
                range = 2f..40f,
                onChange = { popupWidthCm = it }
            )
            LabeledSlider(
                label = "Tilt from trigger",
                value = tiltDegrees,
                valueText = "\${tiltDegrees.toInt()}°",
                range = 0f..90f,
                onChange = { tiltDegrees = it }
            )

            Spacer(Modifier.weight(1f))

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = triggerBitmap != null && popupBitmap != null,
                onClick = { runningAr = true }
            ) {
                Text("Start Native AR")
            }
        }
    }
}

@Composable
private fun PickerCard(
    title: String,
    bitmap: Bitmap?,
    buttonText: String,
    onPick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.size(74.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .background(Color(0xFF202620), RoundedCornerShape(12.dp))
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(
                if (bitmap == null) "Not selected" else "\${bitmap.width} × \${bitmap.height}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Button(onClick = onPick) { Text(buttonText) }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f))
            Text(valueText, fontWeight = FontWeight.Bold)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@Composable
private fun NativeARScreen(
    triggerBitmap: Bitmap,
    popupBitmap: Bitmap,
    targetWidthMeters: Float,
    popupWidthMeters: Float,
    tiltDegrees: Float,
    onBack: () -> Unit
) {
    var trackedImage by remember { mutableStateOf<AugmentedImage?>(null) }
    var status by remember { mutableStateOf("Point camera at trigger") }
    var fpsMode by remember { mutableStateOf("Selecting camera") }

    val popupAspect = popupBitmap.width.toFloat() / popupBitmap.height.coerceAtLeast(1)
    val popupHeightMeters = popupWidthMeters / popupAspect

    Box(modifier = Modifier.fillMaxSize()) {
        ARSceneView(
            modifier = Modifier.fillMaxSize(),
            planeRenderer = false,
            imageStabilizationMode = Config.ImageStabilizationMode.EIS,
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE,
            focusMode = Config.FocusMode.AUTO,
            sessionCameraConfig = { session ->
                bestTrackingCameraConfig(session).also {
                    fpsMode = if (it.fpsRange.upper >= 60) "60 FPS tracking" else "30 FPS tracking"
                }
            },
            sessionConfiguration = { session, config ->
                config.planeFindingMode = Config.PlaneFindingMode.DISABLED
                config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                config.augmentedImageDatabase = AugmentedImageDatabase(session).apply {
                    addImage("orbit-target", triggerBitmap, targetWidthMeters)
                }
            },
            onSessionUpdated = { session, frame ->
                val full = session
                    .getAllTrackables(AugmentedImage::class.java)
                    .firstOrNull {
                        it.trackingState == TrackingState.TRACKING &&
                            it.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING
                    }

                trackedImage = full
                status = when {
                    frame.camera.trackingState != TrackingState.TRACKING -> "Camera tracking limited"
                    full != null -> "FULL TRACKING • locked"
                    else -> "Searching for trigger"
                }
            },
            onTrackingFailureChanged = { reason ->
                if (reason != null) status = reason.name.replace('_', ' ')
            }
        ) {
            trackedImage?.let { image ->
                AugmentedImageNode(
                    augmentedImage = image,
                    applyImageScale = false
                ) {
                    ImageNode(
                        bitmap = popupBitmap,
                        size = Size(x = popupWidthMeters, y = popupHeightMeters),
                        position = Position(x = 0f, y = popupHeightMeters / 2f, z = 0f),
                        rotation = Rotation(x = 90f - tiltDegrees)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
                .background(Color(0xCC0A0D08), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(status, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(fpsMode, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            onClick = onBack
        ) {
            Text("Back")
        }
    }
}

private fun bestTrackingCameraConfig(session: Session): CameraConfig {
    val sixtyFpsFilter = CameraConfigFilter(session).apply {
        targetFps = EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_60)
        depthSensorUsage = EnumSet.of(CameraConfig.DepthSensorUsage.DO_NOT_USE)
    }
    val sixty = session.getSupportedCameraConfigs(sixtyFpsFilter)
    if (sixty.isNotEmpty()) {
        return sixty.maxByOrNull { it.textureSize.width * it.textureSize.height } ?: sixty.first()
    }

    val thirtyFpsFilter = CameraConfigFilter(session).apply {
        targetFps = EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30)
        depthSensorUsage = EnumSet.of(CameraConfig.DepthSensorUsage.DO_NOT_USE)
    }
    return session.getSupportedCameraConfigs(thirtyFpsFilter).firstOrNull() ?: session.cameraConfig
}

private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
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
