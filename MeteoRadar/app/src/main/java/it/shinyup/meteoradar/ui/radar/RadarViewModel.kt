package it.shinyup.meteoradar.ui.radar

import android.location.Location
import androidx.lifecycle.*
import it.shinyup.meteoradar.data.WeatherRepository
import it.shinyup.meteoradar.data.models.OpenMeteoResponse
import it.shinyup.meteoradar.data.models.RainViewerResponse
import kotlinx.coroutines.launch

class RadarViewModel : ViewModel() {

    private val repository = WeatherRepository()

    private val _radarData = MutableLiveData<Result<RainViewerResponse>>()
    val radarData: LiveData<Result<RainViewerResponse>> = _radarData

    private val _forecast = MutableLiveData<Result<OpenMeteoResponse>>()
    val forecast: LiveData<Result<OpenMeteoResponse>> = _forecast

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _currentFrameIndex = MutableLiveData(0)
    val currentFrameIndex: LiveData<Int> = _currentFrameIndex

    private val _isAnimating = MutableLiveData(false)
    val isAnimating: LiveData<Boolean> = _isAnimating

    private val _userLocation = MutableLiveData<Location?>()
    val userLocation: LiveData<Location?> = _userLocation

    fun loadData(location: Location?) {
        _userLocation.value = location
        val lat = location?.latitude ?: 41.9028  // default: Roma
        val lon = location?.longitude ?: 12.4964

        viewModelScope.launch {
            _isLoading.value = true
            _radarData.value = repository.getRadarFrames()
            _forecast.value = repository.getForecast(lat, lon)
            _isLoading.value = false
        }
    }

    fun setFrameIndex(index: Int) {
        _currentFrameIndex.value = index
    }

    fun toggleAnimation() {
        _isAnimating.value = !(_isAnimating.value ?: false)
    }

    fun stopAnimation() {
        _isAnimating.value = false
    }
}
