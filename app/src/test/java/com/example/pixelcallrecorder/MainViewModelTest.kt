package com.example.pixelcallrecorder

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.example.pixelcallrecorder.utils.PermissionManager
import com.example.pixelcallrecorder.viewmodel.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.never
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
        viewModel = MainViewModel(permissionManager)
        viewModel.isRecording.observeForever(recordingStateObserver)
    }

    @Test
    fun `initial recording state should be false`() {
        // Assert: Check the initial value of the LiveData.
        // It's assumed to be initialized to false in the ViewModel.
        assertEquals(false, viewModel.isRecording.value)
    }

    @Test
    fun `startRecording should update state to true when permissions are granted`() {
        // Arrange
        whenever(permissionManager.checkPermissions()).thenReturn(true)

        // Act
        viewModel.startRecording()

        // Assert
        verify(recordingStateObserver).onChanged(true)
    }

    @Test
    fun `startRecording should not update state when permissions are not granted`() {
        // Arrange
        whenever(permissionManager.checkPermissions()).thenReturn(false)

        // Act
        viewModel.startRecording()

        // Assert
        verify(recordingStateObserver, never()).onChanged(true)
    }

    @Test
    fun `stopRecording should update state to false`() {
        // Act
        viewModel.stopRecording()

        // Assert
        verify(recordingStateObserver).onChanged(false)
    }
}