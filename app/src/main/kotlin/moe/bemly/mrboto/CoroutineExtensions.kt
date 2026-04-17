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

interface CoroutineMixin {
    val mruby: MRuby

    fun runAsync(callbackId: Int) {
        val mrubyRef = mruby
        bgExecutor.execute {
            mrubyRef.eval("Mrboto.dispatch_callback($callbackId)")
        }
    }

    fun runDelayed(callbackId: Int, delayMs: Int) {
        val mrubyRef = mruby
        mainHandler.postDelayed({
            mrubyRef.eval("Mrboto.dispatch_callback($callbackId)")
        }, delayMs.toLong())
    }

    fun timerStart(callbackId: Int, intervalMs: Int): Int {
        val id = timerIdGen.getAndIncrement()
        val mrubyRef = mruby
        val timer = Timer()
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                mainHandler.post { mrubyRef.eval("Mrboto.dispatch_callback($callbackId)") }
            }
        }, intervalMs.toLong(), intervalMs.toLong())
        activeTimers[id] = timer
        return id
    }

    fun timerStop(timerId: Int) {
        activeTimers.remove(timerId)?.cancel()
    }

    fun timerOnce(callbackId: Int, delayMs: Int): Int {
        val id = timerIdGen.getAndIncrement()
        val mrubyRef = mruby
        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                mainHandler.post { mrubyRef.eval("Mrboto.dispatch_callback($callbackId)") }
                activeTimers.remove(id)
            }
        }, delayMs.toLong())
        activeTimers[id] = timer
        return id
    }
}
