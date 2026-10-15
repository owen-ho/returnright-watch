package sg.returnright.watch.data

import org.json.JSONObject

object StationParser {
    fun parse(raw: String): List<Station> {
        val root = JSONObject(raw)
        require(root.optString("status") == "ok") { "Station feed did not report success" }
        val records = root.getJSONArray("data")
        return buildList {
            for (i in 0 until records.length()) {
                val record = records.optJSONObject(i) ?: continue
                val lat = record.optString("latitude").toDoubleOrNull() ?: continue
                val lon = record.optString("longitude").toDoubleOrNull() ?: continue
                // Unprovisioned entries sometimes carry empty/zero coordinates.
                if (!lat.isFinite() || !lon.isFinite() || lat !in 1.0..1.6 || lon !in 103.5..104.2) continue
                val name = if (record.isNull("locationName")) "" else record.optString("locationName").trim()
                val id = if (record.isNull("id")) "" else record.optString("id").trim()
                if (name.isEmpty() || id.isEmpty()) continue
                add(Station(id, name, lat, lon, record.optString("address"),
                    record.optString("postalCode"), StationStatus.from(record.optString("status")),
                    record.optString("rvmOpeningHours"), record.optString("rvm_remarks")))
            }
        }.distinctBy { it.id }
    }
}
