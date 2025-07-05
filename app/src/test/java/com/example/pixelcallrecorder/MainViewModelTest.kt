package com.example.pixelcallrecorder

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.example.pixelcallrecorder.utils.PermissionManager
import com.example.pixelcallrecorder.viewmodel.MainViewModel
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class MainViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var permissionManager: PermissionManager

    @Mock
    private lateinit var recordingStateObserver: Observer<Boolean>

    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        whenever(permissionManager.checkPermissions()).thenReturn(true)
        viewModel = MainViewModel(permissionManager)
        viewModel.isRecording.observeForever(recordingStateObserver)
    }

    @Test
    fun startRecording_should_update_recording_state_to_true() {
        viewModel.startRecording()
        verify(recordingStateObserver).onChanged(true)
    }

    @Test
    fun stopRecording_should_update_recording_state_to_false() {
        viewModel.stopRecording()
        verify(recordingStateObserver).onChanged(false)
    }
}