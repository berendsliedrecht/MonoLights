@file:OptIn(ExperimentalMaterial3Api::class)

package com.monoapps.monolights.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monoapps.monolights.LightsViewModel
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD

@Composable
fun SetupScreen(viewModel: LightsViewModel) {
    LaunchedEffect(Unit) { viewModel.startDiscovery() }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD("MonoLights", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
            )
        },
    ) { padding ->
        if (viewModel.pairing) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                TextMMD(
                    "Press the round link button on your Hue Bridge",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicatorMMD()
                Spacer(Modifier.height(24.dp))
                OutlinedButtonMMD(onClick = { viewModel.cancelPairing() }) {
                    TextMMD("Cancel")
                }
            }
        } else {
            Discovery(viewModel, Modifier.padding(padding))
        }
    }
}

@Composable
private fun Discovery(viewModel: LightsViewModel, modifier: Modifier) {
    var manualIp by remember { mutableStateOf("") }

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        TextMMD("Connect your Hue Bridge", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        TextMMD(
            "Looking for bridges on your Wi-Fi network. Make sure the bridge is powered on and on the same network.",
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(16.dp))

        if (viewModel.foundBridges.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicatorMMD()
                Spacer(Modifier.width(12.dp))
                TextMMD("Searching…", fontSize = 14.sp)
            }
        }
        viewModel.foundBridges.forEach { bridge ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    TextMMD(bridge.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    TextMMD(bridge.ip, fontSize = 14.sp)
                }
                ButtonMMD(onClick = { viewModel.pair(bridge.ip) }) {
                    TextMMD("Pair")
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDividerMMD()
        Spacer(Modifier.height(16.dp))

        TextMMD("Or enter the bridge IP address manually:", fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        TextFieldMMD(
            value = manualIp,
            onValueChange = { manualIp = it },
            label = { TextMMD("Bridge IP address") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButtonMMD(
            onClick = { viewModel.pair(manualIp.trim()) },
            enabled = manualIp.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextMMD("Pair")
        }

        viewModel.setupError?.let {
            Spacer(Modifier.height(16.dp))
            TextMMD(it, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
