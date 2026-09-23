package com.orbitar.nativeapp.room
import org.junit.Assert.*
import org.junit.Test

class RoomSurfaceMathTest {
    private val square=listOf(Point2(-0.5f,-0.5f),Point2(0.5f,-0.5f),Point2(0.5f,0.5f),Point2(-0.5f,0.5f))
    @Test fun floorsAndTablesNeedAnExplicitReference() {
        assertFalse(acceptsSurface(SurfaceMode.FLOOR,true,null))
        assertTrue(acceptsSurface(SurfaceMode.TABLE,true,null))
        assertTrue(acceptsSurface(SurfaceMode.AUTO,false,null))
        assertFalse(acceptsSurface(SurfaceMode.TABLE,false,null))
        assertTrue(acceptsSurface(SurfaceMode.AUTO,true,null))
        assertTrue(acceptsSurface(SurfaceMode.FLOOR,true,0.05f))
        assertFalse(acceptsSurface(SurfaceMode.TABLE,true,0.05f))
        assertTrue(acceptsSurface(SurfaceMode.TABLE,true,0.75f))
        assertFalse(acceptsSurface(SurfaceMode.FLOOR,true,0.75f))
        assertFalse(acceptsSurface(SurfaceMode.TABLE,false,0.75f))
        assertTrue(acceptsSurface(SurfaceMode.WALL,false,0.75f))
    }
    @Test fun squareTableRequiresSpaceInsideEveryEdge() {
        assertTrue(insideWithMargin(square,Point2(0f,0f),0.14f))
        assertFalse(insideWithMargin(square,Point2(0.45f,0f),0.14f))
        assertFalse(insideWithMargin(square,Point2(0.7f,0f),0.02f))
        assertTrue(insideWithMargin(square.reversed(),Point2(0f,0f),0.14f))
    }
    @Test fun boundingBoxDoesNotTurnUnmappedCornersIntoTable() {
        val diamond=listOf(Point2(0f,-0.5f),Point2(0.5f,0f),Point2(0f,0.5f),Point2(-0.5f,0f))
        assertFalse(insideWithMargin(diamond,Point2(0.4f,0.4f),0.02f))
        assertTrue(insideWithMargin(diamond,Point2(0f,0f),0.02f))
    }
    @Test fun largeObjectsNeedLargerSafeFootprints() {
        val small=footprintMargin(1f,true,false,1f)
        val large=footprintMargin(3f,true,false,1f)
        assertTrue(insideWithMargin(square,Point2(0f,0f),small))
        assertFalse(insideWithMargin(square,Point2(0f,0f),large))
        assertTrue(footprintMargin(1f,false,true,0.5f)>footprintMargin(1f,false,false,0.5f))
    }
    @Test fun jumpingTargetsAndLostTrackingMustReacquire() {
        val s=AimStability();val p=Point3(0f,0f,0f)
        assertFalse(s.update("table",p,1_000_000_000L))
        assertFalse(s.update("table",p,1_200_000_000L))
        assertTrue(s.update("table",p,1_400_000_000L))
        assertFalse(s.update("floor",p,1_500_000_000L))
        assertFalse(s.update("floor",Point3(0.1f,0f,0f),1_900_000_000L))
        s.update(null,null,2_000_000_000L)
        assertFalse(s.update("floor",p,2_500_000_000L))
    }

    @Test fun floorCrossingCameraIsClippedInsteadOfDiscarded() {
        val p=clipSurface(listOf(ClipPoint(-.5f,-.5f,0f,1f),ClipPoint(.5f,-.5f,0f,1f),
            ClipPoint(.5f,.5f,-2f,-1f),ClipPoint(-.5f,.5f,-2f,-1f)))
        assertTrue(p.size>=3)
        assertTrue(p.all {it.x.isFinite() && it.z.isFinite() && it.x in 0f..1f && it.z in 0f..1f})
        assertTrue(clipSurface(listOf(ClipPoint(-1f,0f,0f,-1f),ClipPoint(1f,0f,0f,-1f),ClipPoint(0f,1f,0f,-1f))).isEmpty())
    }
    @Test fun nearestEdgeIsOnTheSegmentAndWindingIndependent() {
        val point=Point2(.4f,.1f)
        val near=nearestBoundary(square,point)!!
        assertEquals(.5f,near.x,.0001f); assertEquals(.1f,near.z,.0001f)
        val edge=nearestBoundary(square.reversed(),Point2(.8f,.8f))!!
        assertEquals(.5f,edge.x,.0001f); assertEquals(.5f,edge.z,.0001f)
    }
    @Test fun holdProgressResetsOnTrackingLoss() {
        val s=AimStability();val p=Point3(0f,0f,0f)
        s.update("floor",p,1_000_000_000L)
        s.update("floor",p,1_175_000_000L)
        assertEquals(.5f,s.progress,.001f)
        s.update(null,null,1_200_000_000L)
        assertEquals(0f,s.progress,.001f)
    }
}
