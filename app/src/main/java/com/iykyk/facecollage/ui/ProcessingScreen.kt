package com.iykyk.facecollage.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.iykyk.facecollage.data.ProcessingState
import kotlin.math.roundToInt

@Composable
fun ProcessingScreen(state: ProcessingState.Working, onCancel: () -> Unit) {
    val target = state.fraction
    val animated by animateFloatAsState(targetValue = target ?: 0f, label = "progress")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = state.label,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
        )

        val percentLabel = target?.let { "${(it * 100).roundToInt()}%" } ?: "Almost there"
        Text(
            text = percentLabel,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 20.dp),
        )

        if (target != null) {
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .semantics { contentDescription = "Processing progress" },
                color = MaterialTheme.colorScheme.primary,
                // a tinted track: plain surface is white on a cream background and vanishes
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .semantics { contentDescription = "Processing" },
                color = MaterialTheme.colorScheme.primary,
                // a tinted track: plain surface is white on a cream background and vanishes
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            )
        }

        TextButton(onClick = onCancel, modifier = Modifier.padding(top = 32.dp)) {
            Text("Cancel", style = MaterialTheme.typography.labelLarge)
        }
    }
}
