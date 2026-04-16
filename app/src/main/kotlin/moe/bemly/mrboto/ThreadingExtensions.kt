package moe.bemly.mrboto

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val threadIdGen = AtomicInteger(1)
private val activeThreads = ConcurrentHashMap<Int, Thread>()
private val atomicCounters = ConcurrentHashMap<Int, AtomicInteger>()

fun MrbotoActivityBase.threadStart(callbackId: Int): Int {
    val id = threadIdGen.getAndIncrement()
    val thread = Thread {
        Mrboto.dispatch_callback(callbackId)
    }
    thread.start()
    activeThreads[id] = thread
    return id
}

fun MrbotoActivityBase.threadJoin(threadId: Int) {
    activeThreads.remove(threadId)?.join(5000)
}

// Atomic operations
fun MrbotoActivityBase.atomicGet(counterId: Int): Int {
    return atomicCounters[counterId]?.get() ?: 0
}

fun MrbotoActivityBase.atomicSet(counterId: Int, value: Int) {
    atomicCounters.getOrPut(counterId) { AtomicInteger(0) }.set(value)
}

fun MrbotoActivityBase.atomicIncrement(counterId: Int): Int {
    return atomicCounters.getOrPut(counterId) { AtomicInteger(0) }.incrementAndGet()
}
