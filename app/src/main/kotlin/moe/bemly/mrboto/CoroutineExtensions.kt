package moe.bemly.mrboto

import android.os.Handler
import android.os.Looper
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private val bgExecutor = Executors.newCachedThreadPool()
private val mainHandler = Handler(Looper.getMainLooper())
private val activeTimers = ConcurrentHashMap<Int, Timer>()
private val timerIdGen = AtomicInteger(1)

fun MrbotoActivityBase.runAsync(callbackId: Int) {
    bgExecutor.execute {
        Mrboto.dispatch_callback(callbackId)
    }
}

fun MrbotoActivityBase.runDelayed(callbackId: Int, delayMs: Int) {
    mainHandler.postDelayed({
        Mrboto.dispatch_callback(callbackId)
    }, delayMs.toLong())
}

fun MrbotoActivityBase.timerStart(callbackId: Int, intervalMs: Int): Int {
    val id = timerIdGen.getAndIncrement()
    val timer = Timer()
    timer.scheduleAtFixedRate(object : TimerTask() {
        override fun run() {
            mainHandler.post { Mrboto.dispatch_callback(callbackId) }
        }
    }, intervalMs.toLong(), intervalMs.toLong())
    activeTimers[id] = timer
    return id
}

fun MrbotoActivityBase.timerStop(timerId: Int) {
    activeTimers.remove(timerId)?.cancel()
}

fun MrbotoActivityBase.timerOnce(callbackId: Int, delayMs: Int): Int {
    val id = timerIdGen.getAndIncrement()
    val timer = Timer()
    timer.schedule(object : TimerTask() {
        override fun run() {
            mainHandler.post { Mrboto.dispatch_callback(callbackId) }
            activeTimers.remove(id)
        }
    }, delayMs.toLong())
    activeTimers[id] = timer
    return id
}
