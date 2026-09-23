package com.orbitar.nativeapp.room

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun RoomScanControls(mode: SurfaceMode, hasFloor: Boolean, canSetFloor: Boolean,
    onMode: (SurfaceMode)->Unit, onSetFloor: ()->Unit) {
    Column {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SurfaceMode.entries.forEach { option ->
                FilterChip(selected=mode==option,onClick={onMode(option)},
                    modifier=Modifier.testTag("surface-${option.name}"),label={Text(option.label)})
            }
        }
        if(mode==SurfaceMode.FLOOR) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(if(hasFloor) "Floor reference set" else "Aim at the actual floor to calibrate",
                    modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick=onSetFloor,enabled=canSetFloor,modifier=Modifier.testTag("set-floor")) {
                    Text(if(hasFloor) "Reset floor" else "Set floor")
                }
            }
        }
    }
}
