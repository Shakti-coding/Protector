package com.filevault.pro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.filevault.pro.data.local.entity.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC LIMIT 100")
    fun getAll(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: NotificationEntity)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>
}
