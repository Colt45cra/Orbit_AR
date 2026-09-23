package com.orbitar.nativeapp.room

import org.junit.Assert.*
import org.junit.Test

class RoomDepthMathTest {
    @Test fun rejectsPlaneBehindTableAndFloatingPlaneInFront() {
        assertFalse(depthAgrees(1f,1.75f))
        assertFalse(depthAgrees(1.75f,1f))
        assertTrue(depthAgrees(1.02f,1f))
        assertFalse(depthAgrees(0f,1f))
        assertFalse(depthAgrees(Float.NaN,1f))
    }
    @Test fun onlySelectedConnectedSurfaceIsShown() {
        val mask=booleanArrayOf(true,true,false,true,true, true,true,false,true,true)
        val result=connectedPatch(mask,5,0)
        assertEquals(4,result.count {it})
        assertFalse(result[3]);assertFalse(result[4]);assertFalse(result[9])
    }
    @Test fun connectivityDoesNotWrapAcrossRows() {
        val mask=booleanArrayOf(false,false,true,true,false,false)
        assertEquals(1,connectedPatch(mask,3,2).count {it})
        assertEquals(0,connectedPatch(mask,3,0).count {it})
    }
    @Test fun gapsAndMissingDepthAreNotFilledIn() {
        val mask=BooleanArray(9) {it!=4}
        val patch=patchGeometry(connectedPatch(mask,3,0),BooleanArray(9),3)
        assertEquals(8,patch.cells.size)
        assertEquals(16,patch.edges.size)
        assertTrue(patch.edges.none {it.supported})
        val measured=BooleanArray(9) {true}
        val measuredPatch=patchGeometry(mask,measured,3)
        assertEquals(4,measuredPatch.edges.count {it.supported})
    }
}
