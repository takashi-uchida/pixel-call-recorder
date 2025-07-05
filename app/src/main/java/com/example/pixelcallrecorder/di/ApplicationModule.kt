package com.example.pixelcallrecorder.di

import android.content.Context
import com.example.pixelcallrecorder.utils.FileUtils
import com.example.pixelcallrecorder.utils.PermissionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApplicationModule {
    @Provides
    @Singleton
    fun providePermissionManager(@ApplicationContext context: Context): PermissionManager {
        return PermissionManager(context as androidx.fragment.app.FragmentActivity)
    }

    @Provides
    @Singleton
    fun provideFileUtils(): FileUtils {
        return FileUtils
    }
}