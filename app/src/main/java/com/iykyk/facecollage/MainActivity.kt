package com.iykyk.facecollage

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iykyk.facecollage.data.ProcessingState
import com.iykyk.facecollage.ui.ProcessingScreen
import com.iykyk.facecollage.ui.ResultsScreen
import com.iykyk.facecollage.ui.VideoPickerScreen
import com.iykyk.facecollage.ui.theme.FaceCollageTheme
import com.iykyk.facecollage.viewmodel.CollageViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FaceCollageTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    App()
                }
            }
        }
    }
}

/**
 * Three screens driven straight off the pipeline state. A navigation library would earn
 * nothing here: the state itself already says which screen belongs on top.
 */
@Composable
private fun App(viewModel: CollageViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is ProcessingState.Idle -> VideoPickerScreen(onVideoPicked = viewModel::process)
        is ProcessingState.Working -> ProcessingScreen(current, onCancel = viewModel::reset)
        is ProcessingState.Done -> ResultsScreen(current.result, onStartOver = viewModel::reset)
        is ProcessingState.Failed -> ErrorScreen(current.message, onRetry = viewModel::reset)
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "That did not work",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        Button(onClick = onRetry) {
            Text(text = "Try again", style = MaterialTheme.typography.labelLarge)
        }
    }
}
