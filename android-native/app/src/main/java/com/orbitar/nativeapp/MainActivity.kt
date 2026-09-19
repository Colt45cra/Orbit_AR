package com.orbitar.nativeapp

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.testTag
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.ar.node.AugmentedImageNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Size

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
internal fun OrbitNativeApp(initialTrigger: Bitmap? = null, initialPopup: Bitmap? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var triggerBitmap by remember { mutableStateOf<Bitmap?>(initialTrigger) }
    var popupBitmap by remember { mutableStateOf<Bitmap?>(initialPopup) }
    var targetWidthCm by rememberSaveable { mutableFloatStateOf(10f) }
    var popupWidthCm by rememberSaveable { mutableFloatStateOf(8f) }
    var tiltDegrees by rememberSaveable { mutableFloatStateOf(90f) }
    var offsetX by rememberSaveable { mutableFloatStateOf(0f) }
    var offsetZ by rememberSaveable { mutableFloatStateOf(0f) }
    var tab by rememberSaveable { mutableStateOf(0) }
    val imagesScroll = rememberLazyListState()
    val placementScroll = rememberLazyListState()
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
            popupWidthCm / 100f, tiltDegrees, offsetX, offsetZ, onBack = { runningAr = false },
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
                        if (tab == 0) tab = 1
                        else if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) runningAr = true
                        else permission.launch(Manifest.permission.CAMERA)
                    }, enabled = ready, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp)) {
                        Text(if (tab == 0) "Place images →" else "Launch AR", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ORBIT AR", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                Text("v0.5 · Runtime stability", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("1 · Images") }, modifier = Modifier.weight(1f).testTag("images-tab"))
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("2 · Placement") }, modifier = Modifier.weight(1f).testTag("placement-tab"))
            }
            if (tab == 0) {
                LazyColumn(state = imagesScroll, modifier = Modifier.weight(1f).fillMaxWidth().testTag("images-list"),
                    contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    item { Text("Choose your two images", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                    item { PickerCard("01", "Trigger image", "The real image your camera will recognize.", triggerBitmap, !loading) { triggerPicker.launch("image/*") } }
                    item { PickerCard("02", "AR image", "The image that appears on your trigger. PNG supports transparency.", popupBitmap, !loading) { popupPicker.launch("image/*") } }
                    item {
                        Text("Next: see both images together and place your AR image on the trigger.", modifier = Modifier.testTag("images-end"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item { Text("Use a detailed, matte trigger in good light. The camera needs to see the whole trigger when scanning.", style = MaterialTheme.typography.bodySmall) }
                    item { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                }
            } else {
                LazyColumn(state = placementScroll, modifier = Modifier.weight(1f).fillMaxWidth().testTag("placement-list"),
                    contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    item {
                        if (triggerBitmap != null && popupBitmap != null) {
                            PlacementPreview(triggerBitmap!!, popupBitmap!!, targetWidthCm / 100f, popupWidthCm / 100f,
                                tiltDegrees, offsetX, offsetZ, onMove = { x, z -> offsetX = x; offsetZ = z })
                        } else Text("Choose a trigger and an AR image in the Images tab to preview placement.")
                    }
                    item { Text("Size & position", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    item { Text("Measure the printed trigger from left to right. Preview and camera use the same placement.", style = MaterialTheme.typography.bodySmall) }
                    item { LabeledSlider("Trigger width", targetWidthCm, "${targetWidthCm.toInt()} cm", 4f..60f) { targetWidthCm = it } }
                    item { LabeledSlider("AR image width", popupWidthCm, "${popupWidthCm.toInt()} cm", 2f..40f) { popupWidthCm = it } }
                    item { TiltControls(tiltDegrees) { tiltDegrees = it } }
                    item { LabeledSlider("Left / right", offsetX, "${(offsetX * 100).toInt()} cm", -0.6f..0.6f) { offsetX = it } }
                    item { LabeledSlider("Back / forward", offsetZ, "${(offsetZ * 100).toInt()} cm", -0.6f..0.6f) { offsetZ = it } }
                    item { OutlinedButton(onClick = { offsetX = 0f; offsetZ = 0f; popupWidthCm = 8f; tiltDegrees = 90f }, modifier = Modifier.fillMaxWidth().testTag("placement-end")) { Text("Reset placement") } }
                    item { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
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
    offsetX: Float,
    offsetZ: Float,
    onBack: () -> Unit,
    onWidthChange: (Float) -> Unit,
    onTiltChange: (Float) -> Unit
) {
    BackHandler(onBack = onBack)
    var showControls by remember { mutableStateOf(false) }
    val detectedImages = remember { mutableStateListOf<AugmentedImage>() }
    var status by remember { mutableStateOf("Point camera at trigger") }
    var arFailure by remember { mutableStateOf<String?>(null) }
    var sessionKey by remember { mutableIntStateOf(0) }

    val popupAspect = popupBitmap.width.toFloat() / popupBitmap.height.coerceAtLeast(1)
    val popupHeightMeters = popupWidthMeters / popupAspect

    Box(modifier = Modifier.fillMaxSize()) {
        key(sessionKey) {
            ARSceneView(
                modifier = Modifier.fillMaxSize(),
                planeRenderer = false,
                sessionCameraConfig = null,
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE,
                focusMode = Config.FocusMode.AUTO,
                sessionConfiguration = { session, config ->
                config.planeFindingMode = Config.PlaneFindingMode.DISABLED
                config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                config.augmentedImageDatabase = AugmentedImageDatabase(session).apply {
                    addImage("orbit-target", triggerBitmap, targetWidthMeters)
                }
                },
                onSessionCreated = {
                    arFailure = null
                    status = "Point camera at trigger"
                },
                onSessionFailed = { exception ->
                    detectedImages.clear()
                    status = "Couldn't start AR"
                    val detail = exception.message?.takeIf { it.isNotBlank() } ?: "No additional details were provided."
                    arFailure = "${exception.javaClass.simpleName}: $detail"
                },
                onSessionUpdated = { _, frame ->
                    frame.getUpdatedTrackables(AugmentedImage::class.java).forEach { image ->
                        if (
                            image.trackingState == TrackingState.TRACKING &&
                            image.trackingMethod == AugmentedImage.TrackingMethod.FULL_TRACKING &&
                            detectedImages.none { it.index == image.index }
                        ) {
                            detectedImages.add(image)
                        }
                    }

                    detectedImages.removeAll { it.trackingState == TrackingState.STOPPED }

                    status = when {
                        frame.camera.trackingState != TrackingState.TRACKING -> "Camera tracking limited"
                        detectedImages.isNotEmpty() -> "Trigger found"
                        else -> "Looking for your trigger"
                    }
                },
            onTrackingFailureChanged = { reason ->
                if (reason != null) status = reason.name.replace('_', ' ')
            }
        ) {
            detectedImages.forEach { image ->
                AugmentedImageNode(
                    augmentedImage = image,
                    applyImageScale = false
                ) {
                    ImageNode(
                        bitmap = popupBitmap,
                        size = Size(x = popupWidthMeters, y = popupHeightMeters),
                        position = placementCenter(popupHeightMeters, tiltDegrees, offsetX, offsetZ).let { Position(it.x, it.y, it.z) },
                        rotation = Rotation(x = tiltDegrees - 90f)
                    )
                }
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
            Text(
                if (arFailure == null) "ARCore native image tracking" else "Session startup failed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
            color = Color(0xEE111511), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(16.dp).heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (detectedImages.isNotEmpty()) "Move around to explore your AR image."
                    else if (arFailure != null) "ARCore could not start the camera session."
                    else "Point at the full trigger image. Move slowly in good light.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                arFailure?.let { failure ->
                    Text(
                        failure,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = {
                            arFailure = null
                            detectedImages.clear()
                            status = "Starting AR…"
                            sessionKey += 1
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text("Try again")
                    }
                }
                if (showControls && arFailure == null) {
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
