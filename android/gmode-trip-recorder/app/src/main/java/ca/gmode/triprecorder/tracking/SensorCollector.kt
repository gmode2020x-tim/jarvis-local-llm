package ca.gmode.triprecorder.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import ca.gmode.triprecorder.data.SensorSnapshot
import kotlin.math.abs
import kotlin.math.sqrt

data class OrientationSnapshot(
    val pitchDegrees: Double?,
    val rollDegrees: Double?,
)

class SensorCollector(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelerationSensor = linearAccelerationSensor ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private var pressureTotal = 0.0
    private var pressureSamples = 0
    private var accelerationSquaredTotal = 0.0
    private var accelerationSamples = 0
    private var accelerationPeak = 0.0
    private var gyroscopePeak = 0.0
    private var pitchDegrees: Double? = null
    private var rollDegrees: Double? = null

    fun start() {
        pressureSensor?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        accelerationSensor?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        gyroscopeSensor?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        rotationVectorSensor?.also { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    @Synchronized
    fun snapshotAndReset(): SensorSnapshot {
        val snapshot = SensorSnapshot(
            pressureHpa = if (pressureSamples > 0) pressureTotal / pressureSamples else null,
            accelerationRmsMs2 = if (accelerationSamples > 0) {
                sqrt(accelerationSquaredTotal / accelerationSamples)
            } else {
                null
            },
            accelerationPeakMs2 = accelerationPeak.takeIf { accelerationSamples > 0 },
            gyroscopePeakRadS = gyroscopePeak.takeIf { gyroscopePeak > 0 },
        )
        pressureTotal = 0.0
        pressureSamples = 0
        accelerationSquaredTotal = 0.0
        accelerationSamples = 0
        accelerationPeak = 0.0
        gyroscopePeak = 0.0
        return snapshot
    }

    @Synchronized
    fun orientation(): OrientationSnapshot = OrientationSnapshot(pitchDegrees, rollDegrees)

    @Synchronized
    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_PRESSURE -> {
                pressureTotal += event.values[0].toDouble()
                pressureSamples += 1
            }

            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> {
                val magnitude = vectorMagnitude(event.values)
                val linearMagnitude = if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    abs(magnitude - SensorManager.GRAVITY_EARTH)
                } else {
                    magnitude
                }
                accelerationSquaredTotal += linearMagnitude * linearMagnitude
                accelerationSamples += 1
                accelerationPeak = maxOf(accelerationPeak, linearMagnitude)
            }

            Sensor.TYPE_GYROSCOPE -> gyroscopePeak = maxOf(gyroscopePeak, vectorMagnitude(event.values))

            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotation = FloatArray(9)
                val orientation = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                pitchDegrees = Math.toDegrees(orientation[1].toDouble())
                rollDegrees = Math.toDegrees(orientation[2].toDouble())
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun vectorMagnitude(values: FloatArray): Double = sqrt(
        values.take(3).sumOf { value -> value.toDouble() * value.toDouble() },
    )
}
