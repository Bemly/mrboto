package com.mrboto

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import com.mrboto.MrbotoCheckChangeListener
import com.mrboto.MrbotoClickListener
import com.mrboto.MrbotoTextWatcher

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

        // Load the Ruby script
        try {
            val script = assets.open(getScriptPath()).bufferedReader().use { it.readText() }
            mruby.loadScript(script)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load script ${getScriptPath()}: ${e.message}")
        }

        // Set the current activity instance in mruby
        mruby.eval("Mrboto.current_activity_id = $activityRefId")
        mruby.eval("Mrboto.current_activity = Mrboto::JavaObject.from_registry($activityRefId)")

        // Dispatch on_create
        val bundleId = if (savedInstanceState != null) {
            mruby.registerJavaObject(savedInstanceState)
        } else {
            0
        }
        mruby.dispatchLifecycle(activityRefId, "on_create", bundleId)
        rubyInstanceId = activityRefId

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
}
