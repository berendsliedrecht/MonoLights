@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.monoapps.monolights.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monoapps.monolights.LightsViewModel
import com.monoapps.monolights.api.HueLight
import com.mudita.mmd.components.bottom_sheet.ModalBottomSheetMMD
import com.mudita.mmd.components.bottom_sheet.rememberModalBottomSheetMMDState
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import kotlin.math.roundToInt

private fun percent(bri: Int) = (bri / 254f * 100).roundToInt()

@Composable
fun LightsTab(viewModel: LightsViewModel, modifier: Modifier) {
    val lights = viewModel.lights
    if (lights.isEmpty()) {
        Centered(modifier) { TextMMD("No lights found on the bridge.", fontSize = 16.sp) }
        return
    }

    // Light whose brightness sheet is open.
    var adjusting by remember { mutableStateOf<String?>(null) }

    val roomedIds = viewModel.rooms.flatMap { it.second.lights }.toSet()
    val other = lights.filterKeys { it !in roomedIds }.toList()

    LazyColumnMMD(modifier = modifier.padding(horizontal = 16.dp)) {
        viewModel.rooms.forEach { (roomId, room) ->
            val roomLights = room.lights.mapNotNull { id -> lights[id]?.let { id to it } }
            if (roomLights.isEmpty()) return@forEach
            item(key = "room-$roomId") {
                RoomHeader(
                    name = room.name,
                    anyOn = room.state.anyOn,
                    onToggle = { viewModel.toggleRoom(roomId) },
                )
            }
            tileGrid(roomLights, viewModel, onAdjust = { adjusting = it })
        }
        if (other.isNotEmpty()) {
            item(key = "room-other") {
                RoomHeader(name = "Other", anyOn = false, onToggle = null)
            }
            tileGrid(other, viewModel, onAdjust = { adjusting = it })
        }
        item(key = "bottom-space") { Spacer(Modifier.height(16.dp)) }
    }

    adjusting?.let { id ->
        lights[id]?.let { light ->
            BrightnessSheet(
                name = light.name,
                percent = if (light.state.on) percent(light.state.bri) else 0,
                onDismiss = { adjusting = null },
                onSet = { viewModel.setBrightness(id, (it / 100f * 254).roundToInt()) },
            )
        }
    }
}

/** Two tiles per row, matching the Kompakt's 480px width. */
private fun androidx.compose.foundation.lazy.LazyListScope.tileGrid(
    roomLights: List<Pair<String, HueLight>>,
    viewModel: LightsViewModel,
    onAdjust: (String) -> Unit,
) {
    items(roomLights.chunked(2), key = { row -> row.joinToString("-") { it.first } }) { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.forEach { (id, light) ->
                LightTile(
                    light = light,
                    modifier = Modifier.weight(1f),
                    onToggle = { viewModel.toggleLight(id) },
                    onAdjust = { onAdjust(id) },
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RoomHeader(name: String, anyOn: Boolean, onToggle: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextMMD(
            name,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (onToggle != null) {
            OutlinedButtonMMD(onClick = onToggle) {
                TextMMD(if (anyOn) "All off" else "All on", fontSize = 14.sp)
            }
        }
    }
}

/** On = inverted (black tile, white text). Tap toggles, long-press adjusts brightness. */
@Composable
private fun LightTile(
    light: HueLight,
    modifier: Modifier,
    onToggle: () -> Unit,
    onAdjust: () -> Unit,
) {
    val on = light.state.on
    val reachable = light.state.reachable
    val shape = RoundedCornerShape(12.dp)
    val foreground = if (on) Color.White else Color.Black

    Column(
        modifier = modifier
            .height(96.dp)
            .clip(shape)
            .background(if (on) Color.Black else Color.White)
            .border(2.dp, Color.Black, shape)
            .then(
                if (reachable) {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        // E-ink: no ripple.
                        indication = null,
                        onClick = onToggle,
                        onLongClick = onAdjust,
                    )
                } else {
                    Modifier
                }
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        TextMMD(
            light.name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = foreground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        TextMMD(
            when {
                !reachable -> "Offline"
                on -> "${percent(light.state.bri)}%"
                else -> "Off"
            },
            fontSize = 14.sp,
            color = foreground,
        )
    }
}

@Composable
private fun BrightnessSheet(
    name: String,
    percent: Int,
    onDismiss: () -> Unit,
    onSet: (Int) -> Unit,
) {
    ModalBottomSheetMMD(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetMMDState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            TextMMD(name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButtonMMD(onClick = { onSet((percent - 10).coerceAtLeast(10)) }) {
                    TextMMD("−", fontSize = 20.sp)
                }
                TextMMD(
                    "$percent%",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButtonMMD(onClick = { onSet((percent + 10).coerceAtMost(100)) }) {
                    TextMMD("+", fontSize = 20.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(25, 50, 75, 100).forEach { preset ->
                    OutlinedButtonMMD(
                        onClick = { onSet(preset) },
                        modifier = Modifier.weight(1f),
                    ) {
                        TextMMD("$preset%", fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            ButtonMMD(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                TextMMD("Done")
            }
        }
    }
}
