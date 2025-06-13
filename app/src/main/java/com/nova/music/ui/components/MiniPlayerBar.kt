package com.nova.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private val PlayArrow = materialIcon(name = "Filled.PlayArrow") {
    materialPath {
        moveTo(8.0f, 5.0f)
        verticalLineTo(19.0f)
        lineTo(19.0f, 12.0f)
        close()
    }
}

private val SkipNext = materialIcon(name = "Filled.SkipNext") {
    materialPath {
        moveTo(6.0f, 18.0f)
        verticalLineTo(6.0f)
        horizontalLineTo(8.0f)
        verticalLineTo(18.0f)
        horizontalLineTo(6.0f)
        moveTo(9.5f, 12.0f)
        lineTo(18.0f, 6.0f)
        verticalLineTo(18.0f)
        lineTo(9.5f, 12.0f)
        close()
    }
}

private val SkipPrevious = materialIcon(name = "Filled.SkipPrevious") {
    materialPath {
        moveTo(6.0f, 6.0f)
        verticalLineTo(18.0f)
        lineTo(14.5f, 12.0f)
        lineTo(6.0f, 6.0f)
        moveTo(16.0f, 6.0f)
        horizontalLineTo(18.0f)
        verticalLineTo(18.0f)
        horizontalLineTo(16.0f)
        verticalLineTo(6.0f)
        close()
    }
}

@Composable
fun MiniPlayerBar(
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onTap),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Song info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Song Title",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Artist Name",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            // Playback controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* Next */ }) {
                    Icon(
                        imageVector = SkipNext,
                        contentDescription = "Next"
                    )
                }
                IconButton(onClick = { /* Play/Pause */ }) {
                    Icon(
                        imageVector = PlayArrow,
                        contentDescription = "Play"
                    )
                }
                IconButton(onClick = { /* Previous */ }) {
                    Icon(
                        imageVector = SkipPrevious,
                        contentDescription = "Previous"
                    )
                }
            }
        }
    }
} 