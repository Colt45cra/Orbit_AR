package com.orbitar.nativeapp.room

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.ar.core.*
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.*

private val roomPlacementIds=AtomicLong(1L)
private enum class RoomCommand { PLACE, MOVE, SET_FLOOR }

@Composable
fun RoomModeScreen(onBack:()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val engine=rememberEngine()
    val loader=rememberModelLoader(engine)
    val scanner=remember { RoomSurfaceScanner() }
    val placements=remember { mutableStateListOf<RoomPlacement>() }
    val modelMessages=remember { mutableStateMapOf<Long,String>() }
    val floor=remember { arrayOfNulls<Anchor>(1) }
    var hasFloor by remember { mutableStateOf(false) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scan by remember { mutableStateOf(RoomScanUi()) }
    var mode by remember { mutableStateOf(SurfaceMode.AUTO) }
    var currentAsset by remember { mutableStateOf<RoomAsset?>(null) }
    var armed by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var command by remember { mutableStateOf<RoomCommand?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }
    var showMap by remember { mutableStateOf(true) }
    var snapCenter by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var depthEnabled by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val lastScan=remember { longArrayOf(0L) }
    val down=remember { floatArrayOf(0f,0f) }
    val tapSlop=with(LocalDensity.current) { 12.dp.toPx() }
    val selected=placements.firstOrNull { it.id==selectedId }

    fun exit() { if(placements.isEmpty()) onBack() else confirmExit=true }
    BackHandler { exit() }
    fun replace(item:RoomPlacement) { val i=placements.indexOfFirst { it.id==item.id };if(i>=0) placements[i]=item }
    fun clear() {
        placements.forEach { runCatching { it.anchor.detach() } };placements.clear();modelMessages.clear();selectedId=null
        moving=false;armed=false;command=null
    }
    DisposableEffect(Unit) { onDispose {
        placements.forEach { runCatching { it.anchor.detach() } }
        floor[0]?.let { runCatching { it.detach() } }
    } }

    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null) scope.launch {
            loading=true;notice=null
            val asset=withContext(Dispatchers.IO) {
                decodeRoomBitmap(context,uri)?.let { RoomAsset(RoomAssetType.IMAGE,fileName(context,uri,"Image"),bitmap=it) }
            }
            loading=false
            if(asset==null) notice="Couldn't open that image. Try a JPG or PNG."
            else { currentAsset=asset;armed=true;moving=false;showTools=false;scanner.reset() }
        }
    }
    val modelPicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null) scope.launch {
            loading=true;notice=null
            val valid=withContext(Dispatchers.IO) { runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val header=ByteArray(4);var count=0
                    while(count<4) { val n=input.read(header,count,4-count);if(n<0) break;count+=n }
                    count==4 && header.contentEquals(byteArrayOf(0x67,0x6c,0x54,0x46))
                }==true
            }.getOrDefault(false) }
            loading=false
            if(!valid) notice="Choose a GLB model (.glb), not a ZIP or separate .gltf file."
            else { currentAsset=RoomAsset(RoomAssetType.MODEL_GLB,fileName(context,uri,"3D model"),modelUri=uri.toString())
                armed=true;moving=false;showTools=false;scanner.reset() }
        }
    }

    Box(Modifier.fillMaxSize().onSizeChanged { viewport=it }) {
        ARSceneView(modifier=Modifier.fillMaxSize(),engine=engine,modelLoader=loader,
            sessionCameraConfig=null,planeRenderer=false,
            planeFindingMode=Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL,
            imageStabilizationMode=Config.ImageStabilizationMode.OFF,
            updateMode=Config.UpdateMode.LATEST_CAMERA_IMAGE,focusMode=Config.FocusMode.AUTO,
            sessionConfiguration={session,config ->
                depthEnabled=session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
                config.depthMode=if(depthEnabled) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
                config.instantPlacementMode=Config.InstantPlacementMode.DISABLED
            },
            onSessionCreated={ failure=null;scanner.reset() },
            onSessionFailed={e -> failure="${e.javaClass.simpleName}: ${e.message ?: "Camera could not start"}";command=null;scan=RoomScanUi() },
            onSessionPaused={ scan=RoomScanUi(message="Camera paused — return to continue");scanner.reset();command=null },
            onSessionUpdated=update@{session,frame ->
                if(frame.timestamp-lastScan[0]<100_000_000L && command==null && frame.camera.trackingState==TrackingState.TRACKING) return@update
                lastScan[0]=frame.timestamp
                val floorAnchor=floor[0]
                val floorY=floorAnchor?.takeIf { it.trackingState==TrackingState.TRACKING }?.pose?.ty()
                hasFloor=floorY!=null
                val editing=if(moving) placements.firstOrNull {it.id==selectedId} else null
                val asset=editing?.asset ?: currentAsset
                val aspect=asset?.bitmap?.let {it.width.toFloat()/it.height} ?: 1f
                val margin=if(asset!=null && (armed || moving)) footprintMargin(editing?.scale ?: 1f,asset.type==RoomAssetType.MODEL_GLB,editing?.flat ?: false,aspect) else 0.02f
                val target=scanner.target(frame,viewport.width,viewport.height,mode,floorY,margin)
                scan=scanner.ui(session,frame,target,mode,floorY,showMap,armed || moving,snapCenter)
                val action=command ?: return@update
                command=null
                if(action==RoomCommand.SET_FLOOR) {
                    if(target?.let {it.horizontal && it.stable && it.safe}==true) {
                        runCatching { target.plane.createAnchor(target.hit.hitPose) }.onSuccess { anchor ->
                            floor[0]?.detach();floor[0]=anchor;hasFloor=true;notice="Floor reference set. You can now choose Floor or Tabletop.";scanner.reset()
                        }.onFailure { notice="Couldn't set the floor. Scan it again." }
                    } else notice="Aim at a clear floor area and hold steady before setting it."
                    return@update
                }
                if(target==null || !scan.canPlace) { notice="Surface moved or tracking changed. Hold steady and try again.";return@update }
                if((action==RoomCommand.PLACE && !armed) || (action==RoomCommand.MOVE && (editing==null || !moving))) return@update
                val assetToPlace=editing?.asset ?: currentAsset ?: return@update
                if(action==RoomCommand.PLACE && placements.size>=20) { notice="This room already has 20 objects. Delete one before adding another.";return@update }
                val point=if(snapCenter) Point2(target.polygon.map{it.x}.average().toFloat(),target.polygon.map{it.z}.average().toFloat()) else target.localPoint
                if(!insideWithMargin(target.polygon,point,margin)) { notice="Not enough mapped surface here. Scan the edges or choose another spot.";return@update }
                val pose=target.plane.centerPose.compose(Pose.makeTranslation(point.x,0f,point.z))
                val anchor=runCatching { target.plane.createAnchor(pose) }.getOrElse {notice="Couldn't anchor here. Please try again.";return@update}
                val edge=target.polygon.indices.maxByOrNull { i -> val a=target.polygon[i];val b=target.polygon[(i+1)%target.polygon.size];hypot(b.x-a.x,b.z-a.z) } ?: 0
                val a=target.polygon[edge];val b=target.polygon[(edge+1)%target.polygon.size]
                val edgeYaw=(-atan2(b.z-a.z,b.x-a.x)*180f/PI.toFloat()).coerceIn(-180f,180f)
                if(action==RoomCommand.MOVE && editing!=null) {
                    replace(editing.copy(anchor=anchor,elevation=0f,surfaceLabel=target.label,alignmentYaw=edgeYaw))
                    editing.anchor.detach();notice="Object moved to ${target.label.lowercase()}"
                } else {
                    val camera=pose.inverse().compose(frame.camera.pose)
                    val yaw=if(target.horizontal) atan2(camera.tx(),camera.tz())*180f/PI.toFloat() else 0f
                    val p=RoomPlacement(roomPlacementIds.getAndIncrement(),anchor,assetToPlace,rotationY=yaw,
                        flat=!target.horizontal && assetToPlace.type==RoomAssetType.IMAGE,surfaceLabel=target.label,alignmentYaw=edgeYaw)
                    placements.add(p);selectedId=p.id;notice="Placed on ${target.label.lowercase()}"
                }
                armed=false;moving=false;showTools=false
            },
            onTouchEvent={event,hit ->
                when(event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {down[0]=event.x;down[1]=event.y;false}
                    MotionEvent.ACTION_UP -> {
                        if(!armed && !moving && hypot(event.x-down[0],event.y-down[1])<tapSlop) {
                            val id=generateSequence(hit?.nodeOrNull) {it.parent}.take(10).mapNotNull {it.name?.removePrefix("room-")?.toLongOrNull()}.firstOrNull()
                            if(id!=null && placements.any{it.id==id}) {selectedId=id;showTools=true;true} else false
                        } else false
                    }
                    else -> false
                }
            }
        ) {
            placements.forEach { placement -> key(placement.id) {
                AnchorNode(anchor=placement.anchor) {
                    RoomObjectContent(placement) {id,message -> if(message==null) modelMessages.remove(id) else modelMessages[id]=message }
                }
            } }
        }

        Canvas(Modifier.fillMaxSize()) {
            scan.outlines.forEach { outline ->
                val path=Path();outline.points.forEachIndexed {i,p -> if(i==0) path.moveTo(p.x*size.width,p.z*size.height) else path.lineTo(p.x*size.width,p.z*size.height)};path.close()
                val color=if(outline.selected) {if(scan.canPlace) Color(0xFFB8FF3D) else Color(0xFFFFCB75)} else Color(0xFF75B8EF)
                drawPath(path,color.copy(alpha=0.06f));drawPath(path,color.copy(alpha=0.75f),style=Stroke(2.dp.toPx()))
            }
            if(scan.footprint.size>=3) {
                val path=Path();scan.footprint.forEachIndexed {i,p ->
                    if(i==0) path.moveTo(p.x*size.width,p.z*size.height) else path.lineTo(p.x*size.width,p.z*size.height)
                };path.close()
                val color=if(scan.canPlace) Color(0xFFB8FF3D) else Color(0xFFFFCB75)
                drawPath(path,color.copy(alpha=.18f));drawPath(path,color,style=Stroke(2.dp.toPx()))
            }
            if(scan.edgeGuide.size==2) {
                val a=scan.edgeGuide[0];val b=scan.edgeGuide[1]
                drawLine(Color(0xFFFFCB75),Offset(a.x*size.width,a.z*size.height),Offset(b.x*size.width,b.z*size.height),
                    strokeWidth=2.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(),6.dp.toPx())))
                drawCircle(Color(0xFFFFCB75),4.dp.toPx(),Offset(b.x*size.width,b.z*size.height))
            }
            val center=Offset(size.width*.5f,size.height*.40f)
            val color=if(scan.canPlace) Color(0xFFB8FF3D) else Color.White
            drawCircle(Color.Black.copy(alpha=.5f),19.dp.toPx(),center)
            drawCircle(color.copy(alpha=.35f),16.dp.toPx(),center,style=Stroke(2.dp.toPx()))
            val radius=16.dp.toPx()
            drawArc(color,-90f,360f*scan.holdProgress,false,Offset(center.x-radius,center.y-radius),Size(radius*2,radius*2),style=Stroke(3.dp.toPx()))
            drawCircle(color,3.dp.toPx(),center)
        }

        Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),color=Color(0xE6101510),contentColor=Color.White,shape=RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("ROOM · v0.8",fontWeight=FontWeight.Black,color=Color(0xFFB8FF3D),modifier=Modifier.weight(1f))
                    Text(if(depthEnabled) "Depth enabled" else "Plane mapping",style=MaterialTheme.typography.labelSmall)
                }
                Text(failure ?: scan.message,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.SemiBold)
                if(failure==null) {
                    Text("${scan.targetLabel} · ${scan.surfaceCount} mapped surfaces",style=MaterialTheme.typography.labelSmall,color=Color(0xFFC7D2C2))
                    scan.detail?.let {Text(it,style=MaterialTheme.typography.labelSmall,color=Color(0xFFC7D2C2))}
                }
            }
        }

        Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp).fillMaxWidth(),
            color=Color(0xF2101510),contentColor=Color.White,shape=RoundedCornerShape(24.dp)) {
            Column(Modifier.heightIn(max=if(showTools) 340.dp else 300.dp).verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                RoomScanControls(mode,hasFloor,scan.canSetFloor && failure==null,
                    onMode={mode=it;scanner.reset();notice=null},onSetFloor={command=RoomCommand.SET_FLOOR})
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={imagePicker.launch("image/*")},enabled=!loading && placements.size<20,modifier=Modifier.weight(1f)) {Text("Add image")}
                    OutlinedButton(onClick={modelPicker.launch("*/*")},enabled=!loading && placements.size<20,modifier=Modifier.weight(1f)) {Text("Add GLB")}
                }
                if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(armed || moving) {
                    Text(if(moving) "Move selected object" else "Ready: ${currentAsset?.label}",maxLines=1,style=MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(onClick={command=if(moving) RoomCommand.MOVE else RoomCommand.PLACE},enabled=scan.canPlace && failure==null,
                            modifier=Modifier.weight(1f)) {Text(if(moving) "Move here" else "Place here")}
                        TextButton(onClick={armed=false;moving=false;command=null}) {Text("Cancel")}
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={showTools=!showTools},modifier=Modifier.weight(1f)) {Text(if(showTools) "Hide controls" else "Surfaces & objects (${placements.size})")}
                    TextButton(onClick={exit()}) {Text("Exit")}
                }
                notice?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=Color(0xFFD8EBC8)) }
                if(showTools) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("Show mapped boundaries",Modifier.weight(1f));Switch(checked=showMap,onCheckedChange={showMap=it})
                    }
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("Place at mapped surface center",Modifier.weight(1f));Switch(checked=snapCenter,onCheckedChange={snapCenter=it})
                    }
                    Text("Blue: mapped areas. Amber: keep scanning or move inward. Green: ready. The ring shows placement clearance. Boundaries follow the scanned area; they are not confirmed physical edges. Scan around every table corner. Glass and glossy tops may need a textured mat.",style=MaterialTheme.typography.bodySmall)
                    if(placements.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            placements.forEachIndexed {index,p -> FilterChip(selected=p.id==selectedId,onClick={selectedId=p.id;armed=false;moving=false},label={Text("${index+1} · ${p.asset.label.take(16)}")}) }
                        }
                    }
                    selected?.let {p ->
                        Text("${p.asset.label} · ${p.surfaceLabel}",fontWeight=FontWeight.Bold)
                        modelMessages[p.id]?.let {Text(it,color=Color(0xFFFFD79A),style=MaterialTheme.typography.bodySmall)}
                        RoomTransformControls(p.scale,p.elevation,p.rotationY,p.flat,p.asset.type==RoomAssetType.IMAGE,
                            onScale={replace(p.copy(scale=it))},onElevation={replace(p.copy(elevation=it))},
                            onRotation={replace(p.copy(rotationY=it))},onFlat={replace(p.copy(flat=it))})
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick={moving=true;armed=false;showTools=false;scanner.reset()},modifier=Modifier.weight(1f)) {Text("Move")}
                            OutlinedButton(onClick={replace(p.copy(rotationY=p.alignmentYaw))},modifier=Modifier.weight(1f)) {Text("Align to edge")}
                        }
                        if(currentAsset!=null) TextButton(onClick={currentAsset=p.asset;armed=true;moving=false;showTools=false;scanner.reset()}) {Text("Place another copy")}
                        OutlinedButton(onClick={p.anchor.detach();placements.removeAll{it.id==p.id};modelMessages.remove(p.id);selectedId=placements.lastOrNull()?.id;moving=false;notice="Object deleted"},modifier=Modifier.fillMaxWidth()) {Text("Delete selected")}
                    }
                    if(placements.isNotEmpty()) TextButton(onClick={confirmClear=true}) {Text("Clear room")}
                    Text("Objects stay anchored during this room session. Exiting clears them.",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if(confirmClear || confirmExit) AlertDialog(onDismissRequest={confirmClear=false;confirmExit=false},
        title={Text(if(confirmExit) "Leave this room?" else "Clear all objects?")},
        text={Text("The ${placements.size} placed objects will be removed from this session.")},
        confirmButton={TextButton(onClick={if(confirmExit) {confirmExit=false;onBack()} else {clear();confirmClear=false;notice="Room cleared"}}) {Text(if(confirmExit) "Leave room" else "Clear")}},
        dismissButton={TextButton(onClick={confirmClear=false;confirmExit=false}) {Text("Keep room")}})
}

private fun fileName(context:Context,uri:Uri,fallback:String):String = runCatching {
    context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {cursor ->
        if(cursor.moveToFirst()) cursor.getString(0) else fallback
    } ?: fallback
}.getOrDefault(fallback)

private fun decodeRoomBitmap(context:Context,uri:Uri):Bitmap? = runCatching {
    if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P) ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver,uri)) {decoder,info,_ ->
        val longest=maxOf(info.size.width,info.size.height)
        if(longest>1024) decoder.setTargetSampleSize((longest+1023)/1024)
        decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
    } else {
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        context.contentResolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,bounds)}
        val options=BitmapFactory.Options().apply {inSampleSize=maxOf(1,(maxOf(bounds.outWidth,bounds.outHeight)+1023)/1024)}
        context.contentResolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,options)}
    }
}.getOrNull()
