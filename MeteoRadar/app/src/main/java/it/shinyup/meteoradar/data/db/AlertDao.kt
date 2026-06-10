package it.shinyup.meteoradar.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import it.shinyup.meteoradar.data.models.WeatherAlert

@Dao
interface AlertDao {

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC")
    fun getAllAlerts(): LiveData<List<WeatherAlert>>

    @Query("SELECT * FROM alerts WHERE isRead = 0 ORDER BY timestamp DESC")
    fun getUnreadAlerts(): LiveData<List<WeatherAlert>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alert: WeatherAlert): Long

    @Query("UPDATE alerts SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Int)

    @Query("UPDATE alerts SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM alerts WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT COUNT(*) FROM alerts WHERE isRead = 0")
    fun getUnreadCount(): LiveData<Int>

    @Query("SELECT * FROM alerts ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastAlert(): WeatherAlert?

    @Query("SELECT * FROM alerts WHERE timestamp > :since")
    suspend fun getAlertsSince(since: Long): List<WeatherAlert>
}
