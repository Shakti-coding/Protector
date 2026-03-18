package com.filevault.pro.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.filevault.pro.domain.model.AppNotification
import com.filevault.pro.domain.model.NotificationType

@Entity(
    tableName = "notifications",
    indices = [Index("timestamp")]
)
data class NotificationEntity(
    @PrimaryKey val id: Long,
    val type: String,
    val title: String,
    val message: String,
    val timestamp: Long,
    val isRead: Boolean
)

fun NotificationEntity.toDomain() = AppNotification(
    id = id,
    type = NotificationType.valueOf(type),
    title = title,
    message = message,
    timestamp = timestamp,
    isRead = isRead
)

fun AppNotification.toEntity() = NotificationEntity(
    id = id,
    type = type.name,
    title = title,
    message = message,
    timestamp = timestamp,
    isRead = isRead
)
