package com.filevault.pro.data.repository

import com.filevault.pro.data.local.dao.NotificationDao
import com.filevault.pro.data.local.entity.toDomain
import com.filevault.pro.data.local.entity.toEntity
import com.filevault.pro.domain.model.AppNotification
import com.filevault.pro.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class NotificationRepositoryImpl @Inject constructor(
    private val notificationDao: NotificationDao
) : NotificationRepository {

    override fun getAll(): Flow<List<AppNotification>> =
        notificationDao.getAll().map { list -> list.map { it.toDomain() } }

    override suspend fun add(notification: AppNotification) {
        notificationDao.insert(notification.toEntity())
    }

    override suspend fun markAllRead() {
        notificationDao.markAllRead()
    }

    override suspend fun clearAll() {
        notificationDao.deleteAll()
    }

    override fun getUnreadCount(): Flow<Int> = notificationDao.getUnreadCount()
}
