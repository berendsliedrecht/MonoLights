@file:OptIn(ExperimentalMaterial3Api::class)

package com.monoapps.monolights.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.monoapps.monolights.api.LightState
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.snackbar.SnackbarHostMMD
import com.mudita.mmd.components.snackbar.SnackbarHostStateMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import kotlin.math.roundToInt

/** What the adjust screen is pointed at. */
sealed interface Adjusting {
    data class Light(val id: String) : Adjusting
    data class Room(val id: String) : Adjusting
}

private fun percent(bri: Int) = (bri / 254f * 100).roundToInt()

/**
 * Full-screen editor for one light or a whole room: on/off in the top bar,
 * brightness, and named color / white tone pickers. Sized to fit the
 * Kompakt's 360x601dp page without scrolling.
 */
@Composable
fun AdjustScreen(
    viewModel: LightsViewModel,
    target: Adjusting,
    snackbarHostState: SnackbarHostStateMMD,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val name: String
    val state: LightState
    val supportsColor: Boolean
    val supportsWhite: Boolean
    val toggleLabel: String
    val onToggle: () -> Unit
    val onSetPercent: (Int) -> Unit
    val onSetColor: (NamedColor) -> Unit
    val onSetWhite: (WhitePreset) -> Unit

    when (target) {
        is Adjusting.Light -> {
            val light = viewModel.lights[target.id]
            if (light == null) {
                // The light vanished from the bridge (e.g. after a refresh).
                LaunchedEffect(Unit) { onBack() }
                return
            }
            name = light.name
            state = light.state
            supportsColor = state.hue != null
            supportsWhite = state.ct != null
            toggleLabel = if (state.on) "Turn off" else "Turn on"
            onToggle = { viewModel.toggleLight(target.id) }
            onSetPercent = { viewModel.setBrightness(target.id, (it / 100f * 254).roundToInt()) }
            onSetColor = { viewModel.setColor(target.id, it.hue, 254) }
            onSetWhite = { viewModel.setWhite(target.id, it.ct) }
        }
        is Adjusting.Room -> {
            val room = viewModel.rooms.toMap()[target.id]
            if (room == null) {
                LaunchedEffect(Unit) { onBack() }
                return
            }
            val members = room.lights.mapNotNull { viewModel.lights[it] }
            name = room.name
            state = room.action.copy(on = room.state.anyOn)
            supportsColor = members.any { it.state.hue != null }
            supportsWhite = members.any { it.state.ct != null }
            toggleLabel = if (room.state.anyOn) "All off" else "All on"
            onToggle = { viewModel.toggleRoom(target.id) }
            onSetPercent = { viewModel.setRoomBrightness(target.id, (it / 100f * 254).roundToInt()) }
            onSetColor = { viewModel.setRoomColor(target.id, it.hue, 254) }
            onSetWhite = { viewModel.setRoomWhite(target.id, it.ct) }
        }
    }

    val pct = if (state.on) percent(state.bri) else 0
    val selectedColor =
        if (state.on && state.colormode != "ct" && state.hue != null && (state.sat ?: 0) >= 50)
            nearestColor(state.hue).name
        else null
    val selectedWhite =
        if (state.on && state.colormode == "ct" && state.ct != null) nearestWhite(state.ct).name
        else null

    Scaffold(
        containerColor = Color.White,
        snackbarHost = { SnackbarHostMMD(hostState = snackbarHostState) },
        topBar = {
            TopAppBarMMD(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                title = {
                    TextMMD(
                        name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    OutlinedButtonMMD(
                        onClick = onToggle,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        TextMMD(toggleLabel, fontSize = 14.sp)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButtonMMD(onClick = { onSetPercent((pct - 10).coerceAtLeast(10)) }) {
                    TextMMD("−", fontSize = 20.sp)
                }
                TextMMD(
                    "$pct%",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButtonMMD(onClick = { onSetPercent((pct + 10).coerceAtMost(100)) }) {
                    TextMMD("+", fontSize = 20.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(25, 50, 75, 100).forEach { preset ->
                    OutlinedButtonMMD(
                        onClick = { onSetPercent(preset) },
                        modifier = Modifier.weight(1f),
                    ) {
                        TextMMD("$preset%", fontSize = 14.sp)
                    }
                }
            }
            if (supportsColor) {
                Spacer(Modifier.height(16.dp))
                TextMMD("Color", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ChipGrid(
                    labels = HUE_COLORS.map { it.name },
                    selected = selectedColor,
                    onPick = { onSetColor(HUE_COLORS[it]) },
                )
            }
            if (supportsWhite) {
                Spacer(Modifier.height(12.dp))
                TextMMD("White", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ChipGrid(
                    labels = WHITE_PRESETS.map { it.name },
                    selected = selectedWhite,
                    onPick = { onSetWhite(WHITE_PRESETS[it]) },
                )
            }
        }
    }
}

/** Three text chips per row; the active one is inverted. */
@Composable
private fun ChipGrid(labels: List<String>, selected: String?, onPick: (Int) -> Unit) {
    labels.chunked(3).forEachIndexed { rowIndex, row ->
        if (rowIndex > 0) Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            row.forEachIndexed { colIndex, label ->
                Chip(
                    label = label,
                    selected = label == selected,
                    onClick = { onPick(rowIndex * 3 + colIndex) },
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(if (selected) Color.Black else Color.White)
            .border(2.dp, Color.Black, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                // E-ink: no ripple.
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(
            label,
            fontSize = 14.sp,
            color = if (selected) Color.White else Color.Black,
            maxLines = 1,
        )
    }
}
