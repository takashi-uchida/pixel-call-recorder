package com.example.pixelcallrecorder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pixelcallrecorder.service.CallRecorderService
import com.example.pixelcallrecorder.ui.MainScreen
import com.example.pixelcallrecorder.ui.theme.PixelCallRecorderTheme
import com.example.pixelcallrecorder.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PixelCallRecorderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val viewModel: MainViewModel = hiltViewModel()
                    val uiState = viewModel.uiState.collectAsState()

                    when (val state = uiState.value) {
                        is MainViewModel.MainUiState.HasPermissions -> {
                            startService(Intent(this, CallRecorderService::class.java))
                            MainScreen.RecordingScreen(
                                isRecording = state.isRecording,
                                recordings = emptyList()
                            )
                        }
                        MainViewModel.MainUiState.NeedsPermission -> {
                            MainScreen.PermissionRequestScreen(
                                onRequestPermission = viewModel::requestPermissions
                            )
                        }
                        MainViewModel.MainUiState.Loading -> {
                            // ローディング表示
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(Intent(this, CallRecorderService::class.java))
    }
}