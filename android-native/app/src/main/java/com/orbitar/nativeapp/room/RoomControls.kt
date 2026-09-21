package com.orbitar.nativeapp.room

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
internal fun RoomTransformControls(scale: Float, elevation: Float, rotation: Float, flat: Boolean, isImage: Boolean,
    onScale: (Float)->Unit, onElevation: (Float)->Unit, onRotation: (Float)->Unit, onFlat: (Boolean)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        RoomSlider("Height correction",elevation,-1f..1f,"${(elevation*100).toInt()} cm","room-height",onElevation)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick={onElevation((elevation-0.01f).coerceAtLeast(-1f))},modifier=Modifier.weight(1f).testTag("room-lower")) {Text("Lower")}
            TextButton(onClick={onElevation(0f)},modifier=Modifier.testTag("room-ground")) { Text("Reset") }
            OutlinedButton(onClick={onElevation((elevation+0.01f).coerceAtMost(1f))},modifier=Modifier.weight(1f).testTag("room-raise")) {Text("Raise")}
        }
        Text("Lower / Raise: 1 cm. Use negative height if the image floats above the real surface.",style=MaterialTheme.typography.bodySmall)
        RoomSlider("Size",scale,0.25f..3f,String.format(Locale.US,"%.2f×",scale),"room-size",onScale)
        RoomSlider("Rotate",rotation,-180f..180f,"${rotation.toInt()}°","room-rotation",onRotation)
        if(isImage) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=!flat,onClick={onFlat(false)},label={Text("Stand upright")})
            FilterChip(selected=flat,onClick={onFlat(true)},label={Text("Lay flat")})
        }
    }
}
@Composable
private fun RoomSlider(label: String,value: Float,range: ClosedFloatingPointRange<Float>,text: String,tag: String,onChange:(Float)->Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) { Text(label,Modifier.weight(1f));Text(text,fontWeight=FontWeight.Bold) }
        Slider(value=value,onValueChange=onChange,valueRange=range,modifier=Modifier.testTag(tag))
    }
}
