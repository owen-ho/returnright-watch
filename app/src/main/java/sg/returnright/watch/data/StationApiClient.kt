package sg.returnright.watch.data

import okhttp3.HttpUrl
import okhttp3.ConnectionPool
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import sg.returnright.watch.navigation.Coordinate
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Uses the direct location endpoints confirmed in the user's working map session. */
class StationApiClient(
    baseUrl: String,
    private val userAgent: String,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val diagnostic: (String) -> Unit = {},
    private val warmUpFirstConnection: Boolean = true,
) {
    private val base = baseUrl.trimEnd('/').toHttpUrl()
    // Observe actual network requests, including OkHttp's gzip header, without
    // recording tokens, query coordinates, cookies, or response bodies.
    private val observedClient = client.newBuilder().addNetworkInterceptor { chain ->
        val request = chain.request()
        val connection = chain.connection()
        val connectionId = connection?.let { System.identityHashCode(it) }
        val headers = listOf("User-Agent", "Accept", "Accept-Language", "Accept-Encoding",
            "x-bcrs-client", "Referer", "DNT", "Connection", "Sec-Fetch-Dest", "Sec-Fetch-Mode",
            "Sec-Fetch-Site", "Sec-GPC", "Priority").joinToString { "$it=${request.header(it)}" }
        val started = System.nanoTime()
        diagnostic("request path=${request.url.encodedPath} tokenPresent=${request.header("x-bcrs-map-token") != null} connection=$connectionId peer=${connection?.socket()?.remoteSocketAddress} tls=${connection?.handshake()?.tlsVersion} headers=[$headers]")
        try {
            chain.proceed(request).also { response ->
                diagnostic("response path=${request.url.encodedPath} status=${response.code} protocol=${response.protocol} connection=$connectionId elapsedMs=${(System.nanoTime() - started) / 1_000_000} server=${response.header("server")}")
            }
        } catch (e: IOException) {
            diagnostic("failure path=${request.url.encodedPath} type=${e.javaClass.simpleName} elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
            throw e
        }
    }.build()
    private var accessToken: String? = null
    private var accessExpiresAt = 0L
    private var startupAttempted = false

    fun fetchRaw(origin: Coordinate? = null): String {
        val url = if (origin == null) endpoint("locations") else {
            require(origin.latitude.isFinite() && origin.longitude.isFinite() &&
                origin.latitude in -90.0..90.0 && origin.longitude in -180.0..180.0)
            endpoint("locations", "nearby").newBuilder()
                .addQueryParameter("lat", origin.latitude.toString())
                .addQueryParameter("lng", origin.longitude.toString())
                .addQueryParameter("radius", "2000").build()
        }
        prepareStartupConnection()
        // The actual token bootstrap still has no unbounded authorization retries.
        val token = token()
        return try {
            request(url, "station list", token)
        } catch (e: HttpFailure) {
            if (e.code != 401 && e.code != 403) throw e
            // Refresh a rejected token once. Never loop on denied requests.
            accessToken = null
            accessExpiresAt = 0
            request(url, "station list", token())
        }
    }

    private fun prepareStartupConnection() {
        if (startupAttempted || !warmUpFirstConnection) return
        startupAttempted = true
        // A dedicated pool guarantees the real request cannot reuse this socket.
        val pool = ConnectionPool()
        val warmup = observedClient.newBuilder().connectionPool(pool)
            .retryOnConnectionFailure(false).callTimeout(5, TimeUnit.SECONDS).build()
        diagnostic("startup warmup begin; response will be discarded")
        try {
            request(endpoint("locations", "access-token"), "startup warmup", httpClient = warmup)
        } catch (e: IOException) {
            if (e is HttpFailure && e.code == 429) throw e
            diagnostic("startup warmup discarded type=${e.javaClass.simpleName}")
        } finally {
            pool.evictAll()
            diagnostic("startup warmup connection closed")
        }
    }

    private fun token(): String =
        accessToken?.takeIf { clock() < accessExpiresAt - 30_000 } ?: run {
            val data = payload(request(endpoint("locations", "access-token"), "station token"))
            val value = data.optString("token").takeUnless { data.isNull("token") }.orEmpty().trim()
            if (value.isEmpty()) throw IOException("Station service did not issue an access token")
            accessToken = value
            accessExpiresAt = data.optLong("expiresAt", 0)
            value
        }

    private fun endpoint(vararg segments: String): HttpUrl = base.newBuilder()
        .apply { segments.forEach(::addPathSegment) }.build()

    private fun payload(raw: String): JSONObject = try {
        JSONObject(raw).getJSONObject("data")
    } catch (_: Exception) {
        throw IOException("Station service returned an invalid token response")
    }

    private fun request(url: HttpUrl, stage: String, token: String? = null,
                        httpClient: OkHttpClient = observedClient): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("x-bcrs-client", "web")
            .header("Referer", "https://returnright.sg/px/")
            // Browser-header diagnostic only: Android TLS and HTTP negotiation stay unchanged.
            // Leave Accept-Encoding to OkHttp so it advertises and decodes supported gzip.
            .header("DNT", "1")
            .header("Connection", "keep-alive")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Sec-GPC", "1")
            .header("Priority", "u=4")
            .apply { if (token != null) header("x-bcrs-map-token", token) }
            .build()
        return httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string()
                ?: throw IOException("Station service returned an empty response ($stage)")
            if (!response.isSuccessful) {
                // Surface only a short server error message; never include tokens or raw payloads.
                val hint = runCatching { JSONObject(body).optString("error").take(160) }
                    .getOrNull()?.takeIf { it.isNotBlank() }
                val server = response.header("server")?.take(80)
                throw HttpFailure(response.code, stage, server, hint)
            }
            body
        }
    }

    private class HttpFailure(
        val code: Int,
        stage: String,
        server: String?,
        hint: String?,
    ) : IOException(buildString {
        append(when (code) {
            401, 403 -> "Station service denied access ($code • $stage)"
            429 -> "Station service is busy; try again later (429 • $stage)"
            else -> "Station service error ($code • $stage)"
        })
        if (server != null) append(" • ").append(server)
        if (hint != null) append(" • ").append(hint)
    })
}
