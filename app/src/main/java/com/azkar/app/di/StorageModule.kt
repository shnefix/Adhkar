package com.azkar.app.di

import android.content.Context
import com.azkar.app.data.AzkarDao
import com.azkar.app.data.AzkarDatabase
import com.azkar.app.data.AzkarDatabaseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AzkarDatabase =
        AzkarDatabaseProvider.get(context)

    @Provides
    fun provideAzkarDao(database: AzkarDatabase): AzkarDao = database.azkarDao()
}
