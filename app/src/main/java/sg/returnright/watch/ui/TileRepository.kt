package sg.returnright.watch.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import sg.returnright.watch.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit

data class Tile(val zoom: Int, val x: Int, val y: Int)

class TileRepository private constructor(context: Context) {
    private val client = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "map-tiles"), 48L * 1024 * 1024))
        .callTimeout(12, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            // Honor HTTP caching; use OSM's minimum seven days only when unspecified.
            if (response.header("Cache-Control") == null && response.header("Expires") == null)
                response.newBuilder().header("Cache-Control", "public, max-age=604800").build()
            else response
        }.build()

    suspend fun load(tile: Tile): ImageBitmap? = withContext(Dispatchers.IO) {
        val url = BuildConfig.TILE_URL.replace("{z}", tile.zoom.toString())
            .replace("{x}", tile.x.toString()).replace("{y}", tile.y.toString())
        val request = Request.Builder().url(url)
            .header("User-Agent", "ReturnRightWatch/${BuildConfig.VERSION_NAME} (sg.returnright.watch)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val bytes = response.body?.bytes() ?: return@withContext null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    companion object {
        @Volatile private var instance: TileRepository? = null
        fun shared(context: Context): TileRepository = instance ?: synchronized(this) {
            instance ?: TileRepository(context.applicationContext).also { instance = it }
        }
    }
}
