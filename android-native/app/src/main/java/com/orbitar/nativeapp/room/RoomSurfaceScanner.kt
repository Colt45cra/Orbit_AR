package com.orbitar.nativeapp.room

import android.opengl.Matrix
import com.google.ar.core.*
import kotlin.math.*

internal data class SurfaceOutline(val points: List<Point2>, val selected: Boolean)
internal data class RoomScanUi(
    val message: String = "Move slowly across the floor and around table edges",
    val targetLabel: String = "Looking for a surface",
    val canPlace: Boolean = false,
    val canSetFloor: Boolean = false,
    val tracked: Boolean = false,
    val surfaceCount: Int = 0,
    val outlines: List<SurfaceOutline> = emptyList(),
    val holdProgress: Float = 0f,
    val detail: String? = null,
    val footprint: List<Point2> = emptyList(),
    val edgeGuide: List<Point2> = emptyList(),
    val depthPatch: DepthPatch = DepthPatch(),
    val depthVerified: Boolean = false
)
internal data class RoomTarget(val hit: HitResult, val plane: Plane, val horizontal: Boolean, val stable: Boolean,
    val allowed: Boolean, val safe: Boolean, val label: String, val localPoint: Point2, val polygon: List<Point2>, val margin: Float, val depthVerified: Boolean = false, val patch: DepthPatch = DepthPatch())

