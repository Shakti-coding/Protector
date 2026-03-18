package com.filevault.pro.di

import android.content.Context
import androidx.room.Room
import com.filevault.pro.data.local.AppDatabase
import com.filevault.pro.data.local.dao.ExcludedFolderDao
import com.filevault.pro.data.local.dao.FileEntryDao
import com.filevault.pro.data.local.dao.NotificationDao
import com.filevault.pro.data.local.dao.SyncHistoryDao
import com.filevault.pro.data.local.dao.SyncProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideFileEntryDao(db: AppDatabase): FileEntryDao = db.fileEntryDao()
    @Provides fun provideSyncProfileDao(db: AppDatabase): SyncProfileDao = db.syncProfileDao()
    @Provides fun provideSyncHistoryDao(db: AppDatabase): SyncHistoryDao = db.syncHistoryDao()
    @Provides fun provideExcludedFolderDao(db: AppDatabase): ExcludedFolderDao = db.excludedFolderDao()
    @Provides fun provideNotificationDao(db: AppDatabase): NotificationDao = db.notificationDao()
}
