package sg.returnright.watch

import org.junit.Assert.*
import org.junit.Test
import sg.returnright.watch.data.*

class StationParserTest {
    private fun feed(records: String) = """{"status":"ok","data":[$records]}"""
    private fun record(id: String = "10", lat: String = "1.323937", lon: String = "103.867800", status: String = "RUNNING") =
        """{"id":$id,"locationName":"Example station","latitude":"$lat","longitude":"$lon","status":"$status","address":"54 Geylang Bahru","postalCode":"330054","rvmOpeningHours":"24 Hrs"}"""

    @Test fun `parses observed API fields and string coordinates`() {
        val station = StationParser.parse(feed(record())).single()
        assertEquals("10", station.id)
        assertEquals(1.323937, station.latitude, .0000001)
        assertEquals(StationStatus.RUNNING, station.status)
        assertEquals("24 Hrs", station.hours)
    }
    @Test fun `offline casing is normalized and unknown states stay unknown`() {
        assertEquals(StationStatus.OFFLINE, StationStatus.from(" offline "))
        assertEquals(StationStatus.UNKNOWN, StationStatus.from("NEW_VENDOR_STATE"))
    }
    @Test fun `invalid coordinates and unprovisioned entries are omitted`() {
        val raw = feed(listOf(record(lat = ""), record(lat = "NaN"), record(lat = "0", lon = "0"),
            record(lat = "90"), "{}", "null", record()).joinToString(","))
        assertEquals(1, StationParser.parse(raw).size)
    }
    @Test fun `duplicate machine ids do not create duplicate list keys`() {
        assertEquals(1, StationParser.parse(feed("${record()},${record()}")).size)
    }
    @Test fun `JSON null identity fields do not become phantom stations`() {
        val nullName = record().replace("\"Example station\"", "null")
        assertTrue(StationParser.parse(feed("${record(id = "null")},$nullName")).isEmpty())
    }
    @Test(expected = IllegalArgumentException::class)
    fun `error envelopes are never presented as successful data`() {
        StationParser.parse("""{"status":"error","data":[]}""")
    }
}