internal class RoomSurfaceScanner {
    private val stability = AimStability()
    private var depthIssue: String? = null
    fun reset() {stability.reset();depthIssue=null}
    fun target(frame: Frame, width: Int, height: Int, mode: SurfaceMode, floorY: Float?, margin: Float, requireDepth: Boolean = false): RoomTarget? {
        depthIssue=null
        if(frame.camera.trackingState != TrackingState.TRACKING || width<=0 || height<=0) { stability.reset(); return null }
        // Select the nearest real polygon FIRST. Never skip a tabletop to place on a floor behind it.
        val depth=if(requireDepth) RoomDepthProbe.acquire(frame) else null
        try {
        if(requireDepth && depth==null) {depthIssue="Gathering depth — move slowly sideways";stability.reset();return null}
        var rejected=false
        val hit=frame.hitTest(width*0.5f,height*0.40f).firstOrNull { h ->
            val p=h.trackable as? Plane
            p!=null && p.subsumedBy==null && p.trackingState==TrackingState.TRACKING &&
                p.isPoseInPolygon(h.hitPose) && p.type!=Plane.Type.HORIZONTAL_DOWNWARD_FACING &&
                (if(requireDepth) {
                    val agrees=depth?.agreesAtAim(h.hitPose)==true
                    if(!agrees) rejected=true
                    agrees
                } else true)
        } ?: run { if(rejected) depthIssue="The visible surface does not match the map yet — scan from another angle";stability.reset(); return null }
        val plane=hit.trackable as Plane
        val horizontal=plane.type==Plane.Type.HORIZONTAL_UPWARD_FACING
        val polygon=polygon(plane)
        val local=plane.centerPose.inverse().compose(hit.hitPose)
        val point=Point2(local.tx(),local.tz())
        val pose=hit.hitPose
        val normal=plane.centerPose.getTransformedAxis(1,1f)
        val camera=frame.camera.pose
        val front=(camera.tx()-pose.tx())*normal[0]+(camera.ty()-pose.ty())*normal[1]+(camera.tz()-pose.tz())*normal[2]>0
        val support=if(depth!=null) (0 until 12).all {i ->
            val angle=i*2*PI/12
            depth.supports(plane.centerPose.transformPoint(floatArrayOf(point.x+cos(angle).toFloat()*margin,0f,point.z+sin(angle).toFloat()*margin)))==true
        } else true
        val safe=support && front && hit.distance in 0.2f..5f && plane.extentX>=0.10f && plane.extentZ>=0.10f && insideWithMargin(polygon,point,margin)
        val stable=stability.update(if(safe) plane else null,if(safe) Point3(pose.tx(),pose.ty(),pose.tz()) else null,frame.timestamp)
        val above=floorY?.let { pose.ty()-it }
        return RoomTarget(hit,plane,horizontal,stable,acceptsSurface(mode,horizontal,above),safe,
            if(horizontal && mode==SurfaceMode.TABLE && above==null) "Tabletop · selected" else surfaceLabel(horizontal,above),point,polygon,margin,depth!=null,depth?.patch(plane) ?: DepthPatch())
        } finally {depth?.close()}
    }
    fun ui(session: Session, frame: Frame, target: RoomTarget?, mode: SurfaceMode, floorY: Float?, showMap: Boolean, placing: Boolean = false): RoomScanUi {
        val tracking=frame.camera.trackingState==TrackingState.TRACKING
        val planes=if(tracking) session.getAllTrackables(Plane::class.java).filter {
            it.trackingState==TrackingState.TRACKING && it.subsumedBy==null && it.type!=Plane.Type.HORIZONTAL_DOWNWARD_FACING
        } else emptyList()
        val message=when {
            !tracking -> when(frame.camera.trackingFailureReason) {
                TrackingFailureReason.INSUFFICIENT_LIGHT -> "More light is needed to track the room"
                TrackingFailureReason.EXCESSIVE_MOTION -> "Slow down and hold the camera steady"
                TrackingFailureReason.INSUFFICIENT_FEATURES -> "Aim at textured edges; avoid plain or shiny surfaces"
                else -> "Tracking paused — look back at an area you scanned"
            }
            mode==SurfaceMode.FLOOR && floorY==null -> "Aim at the floor, hold steady, then tap Set floor"
            target==null -> depthIssue ?: "Aim at a wall, floor or tabletop and move slowly sideways"
            !target.allowed -> "Aim at ${mode.label.lowercase()} — the nearer surface blocks placement"
            !target.safe -> "Not enough clear surface — aim farther inside or scan again"
            !target.stable -> "Hold steady to confirm the surface"
            else -> if(placing) "Surface ready — tap Place here" else "Surface ready — add an image or 3D object"
        }
        // Without depth, show a small dashed patch; never imply that a room-sized plane is a table edge.
        val preview=target?.let {t -> t.localPoint}
        val outlines=if(showMap && tracking && target!=null && !target.depthVerified) {
            val p=target.localPoint;val radius=.12f
            listOf(SurfaceOutline(project(frame,target.plane,listOf(Point2(p.x-radius,p.z-radius),Point2(p.x+radius,p.z-radius),Point2(p.x+radius,p.z+radius),Point2(p.x-radius,p.z+radius))),true))
        } else emptyList()
        val detail=when {
            target?.depthVerified==true -> "Depth-supported area · ${if(target.horizontal) "horizontal" else "vertical"}"
            target!=null -> "Plane estimate · depth unavailable · edges unverified"
            else -> null
        }
        val footprint=if(showMap && placing && target!=null) {
            val ring=(0 until 32).map {i -> val a=i*2*PI/32;Point2(preview!!.x+cos(a).toFloat()*target.margin,preview!!.z+sin(a).toFloat()*target.margin)}
            project(frame,target.plane,ring)
        } else emptyList()
        return RoomScanUi(message,target?.label ?: "Looking for a surface",
            tracking && target?.let { it.stable && it.safe && it.allowed }==true,
            tracking && target?.let { it.stable && it.safe && it.horizontal }==true,
            tracking,planes.size,outlines,stability.progress,detail,footprint,emptyList(),if(showMap) target?.patch ?: DepthPatch() else DepthPatch(),target?.depthVerified==true)
    }
    private fun polygon(plane: Plane): List<Point2> {
        val b=plane.polygon.duplicate(); b.rewind()
        val out=ArrayList<Point2>(b.remaining()/2)
        while(b.remaining()>=2) out.add(Point2(b.get(),b.get()))
        return out
    }
    private fun projectPoints(frame: Frame, plane: Plane, polygon: List<Point2>): List<ClipPoint> {
        val view=FloatArray(16); val projection=FloatArray(16); val vp=FloatArray(16)
        frame.camera.getViewMatrix(view,0); frame.camera.getProjectionMatrix(projection,0,0.05f,20f)
        Matrix.multiplyMM(vp,0,projection,0,view,0)
        return polygon.map { p ->
            val w=plane.centerPose.transformPoint(floatArrayOf(p.x,0f,p.z)); val clip=FloatArray(4)
            Matrix.multiplyMV(clip,0,vp,0,floatArrayOf(w[0],w[1],w[2],1f),0)
            ClipPoint(clip[0],clip[1],clip[2],clip[3])
        }
    }
    private fun project(frame: Frame, plane: Plane, points: List<Point2>) = clipSurface(projectPoints(frame,plane,points))
}
