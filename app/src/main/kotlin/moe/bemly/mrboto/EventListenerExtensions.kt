package moe.bemly.mrboto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.util.Log

private var batteryReceiver: BroadcastReceiver? = null
private var batteryCallbackId: Int = -1

fun MrbotoActivityBase.observeBattery(callbackId: Int): Boolean {
    return try {
        batteryCallbackId = callbackId
        batteryReceiver?.let { unregisterReceiver(it) }
        val mrubyRef = mruby
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val pct = if (scale > 0) (level * 100 / scale) else -1
                mrubyRef.eval("Mrboto.dispatch_callback($batteryCallbackId, $pct)")
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        true
    } catch (e: Exception) {
        Log.w("Mrboto", "observeBattery failed: ${e.message}")
        false
    }
}

fun MrbotoActivityBase.observeNetwork(callbackId: Int): Boolean {
    return try {
        val mrubyRef = mruby
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(network)
                val connected = caps != null
                val wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
                val mobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false
                mrubyRef.eval("Mrboto.dispatch_callback($callbackId, $connected, $wifi, $mobile)")
            }
        }
        registerReceiver(receiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        true
    } catch (e: Exception) {
        Log.w("Mrboto", "observeNetwork failed: ${e.message}")
        false
    }
}

fun MrbotoActivityBase.isNetworkConnected(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
