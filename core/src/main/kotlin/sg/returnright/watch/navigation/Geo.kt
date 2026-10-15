package sg.returnright.watch.navigation

import kotlin.math.*

data class Coordinate(val latitude: Double, val longitude: Double)
data class WorldPoint(val x: Double, val y: Double)

object Geo {
    private const val RADIUS = 6_371_008.8
    fun normalize(degrees: Double): Double = ((degrees % 360) + 360) % 360

    fun distance(from: Coordinate, to: Coordinate): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val a = (sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)).coerceIn(0.0, 1.0)
        return RADIUS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Initial great-circle bearing relative to true north. */
    fun bearing(from: Coordinate, to: Coordinate): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        return normalize(Math.toDegrees(atan2(sin(dLon) * cos(lat2),
            cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon))))
    }

    /** Signed shortest turn, including wrap-around at north. */
    fun turn(target: Double, heading: Double): Double = (normalize(target - heading) + 180) % 360 - 180
    fun smooth(previous: Double, next: Double, fraction: Double = 0.18): Double =
        normalize(previous + turn(next, previous) * fraction)

    fun world(coordinate: Coordinate, zoom: Int): WorldPoint {
        val n = 256.0 * (1 shl zoom)
        val lat = Math.toRadians(coordinate.latitude.coerceIn(-85.05112878, 85.05112878))
        return WorldPoint((coordinate.longitude + 180) / 360 * n,
            (1 - ln(tan(lat) + 1 / cos(lat)) / PI) / 2 * n)
    }

    fun coordinate(point: WorldPoint, zoom: Int): Coordinate {
        val n = 256.0 * (1 shl zoom)
        return Coordinate(Math.toDegrees(atan(sinh(PI * (1 - 2 * point.y.coerceIn(0.0, n) / n)))),
            ((point.x / n * 360) % 360 + 360) % 360 - 180)
    }
}
