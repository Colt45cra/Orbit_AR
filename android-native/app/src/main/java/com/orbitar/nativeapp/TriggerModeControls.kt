package com.orbitar.nativeapp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun TriggerModeControls(anchorInRoom:Boolean,canRealign:Boolean,onMode:(Boolean)->Unit,onRealign:()->Unit) {
    Column {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=anchorInRoom,onClick={onMode(true)},label={Text("Anchor in room")},modifier=Modifier.testTag("hybrid-mode"))
            FilterChip(selected=!anchorInRoom,onClick={onMode(false)},label={Text("Follow trigger")},modifier=Modifier.testTag("follow-mode"))
        }
        if(anchorInRoom) OutlinedButton(onClick=onRealign,enabled=canRealign,modifier=Modifier.fillMaxWidth().testTag("realign-trigger")) {
            Text(if(canRealign) "Realign to trigger" else "Show trigger to realign")
        }
    }
}
