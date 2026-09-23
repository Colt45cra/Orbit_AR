package com.orbitar.nativeapp.room

import android.media.Image
import android.opengl.Matrix
import com.google.ar.core.*
import java.nio.ByteOrder
import kotlin.math.*

// One acquisition per scan update. Never retain the Frame or the native Image across frames.
internal class RoomDepthProbe private constructor(private val frame: Frame,private val image: Image): AutoCloseable {
    private val plane=image.planes[0]
    private val buffer=plane.buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    private val inverseProjection=FloatArray(16)
    private val projection=FloatArray(16)
    private val cameraInverse=frame.camera.displayOrientedPose.inverse()
    private val uvBasis=FloatArray(6)
    init {frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,floatArrayOf(0f,0f,1f,0f,0f,1f),Coordinates2d.TEXTURE_NORMALIZED,uvBasis)
        frame.camera.getProjectionMatrix(projection,0,.05f,20f);Matrix.invertM(inverseProjection,0,projection,0)}
    override fun close()=image.close()
    fun depth(x:Float,y:Float):Float? {
        if(x !in 0f..1f || y !in 0f..1f) return null
        val nx=x*2-1;val ny=1-y*2
        val uv=floatArrayOf(uvBasis[0]+nx*(uvBasis[2]-uvBasis[0])+ny*(uvBasis[4]-uvBasis[0]),uvBasis[1]+nx*(uvBasis[3]-uvBasis[1])+ny*(uvBasis[5]-uvBasis[1]))
        if(uv.any {!it.isFinite() || it !in 0f..1f}) return null
        val px=(uv[0]*image.width).toInt().coerceIn(0,image.width-1)
        val py=(uv[1]*image.height).toInt().coerceIn(0,image.height-1)
        val offset=py*plane.rowStride+px*plane.pixelStride
        if(offset+1>=buffer.limit()) return null
        val value=(buffer.getShort(offset).toInt() and 0xffff)/1000f
        return value.takeIf {it in .2f..5f}
    }
    fun agreesAtAim(pose:Pose):Boolean? {
        val d=depth(.5f,.40f) ?: return null
        val expected=-cameraInverse.compose(pose).tz()
        return depthAgrees(d,expected)
    }
    fun supports(world:FloatArray):Boolean? {
        val p=cameraInverse.transformPoint(world);val clip=FloatArray(4)
        Matrix.multiplyMV(clip,0,projection,0,floatArrayOf(p[0],p[1],p[2],1f),0)
        if(clip[3]<=.05f) return false
        val d=depth((clip[0]/clip[3]+1)/2,(1-clip[1]/clip[3])/2) ?: return null
        return depthAgrees(d,-p[2])
    }
    fun patch(surface:Plane):DepthPatch {
        val width=40;val height=60
        val pose=cameraInverse.compose(surface.centerPose)
        val n=pose.getTransformedAxis(1,1f)
        val offset=n[0]*pose.tx()+n[1]*pose.ty()+n[2]*pose.tz()
        val matches=BooleanArray(width*height);val measured=BooleanArray(matches.size)
        for(y in 0 until height) for(x in 0 until width) {
            val sx=(x+.5f)/width;val sy=(y+.5f)/height;val i=y*width+x
            val d=depth(sx,sy) ?: continue;measured[i]=true
            val ray=FloatArray(4)
            Matrix.multiplyMV(ray,0,inverseProjection,0,floatArrayOf(sx*2-1,1-sy*2,1f,1f),0)
            val dot=n[0]*ray[0]+n[1]*ray[1]+n[2]*ray[2]
            if(abs(dot)<.00001f) continue
            val expected=-ray[2]*offset/dot
            matches[i]=depthAgrees(d,expected)
        }
        val seed=(.40f*height).toInt()*width+width/2
        return patchGeometry(connectedPatch(matches,width,seed),measured,width)
    }
    companion object {
        fun acquire(frame:Frame):RoomDepthProbe? {
            val image=try {frame.acquireDepthImage16Bits()} catch(_:com.google.ar.core.exceptions.NotYetAvailableException) {return null}
                catch(_:com.google.ar.core.exceptions.NotTrackingException) {return null}
                catch(_:IllegalStateException) {return null}
            // Old depth must not be compared to a camera pose from a different moment.
            if(abs(frame.timestamp-image.timestamp)>150_000_000L) {image.close();return null}
            return try {RoomDepthProbe(frame,image)} catch(e:Exception) {image.close();throw e}
        }
    }
}
