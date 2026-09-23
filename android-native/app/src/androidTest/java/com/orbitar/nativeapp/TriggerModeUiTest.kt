package com.orbitar.nativeapp

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TriggerModeUiTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun hybridDefaultAndRealignRequiresVisibleTrigger() {
        var hybrid by mutableStateOf(true)
        var visible by mutableStateOf(false)
        var realign=false
        rule.activity.runOnUiThread {rule.activity.setContent {MaterialTheme {
            TriggerModeControls(hybrid,visible,{hybrid=it},{realign=true})
        }}}
        rule.onNodeWithTag("hybrid-mode").assertIsSelected()
        rule.onNodeWithTag("realign-trigger").assertIsNotEnabled()
        rule.runOnIdle {visible=true}
        rule.onNodeWithTag("realign-trigger").performClick()
        rule.runOnIdle {assertTrue(realign)}
        rule.onNodeWithTag("follow-mode").performClick().assertIsSelected()
        rule.onNodeWithTag("realign-trigger").assertDoesNotExist()
    }
}
