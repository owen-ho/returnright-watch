package sg.returnright.watch.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import sg.returnright.watch.BuildConfig
import sg.returnright.watch.navigation.Coordinate
import java.io.IOException

class StationRepository(context: Context) {
    private val cache = context.getSharedPreferences("stations", Context.MODE_PRIVATE)
    val lastAttemptAt: Long get() = maxOf(cache.getLong("lastAttemptAt", 0), cache.getLong("fetchedAt", 0))
    fun recordAttempt(now: Long) { cache.edit().putLong("lastAttemptAt", now).apply() }
    private val api = StationApiClient(BuildConfig.STATION_API,
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:157.0) Gecko/20100101 Firefox/157.0",
        diagnostic = { if (BuildConfig.DEBUG) Log.i("StationHttp", it) })

    suspend fun cached(): StationSnapshot? = withContext(Dispatchers.IO) {
        val raw = cache.getString("response", null) ?: return@withContext null
        runCatching { StationSnapshot(StationParser.parse(raw), cache.getLong("fetchedAt", 0)) }.getOrNull()
    }

    suspend fun fetch(origin: Coordinate? = null): StationSnapshot = withContext(Dispatchers.IO) {
        val raw = api.fetchRaw(origin)
        val stations = StationParser.parse(raw)
        if (stations.isEmpty()) throw IOException("Station feed contained no mapped Singapore stations")
        val timestamp = System.currentTimeMillis()
        cache.edit().putString("response", raw).putLong("fetchedAt", timestamp).apply()
        StationSnapshot(stations, timestamp)
    }

}
