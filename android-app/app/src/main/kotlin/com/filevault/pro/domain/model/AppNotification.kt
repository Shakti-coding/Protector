package com.filevault.pro.domain.model

enum class NotificationType { SCAN, SYNC, ERROR, INFO }

data class AppNotification(
    val id: Long = System.currentTimeMillis(),
    val type: NotificationType,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
