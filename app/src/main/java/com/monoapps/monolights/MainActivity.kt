package com.monoapps.monolights

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.monoapps.monolights.ui.HomeScreen
import com.monoapps.monolights.ui.SetupScreen
import com.mudita.mmd.ThemeMMD

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ThemeMMD { App() } }
    }
}

@Composable
private fun App() {
    val viewModel: LightsViewModel = viewModel()
    if (viewModel.paired) HomeScreen(viewModel) else SetupScreen(viewModel)
}
