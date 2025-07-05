package com.example.pixelcallrecorder.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pixelcallrecorder.R

@Composable
fun PermissionRequestScreen(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.permission_required_title),
            modifier = Modifier.padding(16.dp)
        )
        Text(
            text = stringResource(R.string.permission_required_message),
            modifier = Modifier.padding(16.dp)
        )
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(text = stringResource(R.string.allow_permission))
        }
    }
}

@Composable
fun RecordingScreen(
    isRecording: Boolean,
    recordings: List<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isRecording) {
            Text(text = stringResource(R.string.recording_in_progress))
        }
        if (recordings.isEmpty()) {
            Text(text = stringResource(R.string.no_recordings))
        }
    }
}