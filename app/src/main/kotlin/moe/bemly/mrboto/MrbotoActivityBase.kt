package moe.bemly.mrboto

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import moe.bemly.mrboto.MrbotoCheckChangeListener
import moe.bemly.mrboto.MrbotoClickListener
import moe.bemly.mrboto.MrbotoTextWatcher

/**
 * Base class for Ruby-backed Activities.
 *
 * Subclasses must override `getScriptPath()` to return the
 * asset path of the Ruby script (e.g. "main_activity.rb").
 *
 * All lifecycle callbacks are delegated to mruby. The Ruby
 * script defines a class inheriting from Mrboto::Activity
 * and overrides the lifecycle methods.
 */
abstract class MrbotoActivityBase : Activity() {

    companion object {
        private const val TAG = "MrbotoActivity"
    }

    internal lateinit var mruby: MRuby
    protected var rubyInstanceId: Int = 0

    /** Expose mruby to subclasses in other modules */
    protected fun getMRuby(): MRuby = mruby

    /** Override to return the Ruby script asset path */
    protected abstract fun getScriptPath(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        // Get or create the MRuby instance from the Application
        mruby = (application as? MrbotoApplication)?.mruby
            ?: run {
                val m = MRuby()
                m.registerAndroidClasses()
                loadMrbotoCore(assets, m)
                (application as? MrbotoApplication)?.let { it.mruby = m }
                m
            }

        super.onCreate(savedInstanceState)

        // Wrap 'this' Activity as a JavaObject in mruby
        val activityRefId = mruby.registerJavaObject(this)

        // Set the Java activity reference BEFORE loading the script
        mruby.eval("Mrboto.current_activity_id = $activityRefId")

        // Load the Ruby script (should define a class inheriting Mrboto::Activity)
        try {
            val script = assets.open(getScriptPath()).bufferedReader().use { it.readText() }
            val result = mruby.loadScript(script)
            if (result != "ok") {
                Log.e(TAG, "Script load error: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load script ${getScriptPath()}: ${e.message}")
        }

        // Debug: check what the script defined
        val checkResult = mruby.eval("Mrboto.class.to_s rescue 'error:' + $!.message")
        Log.i(TAG, "Debug: Mrboto = $checkResult")

        // Instantiate the Ruby Activity class
        val instantiateResult = mruby.eval(
            "klass = Mrboto._ruby_activity_class\n" +
            "if klass\n" +
            "  act = klass.new(Mrboto.current_activity_id)\n" +
            "  Mrboto.current_activity = act\n" +
            "  'instantiated'\n" +
            "else\n" +
            "  'no class defined'\n" +
            "end"
        )
        Log.i(TAG, "Ruby activity instantiation: $instantiateResult")

        // Dispatch on_create (bundle will be passed as argument to the Ruby method)
        val bundleId = if (savedInstanceState != null) {
            mruby.registerJavaObject(savedInstanceState)
        } else {
            0
        }
        val dispatchResult = mruby.dispatchLifecycle(activityRefId, "on_create", bundleId)
        if (dispatchResult != "ok") {
            Log.e(TAG, "on_create dispatch error: $dispatchResult")
        }
        rubyInstanceId = activityRefId

        // Check for widget creation errors (after dispatch, since that's when widgets are created)
        val widgetError = mruby.eval($$"$mrboto_widget_error.to_s rescue ''")
        if (widgetError.isNotEmpty()) {
            Log.e(TAG, "Widget creation error: $widgetError")
        }

        Log.i(TAG, "Activity created, script: ${getScriptPath()}")
    }

    override fun onResume() {
        super.onResume()
        mruby.dispatchLifecycle(rubyInstanceId, "on_resume")
    }

    override fun onPause() {
        mruby.dispatchLifecycle(rubyInstanceId, "on_pause")
        super.onPause()
    }

    override fun onStop() {
        mruby.dispatchLifecycle(rubyInstanceId, "on_stop")
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        mruby.dispatchLifecycle(rubyInstanceId, "on_start")
    }

    override fun onDestroy() {
        mruby.dispatchLifecycle(rubyInstanceId, "on_destroy")
        super.onDestroy()
    }

    /**
     * Load mrboto core scripts into a fresh MRuby instance.
     */
    private fun loadMrbotoCore(assets: android.content.res.AssetManager, mruby: MRuby) {
        val coreFiles = listOf(
            "mrboto/core.rb",
            "mrboto/layout.rb",
            "mrboto/activity.rb",
            "mrboto/widgets.rb",
            "mrboto/helpers.rb"
        )
        for (file in coreFiles) {
            mruby.loadAssetScript(assets, file)
        }
    }

    /**
     * Set a click listener on a View that dispatches to mruby.
     * Called from Ruby DSL: button(text: "Click") { toast("Hi") }
     *
     * @param viewRegistryId the registry ID of the View
     * @param callbackId the mruby callback ID
     */
    fun setViewClickListener(viewRegistryId: Int, callbackId: Int) {
        val view = mruby.lookupJavaObject<View>(viewRegistryId)
            ?: return
        view.setOnClickListener(MrbotoClickListener(this, callbackId))
    }

    /**
     * Set a text watcher on an EditText that dispatches to mruby.
     *
     * @param viewRegistryId the registry ID of the EditText
     * @param callbackId the mruby callback ID
     */
    fun setTextWatcher(viewRegistryId: Int, callbackId: Int) {
        val view = mruby.lookupJavaObject<android.widget.EditText>(viewRegistryId)
            ?: return
        view.addTextChangedListener(MrbotoTextWatcher(this, callbackId))
    }

    /**
     * Return the source content of a script from assets.
     * Called from Ruby via call_java_method("loadAssetScriptSource", path).
     */
    fun loadAssetScriptSource(path: CharSequence): String {
        return try {
            assets.open(path.toString()).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "loadAssetScriptSource('$path') failed: ${e.message}")
            "Error: ${e.message}"
        }
    }

    /**
     * Load and execute a Ruby script from assets.
     * Called from Ruby via call_java_method("loadAssetScript", path).
     * Uses CharSequence because C side maps Ruby strings to CharSequence
     * for reflection.
     */
    fun loadAssetScript(path: CharSequence): String {
        return try {
            val script = assets.open(path.toString()).bufferedReader().use { it.readText() }
            mruby.loadScript(script)
        } catch (e: Exception) {
            Log.w(TAG, "loadAssetScript('$path') failed: ${e.message}")
            "Error: ${e.message}"
        }
    }

    /**
     * Evaluate a raw Ruby string via mruby eval.
     * Called from Ruby via call_java_method("evalRuby", code).
     */
    fun evalRuby(code: CharSequence): String {
        return try {
            mruby.eval(code.toString())
        } catch (e: Exception) {
            Log.w(TAG, "evalRuby failed: ${e.message}")
            "Error: ${e.message}"
        }
    }
}
