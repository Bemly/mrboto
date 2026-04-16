package moe.bemly.mrboto

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val sensorManagers = ConcurrentHashMap<Int, Pair<SensorManager, SensorEventListener>>()
private val sensorIdGen = AtomicInteger(1)

fun MrbotoActivityBase.startGyroscope(callbackId: Int): Int {
    return startSensor(Sensor.TYPE_GYROSCOPE, callbackId)
}

fun MrbotoActivityBase.stopGyroscope(sensorId: Int) {
    stopSensor(sensorId)
}

fun MrbotoActivityBase.startAccelerometer(callbackId: Int): Int {
    return startSensor(Sensor.TYPE_ACCELEROMETER, callbackId)
}

fun MrbotoActivityBase.stopAccelerometer(sensorId: Int) {
    stopSensor(sensorId)
}

fun MrbotoActivityBase.startProximity(callbackId: Int): Int {
    return startSensor(Sensor.TYPE_PROXIMITY, callbackId)
}

fun MrbotoActivityBase.stopProximity(sensorId: Int) {
    stopSensor(sensorId)
}

private fun MrbotoActivityBase.startSensor(type: Int, callbackId: Int): Int {
    return try {
        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(type) ?: return -1
        val id = sensorIdGen.getAndIncrement()
        val mrubyRef = mruby
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val args = event.values.joinToString(",") { it.toString() }
                mrubyRef.eval("Mrboto.dispatch_callback($callbackId, $args)")
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        sensorManagers[id] = Pair(sm, listener)
        id
    } catch (e: Exception) {
        Log.w("Mrboto", "startSensor failed: ${e.message}")
        -1
    }
}

private fun stopSensor(sensorId: Int) {
    val pair = sensorManagers.remove(sensorId) ?: return
    pair.first.unregisterListener(pair.second)
}
