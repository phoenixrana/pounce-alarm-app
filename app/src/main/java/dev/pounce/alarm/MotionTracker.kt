package dev.pounce.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.sqrt

/** Mission sensors run only while the visible activity is resumed. No background tracking. */
internal class MotionTracker(context: Context, private val kind: MissionKind, private val onPulse: () -> Unit,
    private val onTilt: (Float, Float) -> Unit = { _, _ -> }) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var selected: Sensor? = null
    private var lastPulse = 0L
    private var gravity = 9.81f
    private var above = false
    private var near = false
    private var nearAt = 0L
    private var stepBase: Float? = null
    var mode: String = ""
        private set

    fun start(): Boolean {
        stop()
        selected = when (kind) {
            MissionKind.WALK -> manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
                ?: manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
                ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            MissionKind.PUSHUPS -> manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            else -> manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        }
        val sensor = selected ?: return false
        mode = when (sensor.type) {
            Sensor.TYPE_STEP_DETECTOR, Sensor.TYPE_STEP_COUNTER -> "Step sensor"
            Sensor.TYPE_PROXIMITY -> "Near / far sensor"
            else -> if (kind == MissionKind.WALK) "Motion-based step estimate" else "Motion sensor"
        }
        return try { manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME) }
        catch (_: SecurityException) {
            if (kind != MissionKind.WALK) false else {
                selected = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                mode = "Motion-based step estimate"
                selected?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) } ?: false
            }
        }
    }
    fun stop() { manager.unregisterListener(this); stepBase = null; near = false; above = false; lastPulse = SystemClock.elapsedRealtime(); gravity=9.81f }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onSensorChanged(event: SensorEvent) {
        val now = SystemClock.elapsedRealtime()
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> if (event.values[0] >= 1f) onPulse()
            Sensor.TYPE_STEP_COUNTER -> {
                val previous = stepBase
                stepBase = event.values[0]
                if (previous != null) repeat((event.values[0] - previous).toInt().coerceIn(0, 10)) { onPulse() }
            }
            Sensor.TYPE_PROXIMITY -> {
                val isNear = event.values[0] < minOf(event.sensor.maximumRange, 4f)
                if (isNear && !near) nearAt = now
                if (!isNear && near && now - nearAt >= 200 && now - lastPulse >= 1200) { lastPulse = now; onPulse() }
                near = isNear
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x=event.values[0]; val y=event.values[1]; val z=event.values[2]
                if (kind == MissionKind.TILT) { onTilt((-x/5f).coerceIn(-1f,1f), (y/5f).coerceIn(-1f,1f)); return }
                val magnitude=sqrt(x*x+y*y+z*z)
                gravity=.92f*gravity+.08f*magnitude
                val delta=magnitude-gravity
                val threshold=if(kind==MissionKind.SHAKE) 7f else 1.6f
                val high=if(kind==MissionKind.SHAKE) abs(delta)>threshold else delta>threshold
                val gap=if(kind==MissionKind.SHAKE) 450 else 420
                if(high && !above && now-lastPulse>=gap) { lastPulse=now; onPulse() }
                above=high
            }
        }
    }
}
