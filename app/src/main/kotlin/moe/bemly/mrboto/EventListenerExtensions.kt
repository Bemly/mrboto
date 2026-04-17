package moe.bemly.mrboto

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.util.Log

private var batteryReceiver: BroadcastReceiver? = null
private var batteryCallbackId: Int = -1
private var networkCallback: ConnectivityManager.NetworkCallback? = null

interface EventListenerMixin {
    val mruby: MRuby

    fun observeBattery(callbackId: Int): Boolean {
        val activity = this as Activity
        return try {
            batteryCallbackId = callbackId
            batteryReceiver?.let { activity.unregisterReceiver(it) }
            val mrubyRef = mruby
            batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val pct = if (scale > 0) (level * 100 / scale) else -1
                    mrubyRef.eval("Mrboto.dispatch_callback($batteryCallbackId, $pct)")
                }
            }
            activity.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "observeBattery failed: ${e.message}")
            false
        }
    }

    fun observeNetwork(callbackId: Int): Boolean {
        val activity = this as Activity
        return try {
            val mrubyRef = mruby
            val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let { cm.unregisterNetworkCallback(it) }
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    mrubyRef.eval("Mrboto.dispatch_callback($callbackId, true)")
                }
                override fun onLost(network: Network) {
                    mrubyRef.eval("Mrboto.dispatch_callback($callbackId, false)")
                }
            }
            networkCallback = cb
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, cb)
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "observeNetwork failed: ${e.message}")
            false
        }
    }

    fun isNetworkConnected(): Boolean {
        val activity = this as Activity
        val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
