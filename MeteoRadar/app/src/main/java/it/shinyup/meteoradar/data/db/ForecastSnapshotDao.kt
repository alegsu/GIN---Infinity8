package it.shinyup.meteoradar.data.db

import androidx.room.*

@Dao
interface ForecastSnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<ForecastSnapshot>)

    @Query("SELECT * FROM forecast_snapshots WHERE targetDate >= :fromDate ORDER BY targetDate ASC, fetchedAt ASC")
    suspend fun getSnapshotsFrom(fromDate: String): List<ForecastSnapshot>

    @Query("SELECT MAX(fetchedAt) FROM forecast_snapshots WHERE locationName = :location")
    suspend fun getLastFetchTimeForLocation(location: String): Long?

    @Query("DELETE FROM forecast_snapshots WHERE fetchedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query("SELECT * FROM forecast_snapshots WHERE targetDate = :date ORDER BY fetchedAt ASC")
    suspend fun getSnapshotsForDate(date: String): List<ForecastSnapshot>
}
