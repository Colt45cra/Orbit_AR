package com.orbitar.nativeapp

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.orbitar.nativeapp.room.RoomTransformControls
import com.orbitar.nativeapp.room.RoomScanControls
import com.orbitar.nativeapp.room.SurfaceMode
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RoomControlsTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun resizingDoesNotChangeHeightAndGroundButtonOnlyChangesHeight() {
        var scale by mutableFloatStateOf(1f)
        var elevation by mutableFloatStateOf(0.2f)
        var rotation by mutableFloatStateOf(0f)
        rule.activity.runOnUiThread {rule.activity.setContent {MaterialTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                RoomTransformControls(scale,elevation,rotation,false,true,{scale=it},{elevation=it},{rotation=it},{})
            }
        }}}
        rule.onNodeWithTag("room-size").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(2f)}
        rule.runOnIdle {assertEquals(2f,scale,0.01f);assertEquals(0.2f,elevation,0.001f)}
        rule.onNodeWithTag("room-height").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(0.5f)}
        rule.runOnIdle {assertEquals(2f,scale,0.01f);assertEquals(0.5f,elevation,0.001f)}
        rule.onNodeWithTag("room-ground").performScrollTo().performClick()
        rule.runOnIdle {assertEquals(0f,elevation,0.001f);assertEquals(2f,scale,0.01f)}
        rule.onNodeWithTag("room-lower").performScrollTo().performClick()
        rule.runOnIdle {assertEquals(-0.01f,elevation,0.001f);assertEquals(2f,scale,0.01f)}
        rule.onNodeWithTag("room-height").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(-0.25f)}
        rule.runOnIdle {assertEquals(-0.25f,elevation,0.001f)}
        rule.onNodeWithTag("room-raise").performScrollTo().performClick()
        rule.runOnIdle {assertEquals(-0.24f,elevation,0.001f)}
    }

    @Test fun surfaceModeAndFloorSetupAreDirectlyAccessible() {
        var mode by mutableStateOf(SurfaceMode.AUTO)
        var ready by mutableStateOf(false)
        var setFloor=false
        rule.activity.runOnUiThread {rule.activity.setContent {MaterialTheme {
            RoomScanControls(mode,false,ready,{mode=it},{setFloor=true})
        }}}
        rule.onNodeWithTag("surface-TABLE").performClick().assertIsSelected()
        rule.onNodeWithTag("set-floor").assertDoesNotExist()
        rule.onNodeWithTag("surface-FLOOR").performClick().assertIsSelected()
        rule.onNodeWithTag("set-floor").assertIsDisplayed().assertIsNotEnabled()
        rule.runOnIdle {ready=true}
        rule.onNodeWithTag("set-floor").performClick()
        rule.runOnIdle {assertTrue(setFloor);assertEquals(SurfaceMode.FLOOR,mode)}
    }
}
