package sg.returnright.watch

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sg.returnright.watch.data.*
import sg.returnright.watch.navigation.Position

data class FeedState(
    val stations: List<Station> = emptyList(),
    val fetchedAt: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val demo: Boolean = false,
)

class StationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StationRepository(application)
    private val mutableFeed = MutableStateFlow(FeedState())
    val feed = mutableFeed.asStateFlow()
    private val refreshPolicy = RefreshPolicy(repository.lastAttemptAt)
    private var searchPosition: Position? = null

    fun updateSearchPosition(position: Position?) { searchPosition = position }
    fun nextAutoRefreshDelay(): Long = refreshPolicy.nextCheckDelay(System.currentTimeMillis())

    init {
        viewModelScope.launch {
            repository.cached()?.let { cached ->
                mutableFeed.update { it.copy(stations = cached.stations, fetchedAt = cached.fetchedAt) }
            }
            refresh()
        }
    }

    fun refresh(manual: Boolean = false) {
        if (mutableFeed.value.loading) return
        val now = System.currentTimeMillis()
        if (!refreshPolicy.tryAcquire(now, manual)) return
        repository.recordAttempt(now)
        val origin = searchPosition?.takeIf { it.isFresh() }?.coordinate
        if (BuildConfig.DEBUG) Log.i("StationHttp", "fetch trigger=${if (manual) "manual" else "automatic"} nearby=${origin != null}")
        mutableFeed.update { it.copy(loading = true, error = null, demo = false,
            stations = if (it.demo) emptyList() else it.stations, fetchedAt = if (it.demo) null else it.fetchedAt) }
        viewModelScope.launch {
            try {
                val snapshot = repository.fetch(origin)
                mutableFeed.value = FeedState(snapshot.stations, snapshot.fetchedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableFeed.update { it.copy(loading = false, error =
                    if (e is java.io.IOException) e.message ?: "Cannot reach station service"
                    else "Station response format changed") }
            }
        }
    }

    fun showDemo() {
        if (mutableFeed.value.loading) return
        mutableFeed.value = FeedState(demoStations, demo = true)
    }
}
