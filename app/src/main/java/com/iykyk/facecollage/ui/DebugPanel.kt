package com.iykyk.facecollage.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iykyk.facecollage.data.CollageResult

/**
 * Collapsed by default. Exists so appearance counts can be eyeballed against a known answer
 * while tuning: it prints each identity's track count and the timestamp range of every segment.
 */
@Composable
fun DebugPanel(result: CollageResult, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (expanded) "Hide details" else "Show details",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        if (!expanded) return@Column

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                DebugLine(
                    "video ${result.videoDurationMs} ms | frames ${result.framesAnalysed} | " +
                        "faces ${result.facesDetected}"
                )
                DebugLine("people ${result.people.size} | appearances ${result.totalAppearances}")

                for (person in result.people) {
                    DebugLine("")
                    DebugLine("person ${person.identityId}: ${person.appearanceCount} appearances")
                    for ((index, appearance) in person.appearances.withIndex()) {
                        DebugLine(
                            "  #${index + 1} ${formatMs(appearance.startMs)}-${formatMs(appearance.endMs)}" +
                                "  tracks=${appearance.trackIds}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugLine(text: String) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun formatMs(ms: Long): String = "%.2fs".format(ms / 1000.0)
