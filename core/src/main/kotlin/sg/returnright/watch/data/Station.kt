package sg.returnright.watch.data

import java.util.Locale

enum class StationStatus(val label: String) {
    RUNNING("Running"), FULL("Full"), OFFLINE("Offline"),
    MAINTENANCE("Maintenance"), ERROR("Fault"), UNKNOWN("Unknown");

    companion object {
        fun from(raw: String): StationStatus = when (raw.trim().uppercase(Locale.ROOT)) {
            "RUNNING" -> RUNNING
            "FULL" -> FULL
            "OFFLINE" -> OFFLINE
            "MAINTENANCE" -> MAINTENANCE
            "ERROR" -> ERROR
            else -> UNKNOWN
        }
    }
}

data class Station(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val postalCode: String,
    val status: StationStatus,
    val hours: String,
    val remarks: String,
)

data class StationSnapshot(val stations: List<Station>, val fetchedAt: Long)

/** Synthetic stations for UI exploration, never mixed into the live feed. */
val demoStations = listOf(
    Station("demo-1", "Demo • City Hall", 1.2932, 103.8520, "Synthetic station near City Hall", "", StationStatus.RUNNING, "Demo hours: 24 hours", "Example only"),
    Station("demo-2", "Demo • Bras Basah", 1.2988, 103.8506, "Synthetic station near Bras Basah", "", StationStatus.FULL, "Demo hours: 07:00–23:00", "Example only"),
    Station("demo-3", "Demo • Bugis", 1.3005, 103.8550, "Synthetic station near Bugis", "", StationStatus.MAINTENANCE, "Demo hours: 24 hours", "Example only"),
    Station("demo-4", "Demo • Esplanade", 1.2938, 103.8555, "Synthetic station near Esplanade", "", StationStatus.OFFLINE, "Demo hours: 24 hours", "Example only"),
)
