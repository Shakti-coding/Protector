package com.filevault.pro.di

import com.filevault.pro.data.repository.FileRepositoryImpl
import com.filevault.pro.data.repository.NotificationRepositoryImpl
import com.filevault.pro.data.repository.SyncRepositoryImpl
import com.filevault.pro.domain.repository.FileRepository
import com.filevault.pro.domain.repository.NotificationRepository
import com.filevault.pro.domain.repository.SyncRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindFileRepository(impl: FileRepositoryImpl): FileRepository

    @Binds @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds @Singleton
    abstract fun bindNotificationRepository(impl: NotificationRepositoryImpl): NotificationRepository
}
