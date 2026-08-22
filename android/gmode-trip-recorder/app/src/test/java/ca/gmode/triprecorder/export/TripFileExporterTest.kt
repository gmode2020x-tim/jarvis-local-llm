package ca.gmode.triprecorder.export

import ca.gmode.triprecorder.data.PointEntity
import ca.gmode.triprecorder.data.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TripFileExporterTest {
    private val trip = TripEntity(
        id = "trip-1",
        title = "Lake & Ridge <run>",
        tripType = "off_road",
        status = "complete",
        startAt = "2026-08-22T14:00:00Z",
        endAt = "2026-08-22T15:00:00Z",
        updatedAtEpochMs = 1L,
        distanceMeters = 1234.5,
        pointCount = 2,
    )
    private val points = listOf(point(sequence = 1, latitude = 44.2), point(sequence = 0, latitude = 44.1))

    @Test
    fun gpxIsStandardsBasedEscapedAndSequenceOrdered() {
        val output = TripFileExporter.render(trip, points, TripExportFormat.GPX)

        assertTrue(output.contains("<gpx version=\"1.1\""))
        assertTrue(output.contains("<name>Lake &amp; Ridge &lt;run&gt;</name>"))
        assertTrue(output.contains("<ele>250.0</ele>"))
        assertTrue(output.contains("<gmode:accuracyMeters>4.0</gmode:accuracyMeters>"))
        assertTrue(output.indexOf("lat=\"44.1\"") < output.indexOf("lat=\"44.2\""))
    }

    @Test
    fun kmlUsesTimestampedGoogleEarthTrack() {
        val output = TripFileExporter.render(trip, points, TripExportFormat.KML)

        assertTrue(output.contains("xmlns:gx=\"http://www.google.com/kml/ext/2.2\""))
        assertTrue(output.contains("<gx:Track>"))
        assertTrue(output.contains("<when>2026-08-22T14:00:00Z</when>"))
        assertTrue(output.contains("<gx:coord>-79.0 44.1 250.0</gx:coord>"))
    }

    @Test
    fun geoJsonContainsLineGeometryTimesAndNullableTelemetry() {
        val output = TripFileExporter.render(trip.copy(title = "Quoted \"trip\"\nname"), points, TripExportFormat.GEOJSON)

        assertTrue(output.contains("\"type\": \"FeatureCollection\""))
        assertTrue(output.contains("\"name\": \"Quoted \\\"trip\\\"\\nname\""))
        assertTrue(output.contains("\"coordTimes\""))
        assertTrue(output.contains("[-79.0,44.1,250.0]"))
        assertTrue(output.contains("\"speedMps\": [7.0,7.0]"))
    }

    @Test
    fun csvPreservesTelemetryAndQuotesSpreadsheetText() {
        val output = TripFileExporter.render(trip.copy(title = "Trip, \"quoted\""), points, TripExportFormat.CSV)
        val lines = output.lines().filter { it.isNotEmpty() }

        assertEquals(3, lines.size)
        assertTrue(lines.first().startsWith("trip_id,trip_title,trip_type,sequence"))
        assertTrue(lines[1].contains("\"Trip, \"\"quoted\"\"\""))
        assertTrue(lines[1].contains(",1013.25,"))
        assertTrue(lines[1].endsWith(",false"))
    }

    @Test
    fun filenameIsPortableAndFormatSpecific() {
        val name = TripFileExporter.suggestedFileName(trip.copy(title = "Trail: North / Test"), TripExportFormat.GPX)

        assertEquals("GMODE_2026-08-22_Trail_North_Test.gpx", name)
        assertFalse(name.contains(':'))
        assertEquals(TripExportFormat.KML, TripExportFormat.fromId("kml"))
        assertEquals(null, TripExportFormat.fromId("unknown"))
    }

    @Test
    fun twentyFiveThousandPointGpxExportKeepsEveryFix() {
        val longTripPoints = List(25_001) { index ->
            point(sequence = index.toLong(), latitude = 44.0 + index * 0.000001)
        }

        val output = TripFileExporter.render(
            trip.copy(pointCount = longTripPoints.size),
            longTripPoints.reversed(),
            TripExportFormat.GPX,
        )

        assertEquals(25_001, Regex("<trkpt ").findAll(output).count())
        assertTrue(output.indexOf("lat=\"44.0\"") < output.indexOf("lat=\"44.025\""))
        assertTrue(output.endsWith("</gpx>\n"))
    }

    private fun point(sequence: Long, latitude: Double) = PointEntity(
        id = "trip-1:$sequence",
        tripId = "trip-1",
        sequence = sequence,
        recordedAt = Instant.parse("2026-08-22T14:00:00Z").plusSeconds(sequence).toString(),
        latitude = latitude,
        longitude = -79.0,
        accuracyMeters = 4.0,
        altitudeMeters = 250.0,
        verticalAccuracyMeters = 2.0,
        speedMps = 7.0,
        bearingDegrees = 90.0,
        pressureHpa = 1013.25,
        accelerationRmsMs2 = 0.1,
        accelerationPeakMs2 = 0.2,
        gyroscopePeakRadS = 0.03,
        batteryPercent = 75.0,
        isCharging = false,
        networkType = "offline",
        satelliteCount = 10,
        synced = false,
    )
}
