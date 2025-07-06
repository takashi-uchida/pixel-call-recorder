package com.example.pixelcallrecorder.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixelcallrecorder.utils.PermissionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val permissionManager: PermissionManager
) : ViewModel() {
    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    init {
        checkPermissions()
    }

    fun startRecording() {
        _isRecording.value = true
    }

    fun stopRecording() {
        _isRecording.value = false
    }

    fun checkPermissions() {
        viewModelScope.launch {
            if (permissionManager.checkPermissions()) {
                _uiState.value = MainUiState.HasPermissions(isRecording = false)
            } else {
                _uiState.value = MainUiState.NeedsPermission
            }
        }
    }

    fun requestPermissions() {
        permissionManager.requestPermissions()
        viewModelScope.launch {
            checkPermissions()
        }
    }
}

sealed interface MainUiState {
    object Loading : MainUiState
    object NeedsPermission : MainUiState
    data class HasPermissions(val isRecording: Boolean) : MainUiState
}
