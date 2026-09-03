package com.iykyk.facecollage.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.iykyk.facecollage.data.CollageResult
import com.iykyk.facecollage.data.PersonResult
import com.iykyk.facecollage.pipeline.MediaSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ResultsScreen(
    result: CollageResult,
    onStartOver: () -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Shown inline rather than as a toast: a toast vanishes in two seconds and leaves no
    // evidence the save worked, on screen or in a screen recording.
    var saveStatus by remember { mutableStateOf<String?>(null) }

    fun save() {
        scope.launch {
            saveStatus = try {
                withContext(Dispatchers.IO) { MediaSaver.saveToGallery(context, result.collage) }
                "Saved to your gallery"
            } catch (e: Throwable) {
                e.message ?: "Could not save that"
            }
        }
    }

    // Only API 26-28 needs a storage permission; from API 29 MediaStore handles it.
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) save() else saveStatus = "Storage permission is needed to save"
    }

    fun onSaveClicked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            save()
        } else {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    fun onShareClicked() {
        scope.launch {
            val intent = withContext(Dispatchers.IO) { MediaSaver.shareIntent(context, result.collage) }
            context.startActivity(Intent.createChooser(intent, "Share your collage"))
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    text = peopleHeadline(result.people.size),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = "${result.totalAppearances} appearances in total",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                Image(
                    bitmap = result.collage.asImageBitmap(),
                    contentDescription = "Your collage, with ${result.people.size} people",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BigButton(
                        label = "Save",
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.weight(1f),
                        onClick = ::onSaveClicked,
                    )
                    BigButton(
                        label = "Share",
                        container = MaterialTheme.colorScheme.secondary,
                        content = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.weight(1f),
                        onClick = ::onShareClicked,
                    )
                }

                saveStatus?.let { status ->
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .semantics { contentDescription = status },
                    )
                }

                Text(
                    text = "Everyone we found",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
                )
            }
        }

        items(result.people) { person -> PersonTile(person) }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                TextButton(
                    onClick = onStartOver,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(top = 8.dp),
                ) {
                    Text("Try another video", style = MaterialTheme.typography.labelLarge)
                }
                DebugPanel(result, Modifier.padding(bottom = 12.dp))
            }
        }
    }
}

@Composable
private fun BigButton(
    label: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container),
        modifier = modifier
            .height(68.dp)
            .semantics { contentDescription = label },
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

@Composable
private fun PersonTile(person: PersonResult) {
    val plural = if (person.appearanceCount == 1) "appearance" else "appearances"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Image(
            bitmap = person.portrait.asImageBitmap(),
            contentDescription = "Person ${person.identityId + 1}, ${person.appearanceCount} $plural",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .semantics { contentDescription = "${person.appearanceCount} $plural" },
        ) {
            Text(
                text = "x${person.appearanceCount}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

private fun peopleHeadline(count: Int): String = when (count) {
    0 -> "No faces found"
    1 -> "Found 1 person"
    else -> "Found $count people"
}
