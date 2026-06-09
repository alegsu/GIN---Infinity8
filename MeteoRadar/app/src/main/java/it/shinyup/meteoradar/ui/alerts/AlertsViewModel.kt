package it.shinyup.meteoradar.ui.alerts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.shinyup.meteoradar.data.db.AppDatabase
import kotlinx.coroutines.launch

class AlertsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.getInstance(app).alertDao()

    val alerts = dao.getAllAlerts()
    val unreadCount = dao.getUnreadCount()

    fun markAllAsRead() {
        viewModelScope.launch { dao.markAllAsRead() }
    }

    fun markAsRead(id: Int) {
        viewModelScope.launch { dao.markAsRead(id) }
    }
}
