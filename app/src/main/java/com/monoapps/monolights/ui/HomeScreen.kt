@file:OptIn(ExperimentalMaterial3Api::class)

package com.monoapps.monolights.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monoapps.monolights.LightsViewModel
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.menus.DropdownMenuItemMMD
import com.mudita.mmd.components.menus.DropdownMenuMMD
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import com.mudita.mmd.components.snackbar.SnackbarDurationMMD
import com.mudita.mmd.components.snackbar.SnackbarHostMMD
import com.mudita.mmd.components.snackbar.SnackbarHostStateMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD

@Composable
fun HomeScreen(viewModel: LightsViewModel) {
    var menuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostStateMMD() }

    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDurationMMD.Short)
        }
    }

    Scaffold(
        containerColor = Color.White,
        snackbarHost = { SnackbarHostMMD(hostState = snackbarHostState) },
        topBar = {
            TopAppBarMMD(
                title = { TextMMD("Lights", fontSize = 24.sp, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(imageVector = Icons.Outlined.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenuMMD(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItemMMD(
                            text = { TextMMD("Forget bridge") },
                            onClick = {
                                menuOpen = false
                                viewModel.forgetBridge()
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when {
            viewModel.loading -> Centered(modifier) { CircularProgressIndicatorMMD() }
            viewModel.loadError != null -> Centered(modifier) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TextMMD(viewModel.loadError!!, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButtonMMD(onClick = { viewModel.refresh() }) { TextMMD("Retry") }
                }
            }
            else -> LightsTab(viewModel, modifier)
        }
    }
}

@Composable
internal fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
        content()
    }
}
