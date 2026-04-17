package moe.bemly.mrboto

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val threadIdGen = AtomicInteger(1)
private val activeThreads = ConcurrentHashMap<Int, Thread>()
private val atomicCounters = ConcurrentHashMap<Int, AtomicInteger>()

interface ThreadingMixin {
    val mruby: MRuby

    fun threadStart(callbackId: Int): Int {
        val id = threadIdGen.getAndIncrement()
        val mrubyRef = mruby
        val thread = Thread {
            mrubyRef.eval("Mrboto.dispatch_callback($callbackId)")
        }
        thread.start()
        activeThreads[id] = thread
        return id
    }

    fun threadJoin(threadId: Int) {
        activeThreads.remove(threadId)?.join(5000)
    }

    // Atomic operations
    fun atomicGet(counterId: Int): Int {
        return atomicCounters[counterId]?.get() ?: 0
    }

    fun atomicSet(counterId: Int, value: Int) {
        atomicCounters.getOrPut(counterId) { AtomicInteger(0) }.set(value)
    }

    fun atomicIncrement(counterId: Int): Int {
        return atomicCounters.getOrPut(counterId) { AtomicInteger(0) }.incrementAndGet()
    }
}
