package com.orbitar.nativeapp.room

import kotlin.math.*

enum class SurfaceMode(val label: String) { AUTO("Any"), FLOOR("Floor"), TABLE("Tabletop"), WALL("Wall") }
internal data class Point2(val x: Float, val z: Float)
internal data class Point3(val x: Float, val y: Float, val z: Float)

// A floor reference is explicit: the lowest plane seen may itself be a table.
internal fun surfaceLabel(horizontal: Boolean, heightAboveFloor: Float?): String = when {
    !horizontal -> "Wall"
    heightAboveFloor == null -> "Horizontal surface"
    abs(heightAboveFloor) <= 0.15f -> "Floor"
    heightAboveFloor in 0.30f..1.50f -> "Table-height surface"
    else -> "Raised surface"
}
internal fun acceptsSurface(mode: SurfaceMode, horizontal: Boolean, heightAboveFloor: Float?): Boolean = when(mode) {
    SurfaceMode.AUTO -> horizontal
    SurfaceMode.FLOOR -> horizontal && heightAboveFloor != null && abs(heightAboveFloor) <= 0.15f
    SurfaceMode.TABLE -> horizontal && heightAboveFloor != null && heightAboveFloor in 0.30f..1.50f
    SurfaceMode.WALL -> !horizontal
}

// ARCore exposes a convex observed polygon. Do not substitute its bounding rectangle:
// that would permit anchors beyond observed tabletop edges and inside missing corners.
internal fun insideWithMargin(polygon: List<Point2>, point: Point2, margin: Float): Boolean {
    if (polygon.size < 3) return false
    var sign = 0
    var area = 0f
    for (i in polygon.indices) {
        val a=polygon[i]; val b=polygon[(i+1)%polygon.size]
        val dx=b.x-a.x; val dz=b.z-a.z
        val length=hypot(dx,dz)
        if (length < 0.0001f) continue
        val cross=dx*(point.z-a.z)-dz*(point.x-a.x)
        if (abs(cross)/length < margin) return false
        if (abs(cross)>0.00001f) {
            val side=if(cross>0) 1 else -1
            if(sign!=0 && sign!=side) return false
            sign=side
        }
        area+=a.x*b.z-b.x*a.z
    }
    return abs(area)>0.0001f
}

internal class AimStability(private val holdNanos: Long = 350_000_000L) {
    private var surface: Any? = null
    private var origin: Point3? = null
    private var since = 0L
    var progress: Float = 0f
        private set
    fun reset() { surface=null; origin=null; since=0; progress=0f }
    fun update(id: Any?, position: Point3?, timestamp: Long): Boolean {
        if(id==null || position==null) { reset(); return false }
        val old=origin
        val moved=old==null || sqrt((position.x-old.x).pow(2)+(position.y-old.y).pow(2)+(position.z-old.z).pow(2))>0.035f
        if(surface!=id || moved || timestamp<since) { surface=id; origin=position; since=timestamp; progress=0f; return false }
        progress=((timestamp-since).toFloat()/holdNanos).coerceIn(0f,1f)
        return progress >= 1f
    }
}
internal const val IMAGE_BASE_WIDTH = 0.24f
internal const val MODEL_BASE_SIZE = 0.28f
internal fun footprintMargin(scale: Float, model: Boolean, flat: Boolean, aspect: Float): Float =
    0.02f + if(model) MODEL_BASE_SIZE*scale*0.7072f
    else if(flat) hypot(IMAGE_BASE_WIDTH*scale/2, IMAGE_BASE_WIDTH*scale/(2*aspect.coerceAtLeast(0.01f)))
    else IMAGE_BASE_WIDTH*scale/2

// Clip before perspective division so a floor spanning behind the camera remains visible.
internal data class ClipPoint(val x: Float, val y: Float, val z: Float, val w: Float)
internal fun clipSurface(input: List<ClipPoint>): List<Point2> {
    var points=input
    val sides: List<(ClipPoint)->Float> = listOf(
        {it.w-0.001f}, {it.x+it.w}, {it.w-it.x},
        {it.y+it.w}, {it.w-it.y}, {it.z+it.w}, {it.w-it.z})
    for(side in sides) {
        if(points.isEmpty()) return emptyList()
        val out=ArrayList<ClipPoint>()
        var a=points.last(); var da=side(a)
        for(b in points) {
            val db=side(b)
            if((da>=0f)!=(db>=0f)) {
                val t=da/(da-db)
                out.add(ClipPoint(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.z+(b.z-a.z)*t,a.w+(b.w-a.w)*t))
            }
            if(db>=0f) out.add(b)
            a=b; da=db
        }
        points=out
    }
    return points.map {Point2((it.x/it.w+1f)/2f,(1f-it.y/it.w)/2f)}
}
internal fun nearestBoundary(polygon: List<Point2>, point: Point2): Point2? = polygon.indices.map { i ->
    val a=polygon[i]; val b=polygon[(i+1)%polygon.size]
    val dx=b.x-a.x; val dz=b.z-a.z; val length=dx*dx+dz*dz
    val t=if(length<0.000001f) 0f else (((point.x-a.x)*dx+(point.z-a.z)*dz)/length).coerceIn(0f,1f)
    Point2(a.x+t*dx,a.z+t*dz)
}.minByOrNull {hypot(it.x-point.x,it.z-point.z)}
