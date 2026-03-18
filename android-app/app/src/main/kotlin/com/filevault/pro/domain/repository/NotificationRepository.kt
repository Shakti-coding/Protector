package com.filevault.pro.domain.repository

import com.filevault.pro.domain.model.AppNotification
import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun getAll(): Flow<List<AppNotification>>
    suspend fun add(notification: AppNotification)
    suspend fun markAllRead()
    suspend fun clearAll()
    fun getUnreadCount(): Flow<Int>
}
