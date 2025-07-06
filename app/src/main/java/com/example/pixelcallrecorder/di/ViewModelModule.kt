package com.example.pixelcallrecorder.di

import com.example.pixelcallrecorder.viewmodel.MainViewModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

@Module
@InstallIn(ViewModelComponent::class)
object ViewModelModule {
    @Provides
    fun provideMainViewModel(
        permissionManager: PermissionManager
    ): MainViewModel {
        return MainViewModel(permissionManager)
    }
}
