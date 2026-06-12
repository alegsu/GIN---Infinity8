package it.shinyup.meteoradar.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import it.shinyup.meteoradar.data.db.AppDatabase
import it.shinyup.meteoradar.data.db.ForecastSnapshot
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DayHistory(
    val date: String,           // "2026-06-16"
    val label: String,          // "Lun 16 giu"
    val snapshots: List<ForecastSnapshot>  // oldest first
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val _history = MutableLiveData<List<DayHistory>>()
    val history: LiveData<List<DayHistory>> = _history

    private val _isEmpty = MutableLiveData(false)
    val isEmpty: LiveData<Boolean> = _isEmpty

    fun load() {
        viewModelScope.launch {
            val dao = AppDatabase.getInstance(getApplication()).snapshotDao()
            val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val snapshots = dao.getSnapshotsFrom(today)

            val grouped = snapshots.groupBy { it.targetDate }
            val result = grouped.entries
                .sortedBy { it.key }
                .map { (date, snaps) ->
                    DayHistory(
                        date = date,
                        label = formatDateLabel(date),
                        snapshots = snaps.sortedBy { it.fetchedAt }
                    )
                }
                .filter { it.snapshots.size >= 2 } // only show days with at least 2 readings

            _history.value = result
            _isEmpty.value = result.isEmpty()
        }
    }

    private fun formatDateLabel(iso: String): String {
        return try {
            val d = LocalDate.parse(iso)
            val dow = when (d.dayOfWeek.value) {
                1 -> "Lun"; 2 -> "Mar"; 3 -> "Mer"; 4 -> "Gio"
                5 -> "Ven"; 6 -> "Sab"; else -> "Dom"
            }
            val months = arrayOf("","gen","feb","mar","apr","mag","giu","lug","ago","set","ott","nov","dic")
            "$dow ${d.dayOfMonth} ${months[d.monthValue]}"
        } catch (e: Exception) { iso }
    }
}
