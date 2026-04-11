package com.mrboto

import android.util.Log

/**
 * High-level Kotlin wrapper for the mruby JNI bridge.
 *
 * Usage:
 *   val mruby = MRuby()
 *   mruby.eval("1 + 2")          // => "3"
 *   mruby.evalBytecode(byteArray) // => result string
 *   mruby.close()
 *
 * Implements AutoCloseable for use in try-with-resources.
 */
class MRuby : AutoCloseable {

    companion object {
        private const val TAG = "mrboto"

        init {
            System.loadLibrary("mrboto-native")
            Log.i(TAG, "Native library loaded")
        }
    }

    /** Native pointer to the mrb_state (opaque to Kotlin). */
    private var mrbPtr: Long = 0L

    /** True if this instance has been opened and not yet closed. */
    val isOpen: Boolean
        get() = mrbPtr != 0L

    init {
        mrbPtr = nativeOpen()
        if (mrbPtr == 0L) {
            throw IllegalStateException("Failed to initialize mruby VM (out of memory?)")
        }
        Log.i(TAG, "mruby VM initialized at $mrbPtr")
    }

    /**
     * Evaluate a Ruby source code string.
     *
     * @param code Ruby source code (UTF-8)
     * @return String representation of the result, or an error message prefixed with "Error: "
     */
    fun eval(code: String): String {
        check(mrbPtr != 0L) { "mruby VM is not open" }
        return nativeEvalString(mrbPtr, code)
    }

    /**
     * Evaluate precompiled .mrb bytecode.
     *
     * @param bytecode Byte array containing compiled mruby bytecode (from mrbc)
     * @return String representation of the result, or an error message prefixed with "Error: "
     */
    fun evalBytecode(bytecode: ByteArray): String {
        check(mrbPtr != 0L) { "mruby VM is not open" }
        return nativeEvalBytecode(mrbPtr, bytecode)
    }

    /**
     * Get the mruby version string (e.g., "3.4.0").
     */
    fun version(): String {
        check(mrbPtr != 0L) { "mruby VM is not open" }
        return nativeVersion(mrbPtr)
    }

    /**
     * Run a full garbage collection cycle.
     * Usually not needed since mruby's GC runs automatically,
     * but useful for memory-constrained scenarios.
     */
    fun gc() {
        if (mrbPtr != 0L) {
            nativeGC(mrbPtr)
        }
    }

    /**
     * Close the mruby VM and free all associated resources.
     * After calling this, the instance cannot be used again.
     */
    override fun close() {
        if (mrbPtr != 0L) {
            nativeClose(mrbPtr)
            Log.i(TAG, "mruby VM closed")
            mrbPtr = 0L
        }
    }

    /** Prevent finalizer attack */
    protected fun finalize() {
        close()
    }

    // ── Native method declarations ─────────────────────────────────

    private external fun nativeOpen(): Long
    private external fun nativeClose(mrbPtr: Long)
    private external fun nativeEvalString(mrbPtr: Long, code: String): String
    private external fun nativeEvalBytecode(mrbPtr: Long, bytecode: ByteArray): String
    private external fun nativeVersion(mrbPtr: Long): String
    private external fun nativeGC(mrbPtr: Long)
}
