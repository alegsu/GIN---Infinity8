package it.shinyup.meteoradar.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import it.shinyup.meteoradar.data.models.WeatherAlert

@Database(entities = [WeatherAlert::class, ForecastSnapshot::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun alertDao(): AlertDao
    abstract fun snapshotDao(): ForecastSnapshotDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "meteoradar.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
