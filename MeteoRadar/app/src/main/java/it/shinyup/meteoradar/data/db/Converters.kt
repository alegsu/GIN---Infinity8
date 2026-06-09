package it.shinyup.meteoradar.data.db

import androidx.room.TypeConverter
import it.shinyup.meteoradar.data.models.AlertLevel
import it.shinyup.meteoradar.data.models.AlertType

class Converters {

    @TypeConverter
    fun fromAlertType(value: AlertType): String = value.name

    @TypeConverter
    fun toAlertType(value: String): AlertType = AlertType.valueOf(value)

    @TypeConverter
    fun fromAlertLevel(value: AlertLevel): String = value.name

    @TypeConverter
    fun toAlertLevel(value: String): AlertLevel = AlertLevel.valueOf(value)
}
