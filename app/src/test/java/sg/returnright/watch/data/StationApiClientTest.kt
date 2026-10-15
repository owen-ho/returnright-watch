package sg.returnright.watch.data

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sg.returnright.watch.navigation.Coordinate
import java.io.IOException

class StationApiClientTest {
    private val server = MockWebServer()
    private var now = 1_800_000_000_000L
    private lateinit var client: StationApiClient
    private val stations = """{"status":"ok","data":[]}"""

    @Before fun setup() {
        server.start()
        client = StationApiClient(server.url("/px-api").toString(), "ReturnRightWatch/test", clock = { now }, warmUpFirstConnection = false)
    }
    @After fun teardown() { server.shutdown() }
    private fun response(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun token(value: String, expiry: Long = now + 600_000) =
        response("""{"status":"ok","data":{"token":"$value","expiresAt":$expiry}}""")
    private fun enqueueHandshake(value: String = "example-token") {
        server.enqueue(token(value)); server.enqueue(response(stations))
    }

    @Test fun `uses confirmed direct endpoints without requiring map UUID`() {
        enqueueHandshake()
        assertEquals(stations, client.fetchRaw())
        val bootstrap = server.takeRequest()
        assertEquals("/px-api/locations/access-token", bootstrap.path)
        assertNull(bootstrap.getHeader("x-bcrs-map-token"))
        val request = server.takeRequest()
        assertEquals("/px-api/locations", request.path)
        assertEquals("example-token", request.getHeader("x-bcrs-map-token"))
        assertEquals("web", request.getHeader("x-bcrs-client"))
        assertEquals("*/*", request.getHeader("Accept"))
        assertEquals("https://returnright.sg/px/", request.getHeader("Referer"))
        assertEquals("ReturnRightWatch/test", request.getHeader("User-Agent"))
        for (captured in listOf(bootstrap, request)) {
            assertEquals("en-US,en;q=0.9", captured.getHeader("Accept-Language"))
            assertEquals("1", captured.getHeader("DNT"))
            assertEquals("keep-alive", captured.getHeader("Connection"))
            assertEquals("empty", captured.getHeader("Sec-Fetch-Dest"))
            assertEquals("cors", captured.getHeader("Sec-Fetch-Mode"))
            assertEquals("same-origin", captured.getHeader("Sec-Fetch-Site"))
            assertEquals("1", captured.getHeader("Sec-GPC"))
            assertEquals("u=4", captured.getHeader("Priority"))
            assertEquals("gzip", captured.getHeader("Accept-Encoding"))
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun `reuses valid token on next refresh`() {
        enqueueHandshake(); server.enqueue(response(stations))
        client.fetchRaw(); client.fetchRaw()
        repeat(2) { server.takeRequest() }
        assertEquals("/px-api/locations", server.takeRequest().path)
        assertEquals(3, server.requestCount)
    }

    @Test fun `nearby search uses current coordinates rather than a saved browser location`() {
        enqueueHandshake()
        client.fetchRaw(Coordinate(1.2932, 103.8520))
        server.takeRequest()
        val url = server.takeRequest().requestUrl!!
        assertEquals("/px-api/locations/nearby", url.encodedPath)
        assertEquals("1.2932", url.queryParameter("lat"))
        assertEquals("103.852", url.queryParameter("lng"))
        assertEquals("2000", url.queryParameter("radius"))
    }

    @Test fun `refreshes expired access token`() {
        enqueueHandshake(); client.fetchRaw()
        now += 600_000
        enqueueHandshake("fresh-token"); client.fetchRaw()
        repeat(2) { server.takeRequest() }
        assertEquals("/px-api/locations/access-token", server.takeRequest().path)
        assertEquals("fresh-token", server.takeRequest().getHeader("x-bcrs-map-token"))
    }

    @Test fun `refreshes token near expiry before it fails in transit`() {
        server.enqueue(token("almost-expired", now + 20_000)); server.enqueue(response(stations))
        client.fetchRaw()
        enqueueHandshake("fresh-token"); client.fetchRaw()
        assertEquals(4, server.requestCount)
    }

    @Test fun `refreshes rejected station token once`() {
        server.enqueue(token("rejected")); server.enqueue(MockResponse().setResponseCode(403))
        enqueueHandshake("fresh-token")
        assertEquals(stations, client.fetchRaw())
        assertEquals(4, server.requestCount)
    }

    @Test fun `does not loop when refreshed token is also rejected`() {
        repeat(2) { server.enqueue(token("rejected")); server.enqueue(MockResponse().setResponseCode(403)) }
        val failure = runCatching { client.fetchRaw() }.exceptionOrNull()
        assertTrue(failure!!.message!!.contains("403 • station list"))
        assertEquals(4, server.requestCount)
    }

    @Test fun `reports denied token bootstrap without retrying`() {
        server.enqueue(MockResponse().setResponseCode(403))
        val failure = runCatching { client.fetchRaw() }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertTrue(failure!!.message!!.contains("403 • station token"))
        assertEquals(1, server.requestCount)
    }

    @Test fun `manual retry after denied bootstrap uses identical headers and redacted diagnostics`() {
        val events = mutableListOf<String>()
        client = StationApiClient(server.url("/px-api").toString(), "ReturnRightWatch/test",
            clock = { now }, diagnostic = events::add, warmUpFirstConnection = false)
        server.enqueue(MockResponse().setResponseCode(403))
        assertTrue(runCatching { client.fetchRaw() }.exceptionOrNull() is IOException)
        enqueueHandshake("private-example-token")
        assertEquals(stations, client.fetchRaw(Coordinate(1.2932, 103.852)))
        val failed = server.takeRequest()
        val successful = server.takeRequest()
        assertEquals(failed.headers, successful.headers)
        assertEquals(failed.path, successful.path)
        server.takeRequest()
        assertTrue(events.any { it.contains("status=403") })
        assertTrue(events.any { it.contains("status=200") })
        assertTrue(events.any { it.contains("Accept-Encoding=gzip") })
        assertTrue(events.any { it.contains("tokenPresent=true") })
        assertFalse(events.any { it.contains("private-example-token") || it.contains("1.2932") || it.contains("103.852") })
    }

    @Test fun `does not retry rate limiting`() {
        server.enqueue(token("example-token")); server.enqueue(MockResponse().setResponseCode(429))
        val failure = runCatching { client.fetchRaw() }.exceptionOrNull()
        assertTrue(failure!!.message!!.contains("429 • station list"))
        assertEquals(2, server.requestCount)
    }

    @Test fun `missing token stops before station requests`() {
        server.enqueue(response("""{"status":"ok","data":{}}"""))
        assertTrue(runCatching { client.fetchRaw() }.exceptionOrNull() is IOException)
        assertEquals(1, server.requestCount)
    }

    @Test fun `startup discards first connection and uses second connection for token and stations`() {
        client = StationApiClient(server.url("/px-api").toString(), "test", clock = { now })
        server.enqueue(MockResponse().setResponseCode(403))
        enqueueHandshake("second-connection-token")
        server.enqueue(response(stations))
        assertEquals(stations, client.fetchRaw())
        assertEquals(stations, client.fetchRaw())
        val warmup = server.takeRequest()
        val realToken = server.takeRequest()
        assertEquals(warmup.path, realToken.path)
        assertEquals(0, warmup.sequenceNumber)
        assertEquals(0, realToken.sequenceNumber)
        val list = server.takeRequest()
        assertEquals(1, list.sequenceNumber)
        assertEquals("second-connection-token", list.getHeader("x-bcrs-map-token"))
        assertEquals(2, server.takeRequest().sequenceNumber)
        assertEquals(4, server.requestCount)
    }

    @Test fun `successful warmup token is discarded too`() {
        client = StationApiClient(server.url("/px-api").toString(), "test", clock = { now })
        server.enqueue(token("discard-me"))
        enqueueHandshake("real-token")
        assertEquals(stations, client.fetchRaw())
        server.takeRequest()
        assertEquals(0, server.takeRequest().sequenceNumber)
        assertEquals("real-token", server.takeRequest().getHeader("x-bcrs-map-token"))
    }

    @Test fun `startup honors rate limiting instead of continuing on second connection`() {
        client = StationApiClient(server.url("/px-api").toString(), "test", clock = { now })
        server.enqueue(MockResponse().setResponseCode(429))
        assertTrue(runCatching { client.fetchRaw() }.exceptionOrNull()!!.message!!.contains("429"))
        assertEquals(1, server.requestCount)
    }
}
