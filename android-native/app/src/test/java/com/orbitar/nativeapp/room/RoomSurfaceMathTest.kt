package com.orbitar.nativeapp.room
import org.junit.Assert.*
import org.junit.Test

class RoomSurfaceMathTest {
    private val square=listOf(Point2(-0.5f,-0.5f),Point2(0.5f,-0.5f),Point2(0.5f,0.5f),Point2(-0.5f,0.5f))
    @Test fun floorsAndTablesNeedAnExplicitReference() {
        assertFalse(acceptsSurface(SurfaceMode.FLOOR,true,null))
        assertFalse(acceptsSurface(SurfaceMode.TABLE,true,null))
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
}
