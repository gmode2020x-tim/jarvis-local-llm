package ca.gmode.triprecorder.sync

import ca.gmode.triprecorder.BuildConfig
import ca.gmode.triprecorder.data.PointEntity
import ca.gmode.triprecorder.data.TripEntity
import org.json.JSONArray
import org.json.JSONObject

object UploadPayloadFactory {
    fun build(deviceId: String, trip: TripEntity, points: List<PointEntity>): String {
        val tripJson = JSONObject()
            .put("id", trip.id)
            .put("title", trip.title)
            .put("tripType", trip.tripType)
            .put("status", trip.status)
            .put("startAt", trip.startAt)
            .putNullable("endAt", trip.endAt)

        val pointsJson = JSONArray()
        points.forEach { point ->
            pointsJson.put(
                JSONObject()
                    .put("pointId", point.id)
                    .put("sequence", point.sequence)
                    .put("at", point.recordedAt)
                    .put("latitude", point.latitude)
                    .put("longitude", point.longitude)
                    .putNullable("accuracyMeters", point.accuracyMeters)
                    .putNullable("altitudeMeters", point.altitudeMeters)
                    .putNullable("verticalAccuracyMeters", point.verticalAccuracyMeters)
                    .putNullable("speedMps", point.speedMps)
                    .putNullable("bearingDegrees", point.bearingDegrees)
                    .putNullable("pressureHpa", point.pressureHpa)
                    .putNullable("accelerationRmsMs2", point.accelerationRmsMs2)
                    .putNullable("accelerationPeakMs2", point.accelerationPeakMs2)
                    .putNullable("gyroscopePeakRadS", point.gyroscopePeakRadS)
                    .putNullable("batteryPercent", point.batteryPercent)
                    .put("isCharging", point.isCharging)
                    .put("networkType", point.networkType)
                    .putNullable("satelliteCount", point.satelliteCount),
            )
        }

        return JSONObject()
            .put("protocolVersion", 1)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("deviceId", deviceId)
            .put("trip", tripJson)
            .put("points", pointsJson)
            .toString()
    }
}

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject = apply {
    if (value == null) put(name, JSONObject.NULL) else put(name, value)
}
