package moe.bemly.mrboto

import android.util.Log

/**
 * High-level Kotlin wrapper for the mruby JNI bridge.
 *
 * Usage:
 *   val mruby = MRuby()
 *   mruby.eval("1 + 2")          // => "3"
 *   mruby.loadAssetScript(assets, "mrboto/core.rb")
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

    // ── Core eval API ────────────────────────────────────────────────

    fun eval(code: String): String {
        check(mrbPtr != 0L) { "mruby VM is not open" }
        return nativeEvalString(mrbPtr, code)
    }

    fun evalBytecode(bytecode: ByteArray): String {
        check(mrbPtr != 0L) { "mruby VM is not open" }
        return nativeEvalBytecode(mrbPtr, bytecode)
    }

    fun version(): String {
        check(mrbPtr != 0L) { "mruby VM is not open" }
        return nativeVersion(mrbPtr)
    }

    fun gc() {
        if (mrbPtr != 0L) {
            nativeGC(mrbPtr)
        }
    }

    // ── Framework API ────────────────────────────────────────────────

    /**
     * Register Android-specific classes in the mruby VM.
     * Must be called before loading any mrboto Ruby scripts.
     */
    fun registerAndroidClasses() {
        check(mrbPtr != 0L) { "mruby VM is not open" }
        nativeRegisterAndroidClasses(mrbPtr)
    }

    /**
     * Dispatch a lifecycle callback to a Ruby Activity instance.
     *
     * @param rubyInstanceId the mruby-side registry ID of the Activity
     * @param callbackName e.g. "on_create", "on_resume"
     * @param argsId optional registry ID for Bundle argument
     * @return "ok" on success, or error message
     */
    fun dispatchLifecycle(rubyInstanceId: Int, callbackName: String, argsId: Int = 0): String {
        check(mrbPtr != 0L) { "mruby VM is not open" }
        return nativeDispatchLifecycle(mrbPtr, rubyInstanceId, callbackName, argsId)
    }

    /**
     * Load and execute a Ruby source script.
     *
     * @return actual evaluation result (last expression), or error message
     */
    fun loadScript(script: String): String {
        check(mrbPtr != 0L) { "mruby VM is not open" }
        return nativeLoadScript(mrbPtr, script)
    }

    /**
     * Convenience: load a Ruby script from Android assets.
     */
    fun loadAssetScript(assets: android.content.res.AssetManager, path: String): String {
        val script = assets.open(path).bufferedReader().use { it.readText() }
        return loadScript(script)
    }

    /**
     * Register a Java object in the JNI reference registry.
     * Returns the integer registry ID.
     */
    fun registerJavaObject(obj: Any): Int {
        check(mrbPtr != 0L) { "mruby VM is not open" }
        return nativeRegisterObject(mrbPtr, obj)
    }

    /**
     * Look up a Java object by its registry ID.
     * Returns null if the ID is invalid.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> lookupJavaObject(registryId: Int): T? {
        check(mrbPtr != 0L) { "mruby VM is not open" }
        return nativeLookupObject(mrbPtr, registryId) as T?
    }

    /**
     * Set OnClickListener on a View with a mruby callback ID.
     */
    fun setOnClick(viewId: Int, callbackId: Int) {
        check(mrbPtr != 0L) { "mruby VM is not open" }
        nativeSetOnClick(mrbPtr, viewId, callbackId)
    }

    /**
     * Call setContentView on the Activity.
     */
    fun setContentView(activityId: Int, viewId: Int) {
        check(mrbPtr != 0L) { "mruby VM is not open" }
        nativeSetContentView(mrbPtr, activityId, viewId)
    }

    // ── Cleanup ──────────────────────────────────────────────────────

    override fun close() {
        if (mrbPtr != 0L) {
            nativeClose(mrbPtr)
            Log.i(TAG, "mruby VM closed")
            mrbPtr = 0L
        }
    }

    protected fun finalize() {
        close()
    }

    // ── Native method declarations ───────────────────────────────────

    private external fun nativeOpen(): Long
    private external fun nativeClose(mrbPtr: Long)
    private external fun nativeEvalString(mrbPtr: Long, code: String): String
    private external fun nativeEvalBytecode(mrbPtr: Long, bytecode: ByteArray): String
    private external fun nativeVersion(mrbPtr: Long): String
    private external fun nativeGC(mrbPtr: Long)

    // Framework externals (implemented in android-jni-bridge.c)
    private external fun nativeRegisterAndroidClasses(mrbPtr: Long)
    private external fun nativeDispatchLifecycle(
        mrbPtr: Long, rubyInstanceId: Int, callbackName: String, argsId: Int
    ): String
    private external fun nativeLoadScript(mrbPtr: Long, script: String): String
    private external fun nativeRegisterObject(mrbPtr: Long, obj: Any): Int
    private external fun nativeLookupObject(mrbPtr: Long, registryId: Int): Any?
    private external fun nativeSetOnClick(mrbPtr: Long, viewId: Int, callbackId: Int)
    private external fun nativeSetContentView(mrbPtr: Long, activityId: Int, viewId: Int)
}
