package sg.returnright.watch.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

data class Position(val coordinate: Coordinate, val accuracy: Float, val elapsedRealtimeNanos: Long) {
    fun isFresh(): Boolean = SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos < 60_000_000_000L
}
data class LocationState(val position: Position? = null, val message: String? = "Waiting for GPS")
data class Heading(val magneticDegrees: Double? = null, val reliable: Boolean = false, val message: String? = null)

class WatchSensors(private val context: Context) {
    fun location() = callbackFlow {
        val manager = context.getSystemService(LocationManager::class.java)
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            trySend(LocationState(message = "Enable location to sort by distance"))
            close()
            return@callbackFlow
        }
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { manager.isProviderEnabled(it) }
        if (providers.isEmpty()) {
            trySend(LocationState(message = "Turn on watch location"))
            close()
            return@callbackFlow
        }
        var latest: Position? = null
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val position = Position(Coordinate(location.latitude, location.longitude),
                    location.accuracy, location.elapsedRealtimeNanos)
                // Prefer a recently received GPS fix over a less accurate network fix.
                if (latest?.isFresh() != true || position.accuracy <= (latest?.accuracy ?: Float.MAX_VALUE) ||
                    position.elapsedRealtimeNanos - (latest?.elapsedRealtimeNanos ?: 0) > 15_000_000_000L) {
                    latest = position
                    trySend(LocationState(position))
                }
            }
            override fun onProviderDisabled(provider: String) {
                trySend(LocationState(latest, "Location provider disabled"))
            }
            override fun onProviderEnabled(provider: String) = Unit
        }
        try {
            providers.mapNotNull { manager.getLastKnownLocation(it) }
                .maxByOrNull { it.elapsedRealtimeNanos }?.let(listener::onLocationChanged)
            providers.forEach { manager.requestLocationUpdates(it, 5_000, 3f, listener, Looper.getMainLooper()) }
        } catch (_: SecurityException) {
            trySend(LocationState(message = "Location permission unavailable"))
        }
        awaitClose { manager.removeUpdates(listener) }
    }

    fun heading() = callbackFlow {
        val manager = context.getSystemService(SensorManager::class.java)
        // Rotation vector uses the magnetometer; game rotation vector has no north reference.
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            trySend(Heading(message = "Compass sensor unavailable"))
            close()
            return@callbackFlow
        }
        val matrix = FloatArray(9)
        val remapped = FloatArray(9)
        val orientation = FloatArray(3)
        var filtered: Double? = null
        var accuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                val axes = when (context.display?.rotation ?: Surface.ROTATION_0) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, remapped)
                SensorManager.getOrientation(remapped, orientation)
                val raw = Geo.normalize(Math.toDegrees(orientation[0].toDouble()))
                filtered = filtered?.let { Geo.smooth(it, raw) } ?: raw
                val reliable = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                trySend(Heading(filtered, reliable, if (reliable) null else "Calibrate: move wrist in a figure 8"))
            }
            override fun onAccuracyChanged(sensor: Sensor?, next: Int) { accuracy = next }
        }
        if (!manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)) {
            trySend(Heading(message = "Could not start compass"))
            close()
            return@callbackFlow
        }
        awaitClose { manager.unregisterListener(listener) }
    }

    companion object {
        fun trueHeading(magnetic: Double, coordinate: Coordinate): Double {
            val field = GeomagneticField(coordinate.latitude.toFloat(), coordinate.longitude.toFloat(),
                0f, System.currentTimeMillis())
            return Geo.normalize(magnetic + field.declination)
        }
    }
}
