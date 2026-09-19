package com.orbitar.nativeapp

import android.Manifest
import android.content.pm.PackageManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val scope = rememberCoroutineScope()
    var triggerBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var popupBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var targetWidthCm by rememberSaveable { mutableFloatStateOf(10f) }
    var popupWidthCm by rememberSaveable { mutableFloatStateOf(8f) }
    var tiltDegrees by rememberSaveable { mutableFloatStateOf(90f) }
    var runningAr by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) runningAr = true
        else error = "Camera access is needed for AR. Allow it in Android Settings, then try again."
    }
    val triggerPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            loading = true
            error = null
            val bitmap = withContext(Dispatchers.IO) { decodeBitmap(context, uri) }
            if (bitmap != null) triggerBitmap = bitmap else error = "Couldn't open that image. Try another JPG or PNG."
            loading = false
        }
    }
    val popupPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            loading = true
            error = null
            val bitmap = withContext(Dispatchers.IO) { decodeBitmap(context, uri) }
            if (bitmap != null) popupBitmap = bitmap else error = "Couldn't open that image. Try another JPG or PNG."
            loading = false
        }
    }
    if (runningAr && triggerBitmap != null && popupBitmap != null) {
        NativeARScreen(triggerBitmap!!, popupBitmap!!, targetWidthCm / 100f,
            popupWidthCm / 100f, tiltDegrees, onBack = { runningAr = false },
            onWidthChange = { popupWidthCm = it * 100f }, onTiltChange = { tiltDegrees = it })
        return
    }
    val ready = triggerBitmap != null && popupBitmap != null && !loading
    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 4.dp) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (loading) "Preparing your image…" else if (ready) "Ready to bring your image to life" else "Add both images to continue",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = {
                        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) runningAr = true
                        else permission.launch(Manifest.permission.CAMERA)
                    }, enabled = ready, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("Launch AR", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("ORBIT", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f))
                Text("AR STUDIO", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Make reality\nmore interesting.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Choose what your camera recognizes, then what appears in AR.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PickerCard("01", "Trigger image", "The real image your camera will recognize.", triggerBitmap, !loading) { triggerPicker.launch("image/*") }
            PickerCard("02", "AR image", "The image that appears above your trigger. PNG supports transparency.", popupBitmap, !loading) { popupPicker.launch("image/*") }
            Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 2.dp) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("03  Size & placement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Measure the physical trigger from left to right for accurate scale.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LabeledSlider("Trigger width", targetWidthCm, "${targetWidthCm.toInt()} cm", 4f..60f) { targetWidthCm = it }
                    LabeledSlider("AR image width", popupWidthCm, "${popupWidthCm.toInt()} cm", 2f..40f) { popupWidthCm = it }
                    TiltControls(tiltDegrees) { tiltDegrees = it }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("For best results, use a detailed, matte trigger in good light. Keep the whole trigger visible when scanning.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PickerCard(step: String, title: String, description: String, bitmap: Bitmap?, enabled: Boolean, onPick: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(enabled = enabled, onClick = onPick)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(68.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF252C24)), contentAlignment = Alignment.Center) {
                    if (bitmap != null) Image(bitmap.asImageBitmap(), title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else Text(step, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (bitmap == null) description else "Selected · ${bitmap.width} × ${bitmap.height}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = onPick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (bitmap == null) "Choose image" else "Change image")
            }
        }
    }
}

@Composable
private fun TiltControls(value: Float, onChange: (Float) -> Unit) {
    LabeledSlider("Tilt", value, "${value.toInt()}°", 0f..90f, onChange)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = value == 0f, onClick = { onChange(0f) }, label = { Text("Flat") })
        FilterChip(selected = value == 90f, onClick = { onChange(90f) }, label = { Text("Upright") })
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
    onBack: () -> Unit,
    onWidthChange: (Float) -> Unit,
    onTiltChange: (Float) -> Unit
) {
    BackHandler(onBack = onBack)
    var showControls by remember { mutableStateOf(false) }
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
                    full != null -> "Trigger found"
                    else -> "Looking for your trigger"
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
                .statusBarsPadding()
                .padding(top = 12.dp, start = 20.dp, end = 20.dp)
                .background(Color(0xCC0A0D08), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(status, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(fpsMode, style = MaterialTheme.typography.bodySmall)
        }

        Surface(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            color = Color(0xEE111511), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp).heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (trackedImage != null) "Move around to explore your AR image." else "Point at the full trigger image. Move slowly in good light.",
                    style = MaterialTheme.typography.bodySmall)
                if (showControls) {
                    LabeledSlider("Image width", popupWidthMeters, "${(popupWidthMeters * 100).toInt()} cm", 0.02f..0.4f, onWidthChange)
                    TiltControls(tiltDegrees, onTiltChange)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Edit setup") }
                    Button(onClick = { showControls = !showControls }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(if (showControls) "Done" else "Adjust")
                    }
                }
            }
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
