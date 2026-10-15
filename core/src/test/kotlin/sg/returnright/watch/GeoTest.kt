package sg.returnright.watch

import org.junit.Assert.*
import org.junit.Test
import sg.returnright.watch.navigation.*

class GeoTest {
    @Test fun `distance is zero for identical coordinates`() {
        assertEquals(0.0, Geo.distance(Coordinate(1.3, 103.8), Coordinate(1.3, 103.8)), 0.001)
    }
    @Test fun `one degree along equator is about 111 km`() {
        assertEquals(111_195.0, Geo.distance(Coordinate(0.0, 0.0), Coordinate(0.0, 1.0)), 1.0)
    }
    @Test fun `antipodal points do not produce NaN`() {
        assertEquals(20_015_114.4, Geo.distance(Coordinate(0.0, 0.0), Coordinate(0.0, 180.0)), 1.0)
    }
    @Test fun `bearing uses all four quadrants`() {
        val origin = Coordinate(0.0, 0.0)
        assertEquals(0.0, Geo.bearing(origin, Coordinate(1.0, 0.0)), .001)
        assertEquals(90.0, Geo.bearing(origin, Coordinate(0.0, 1.0)), .001)
        assertEquals(180.0, Geo.bearing(origin, Coordinate(-1.0, 0.0)), .001)
        assertEquals(270.0, Geo.bearing(origin, Coordinate(0.0, -1.0)), .001)
    }
    @Test fun `arrow crosses north using the shortest turn`() {
        assertEquals(2.0, Geo.turn(1.0, 359.0), .001)
        assertEquals(-2.0, Geo.turn(359.0, 1.0), .001)
        assertEquals(90.0, Geo.turn(0.0, 270.0), .001)
    }
    @Test fun `heading smoothing never spins backwards across north`() {
        assertEquals(359.36, Geo.smooth(359.0, 1.0), .001)
    }
    @Test fun `map projection round trips Singapore at every supported zoom`() {
        val original = Coordinate(1.2932, 103.8520)
        for (zoom in 11..18) {
            val restored = Geo.coordinate(Geo.world(original, zoom), zoom)
            assertEquals(original.latitude, restored.latitude, .0000001)
            assertEquals(original.longitude, restored.longitude, .0000001)
        }
    }
    @Test fun `polar projection stays finite`() {
        assertTrue(Geo.world(Coordinate(90.0, 180.0), 16).y.isFinite())
    }
}
