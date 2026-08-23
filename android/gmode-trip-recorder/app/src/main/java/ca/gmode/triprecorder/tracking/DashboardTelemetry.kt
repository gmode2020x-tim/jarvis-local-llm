package ca.gmode.triprecorder.tracking

import ca.gmode.triprecorder.data.SensorSnapshot

object DashboardTelemetry {
    fun merge(
        stored: LiveTelemetry,
        activeTripId: String?,
        sensors: SensorSnapshot?,
        orientation: OrientationSnapshot,
        batteryPercent: Int?,
    ): LiveTelemetry {
        val storedIsCurrent = activeTripId != null && stored.tripId == activeTripId
        return stored.copy(
            pressureHpa = sensors?.pressureHpa ?: stored.pressureHpa.takeIf { storedIsCurrent },
            accelerationPeakMs2 = sensors?.accelerationPeakMs2
                ?: stored.accelerationPeakMs2.takeIf { storedIsCurrent },
            batteryPercent = batteryPercent?.toDouble() ?: stored.batteryPercent.takeIf { storedIsCurrent },
            pitchDegrees = orientation.pitchDegrees ?: stored.pitchDegrees.takeIf { storedIsCurrent },
            rollDegrees = orientation.rollDegrees ?: stored.rollDegrees.takeIf { storedIsCurrent },
            magneticHeadingDegrees = orientation.magneticHeadingDegrees,
        )
    }
}
