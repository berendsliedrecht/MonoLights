@file:OptIn(ExperimentalFoundationApi::class)

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monoapps.monolights.LightsViewModel
import com.monoapps.monolights.api.HueLight
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import kotlin.math.roundToInt

private fun percent(bri: Int) = (bri / 254f * 100).roundToInt()

@Composable
fun LightsTab(viewModel: LightsViewModel, modifier: Modifier, onAdjust: (Adjusting) -> Unit) {
    val lights = viewModel.lights
    if (lights.isEmpty()) {
        Centered(modifier) { TextMMD("No lights found on the bridge.", fontSize = 16.sp) }
        return
    }

    val roomedIds = viewModel.rooms.flatMap { it.second.lights }.toSet()
    val other = lights.filterKeys { it !in roomedIds }.toList()

    LazyColumnMMD(modifier = modifier.padding(horizontal = 16.dp)) {
        viewModel.rooms.forEach { (roomId, room) ->
            val roomLights = room.lights.mapNotNull { id -> lights[id]?.let { id to it } }
            if (roomLights.isEmpty()) return@forEach
            item(key = "room-$roomId") {
                RoomHeader(name = room.name, onEdit = { onAdjust(Adjusting.Room(roomId)) })
            }
            tileGrid(roomLights, viewModel, onAdjust = { onAdjust(Adjusting.Light(it)) })
        }
        if (other.isNotEmpty()) {
            item(key = "room-other") {
                RoomHeader(name = "Other", onEdit = null)
            }
            tileGrid(other, viewModel, onAdjust = { onAdjust(Adjusting.Light(it)) })
        }
        item(key = "bottom-space") { Spacer(Modifier.height(16.dp)) }
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

/** Edit opens the room-wide adjust screen (brightness, color, all on/off). */
@Composable
private fun RoomHeader(name: String, onEdit: (() -> Unit)?) {
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
        if (onEdit != null) {
            OutlinedButtonMMD(onClick = onEdit) {
                TextMMD("Edit", fontSize = 14.sp)
            }
        }
    }
}

/** On = inverted (black tile, white text). Tap toggles, long-press adjusts. */
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
                on -> listOfNotNull("${percent(light.state.bri)}%", colorLabel(light.state))
                    .joinToString(" · ")
                else -> "Off"
            },
            fontSize = 14.sp,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
