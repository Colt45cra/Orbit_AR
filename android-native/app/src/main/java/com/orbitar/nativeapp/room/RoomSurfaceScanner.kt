package com.orbitar.nativeapp.room

import android.opengl.Matrix
import com.google.ar.core.*
import kotlin.math.*
import java.util.Locale

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
    val edgeGuide: List<Point2> = emptyList()
)
internal data class RoomTarget(val hit: HitResult, val plane: Plane, val horizontal: Boolean, val stable: Boolean,
    val allowed: Boolean, val safe: Boolean, val label: String, val localPoint: Point2, val polygon: List<Point2>, val margin: Float)

internal class RoomSurfaceScanner {
    private val stability = AimStability()
    fun reset() = stability.reset()
    fun target(frame: Frame, width: Int, height: Int, mode: SurfaceMode, floorY: Float?, margin: Float): RoomTarget? {
        if(frame.camera.trackingState != TrackingState.TRACKING || width<=0 || height<=0) { stability.reset(); return null }
        // Select the nearest real polygon FIRST. Never skip a tabletop to place on a floor behind it.
        val hit=frame.hitTest(width*0.5f,height*0.40f).firstOrNull { h ->
            val p=h.trackable as? Plane
            p!=null && p.subsumedBy==null && p.trackingState==TrackingState.TRACKING &&
                p.isPoseInPolygon(h.hitPose) && p.type!=Plane.Type.HORIZONTAL_DOWNWARD_FACING
        } ?: run { stability.reset(); return null }
        val plane=hit.trackable as Plane
        val horizontal=plane.type==Plane.Type.HORIZONTAL_UPWARD_FACING
        val polygon=polygon(plane)
        val local=plane.centerPose.inverse().compose(hit.hitPose)
        val point=Point2(local.tx(),local.tz())
        val pose=hit.hitPose
        val normal=plane.centerPose.getTransformedAxis(1,1f)
        val camera=frame.camera.pose
        val front=(camera.tx()-pose.tx())*normal[0]+(camera.ty()-pose.ty())*normal[1]+(camera.tz()-pose.tz())*normal[2]>0
        val safe=front && hit.distance in 0.2f..5f && plane.extentX>=0.10f && plane.extentZ>=0.10f && insideWithMargin(polygon,point,margin)
        val stable=stability.update(if(safe) plane else null,if(safe) Point3(pose.tx(),pose.ty(),pose.tz()) else null,frame.timestamp)
        val above=floorY?.let { pose.ty()-it }
        return RoomTarget(hit,plane,horizontal,stable,acceptsSurface(mode,horizontal,above),safe,
            surfaceLabel(horizontal,above),point,polygon,margin)
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
            (mode==SurfaceMode.FLOOR || mode==SurfaceMode.TABLE) && floorY==null -> "Aim at the floor, hold steady, then tap Set floor"
            target==null -> "Scan the surface from a few angles, including its corners"
            !target.allowed -> "Aim at ${mode.label.lowercase()} — the nearer surface blocks placement"
            !target.safe -> "Move away from the edge or scan more of this surface"
            !target.stable -> "Hold steady to confirm the surface"
            else -> if(placing) "Surface ready — tap Place here" else "Surface ready — add an image or 3D object"
        }
        val outlines=if(showMap && tracking) outlines(frame,planes.sortedBy { if(it==target?.plane) 0 else 1 }.take(16),target?.plane) else emptyList()
        val edge=target?.let { nearestBoundary(it.polygon,it.localPoint) }
        val detail=target?.let { t ->
            val distance=edge?.let {hypot(it.x-t.localPoint.x,it.z-t.localPoint.z)} ?: 0f
            String.format(Locale.US,"Mapped %.2f × %.2f m · %.0f cm from boundary",t.plane.extentX,t.plane.extentZ,distance*100)
        }
        val footprint=if(showMap && placing && target!=null) {
            val ring=(0 until 32).map {i -> val a=i*2*PI/32;Point2(target.localPoint.x+cos(a).toFloat()*target.margin,target.localPoint.z+sin(a).toFloat()*target.margin)}
            project(frame,target.plane,ring)
        } else emptyList()
        // A short guide points toward the nearest mapped boundary, not an inferred physical edge.
        val guide=if(showMap && target!=null && edge!=null) {
            val pair=projectPoints(frame,target.plane,listOf(target.localPoint,edge))
            if(pair.all {it.w>0.05f}) pair.map {Point2((it.x/it.w+1f)/2f,(1f-it.y/it.w)/2f)} else emptyList()
        } else emptyList()
        return RoomScanUi(message,target?.label ?: "Looking for a surface",
            tracking && target?.let { it.stable && it.safe && it.allowed }==true,
            tracking && target?.let { it.stable && it.safe && it.horizontal }==true,
            tracking,planes.size,outlines,stability.progress,detail,footprint,guide)
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
    private fun outlines(frame: Frame, planes: List<Plane>, selected: Plane?): List<SurfaceOutline> =
        planes.mapNotNull { plane ->
            val points=project(frame,plane,polygon(plane))
            if(points.size<3) null else SurfaceOutline(points,plane==selected)
        }
}
