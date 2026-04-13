package moe.bemly.mrboto

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.animation.Animation
import android.view.animation.AlphaAnimation
import android.view.animation.TranslateAnimation
import android.view.animation.ScaleAnimation
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AlertDialog
import android.widget.PopupMenu
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
        const val EXTRA_SCRIPT_PATH = "mrboto_script_path"
        const val META_DATA_SCRIPT = "mrboto_script"
    }

    internal lateinit var mruby: MRuby
    protected var rubyInstanceId: Int = 0

    /** Expose mruby to subclasses in other modules */
    protected fun getMRuby(): MRuby = mruby

    /** Override to return the Ruby script asset path */
    protected open fun getScriptPath(): String? = null

    /** Dynamic script path from Intent extra (if set) */
    private var _dynamicScriptPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Read dynamic script path from Intent extra
        _dynamicScriptPath = intent.getStringExtra(EXTRA_SCRIPT_PATH)

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

        // Determine script path: Intent extra > subclass override > manifest meta-data
        var scriptPath = _dynamicScriptPath ?: getScriptPath()
        if (scriptPath == null) {
            val info = packageManager.getActivityInfo(componentName, android.content.pm.PackageManager.GET_META_DATA)
            scriptPath = info?.metaData?.getString(META_DATA_SCRIPT)
        }
        if (scriptPath == null) {
            showErrorPage("No Script Path", "No script path was provided.\nOverride getScriptPath() or pass mrboto_script_path via Intent extra.")
            return
        }

        // Wrap 'this' Activity as a JavaObject in mruby
        val activityRefId = mruby.registerJavaObject(this)

        // Set the Java activity reference BEFORE loading the script
        mruby.eval("Mrboto.current_activity_id = $activityRefId")

        // Load the Ruby script (should define a class inheriting Mrboto::Activity)
        // nativeLoadScript returns the last expression value on success,
        // or "ClassName: message" on error. Only treat it as failure if it
        // looks like an exception string.
        val script = try {
            assets.open(scriptPath).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            showErrorPage("Script Not Found", "Cannot open asset: $scriptPath\n\n${e.message}")
            return
        }

        val loadResult = mruby.loadScript(script)
        if (loadResult.startsWith("SyntaxError:") ||
            loadResult.startsWith("ScriptError:") ||
            loadResult.startsWith("RuntimeError:")) {
            showErrorPage("Ruby Script Error", "Failed to load $scriptPath\n\n$loadResult")
            return
        }

        // Debug: check what the script defined
        val checkResult = mruby.eval("Mrboto.class.to_s rescue 'error:' + \$!.message")
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
        if (instantiateResult != "instantiated") {
            showErrorPage("No Activity Class",
                "The script did not define Mrboto._ruby_activity_class.\n\n" +
                "Add this line at the end of your script:\n" +
                "  Mrboto._ruby_activity_class = YourClassName")
            return
        }

        // Dispatch on_create (bundle will be passed as argument to the Ruby method)
        val bundleId = if (savedInstanceState != null) {
            mruby.registerJavaObject(savedInstanceState)
        } else {
            0
        }
        val dispatchResult = mruby.dispatchLifecycle(activityRefId, "on_create", bundleId)
        if (dispatchResult != "ok") {
            showErrorPage("Lifecycle Error", "on_create dispatch failed:\n\n$dispatchResult")
            return
        }
        rubyInstanceId = activityRefId

        // Check for widget creation errors (after dispatch, since that's when widgets are created)
        val widgetErrors = mruby.eval("defined?(\$mrboto_widget_errors) && \$mrboto_widget_errors.to_a.size > 0 ? \$mrboto_widget_errors.join('\\n') : ''")
        if (widgetErrors.isNotEmpty()) {
            Log.e(TAG, "Widget creation errors:\n$widgetErrors")
        }

        Log.i(TAG, "Activity created, script: $scriptPath")
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

    /**
     * Show a full-screen error page for script loading failures.
     * Red background, white selectable text.
     */
    private fun showErrorPage(title: String, message: String) {
        Log.e(TAG, "⚠ $title\n$message")
        val scroll = ScrollView(this)
        val textView = TextView(this).apply {
            text = "⚠ $title\n\n$message"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC0000"))
            setPadding(40, 60, 40, 40)
            setTextIsSelectable(true)
            gravity = Gravity.TOP
        }
        scroll.addView(textView)
        setContentView(scroll)
    }

    /**
     * Start the generic Ruby Activity with a script path.
     * Called from Ruby via _call_java_method.
     * On failure, shows a Toast to give visible feedback.
     */
    fun startRubyActivity(scriptPath: CharSequence) {
        try {
            val rubyActivityClass = Class.forName("moe.bemly.mrboto.RubyActivity")
            val intent = android.content.Intent(this, rubyActivityClass)
            intent.putExtra(EXTRA_SCRIPT_PATH, scriptPath.toString())
            startActivity(intent)
        } catch (e: Exception) {
            val msg = "Cannot open $scriptPath: ${e.message}"
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
            Log.e(TAG, "startRubyActivity failed: $msg")
        }
    }

    /**
     * Show an AlertDialog. Called from Ruby via
     * call_java_method("showDialog", title, message, buttons_json).
     * buttons_json: JSON array of button labels, or null for single OK.
     */
    fun showDialog(title: CharSequence, message: CharSequence, buttonsJson: CharSequence?) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)

        if (buttonsJson.isNullOrEmpty()) {
            builder.setPositiveButton("OK") { d, _ -> d.dismiss() }
        } else {
            try {
                val labels = org.json.JSONArray(buttonsJson.toString())
                if (labels.length() >= 2) {
                    builder.setPositiveButton(labels.getString(0)) { d, _ -> d.dismiss() }
                    builder.setNegativeButton(labels.getString(1)) { d, _ -> d.dismiss() }
                    if (labels.length() >= 3) {
                        builder.setNeutralButton(labels.getString(2)) { d, _ -> d.dismiss() }
                    }
                } else {
                    builder.setPositiveButton(labels.getString(0)) { d, _ -> d.dismiss() }
                }
            } catch (e: Exception) {
                builder.setPositiveButton("OK") { d, _ -> d.dismiss() }
            }
        }

        builder.setCancelable(true)
        builder.show()
    }

    /**
     * Show a Snackbar. Called from Ruby via
     * call_java_method("showSnackbar", viewRegistryId, message, duration).
     */
    fun showSnackbar(viewRegistryId: Int, message: CharSequence, duration: Int) {
        val view = mruby.lookupJavaObject<View>(viewRegistryId)
            ?: return
        val dur = if (duration == 1) Snackbar.LENGTH_LONG else Snackbar.LENGTH_SHORT
        Snackbar.make(view, message, dur).show()
    }

    /**
     * Show a PopupMenu. Called from Ruby via
     * call_java_method("showPopupMenu", anchorRegistryId, items_json).
     * items_json: JSON array of item labels.
     * Returns item index (0-based) or -1 on dismiss/error.
     */
    fun showPopupMenu(anchorRegistryId: Int, itemsJson: CharSequence) {
        val anchor = mruby.lookupJavaObject<View>(anchorRegistryId)
            ?: return
        val popup = PopupMenu(this, anchor)
        try {
            val items = org.json.JSONArray(itemsJson.toString())
            for (i in 0 until items.length()) {
                popup.menu.add(0, i, 0, items.getString(i))
            }
        } catch (e: Exception) {
            return
        }
        popup.setOnMenuItemClickListener { item ->
            val callbackId = popup.javaClass.getDeclaredMethod("hashCode").let {
                item.itemId
            }
            mruby.eval("Mrboto.dispatch_popup_select($callbackId, '${item.title}')")
            true
        }
        popup.show()
    }

    // ── Animation helpers ──────────────────────────────────────────

    /**
     * Run a fade animation on a view.
     * Called from Ruby: call_java_method("animateFade", viewRegistryId, fromAlpha, toAlpha, durationMs)
     */
    fun animateFade(viewRegistryId: Int, fromAlpha: Double, toAlpha: Double, durationMs: Long) {
        val view = mruby.lookupJavaObject<View>(viewRegistryId)
            ?: return
        val anim = AlphaAnimation(fromAlpha.toFloat(), toAlpha.toFloat())
        anim.duration = durationMs
        anim.fillAfter = true
        view.startAnimation(anim)
    }

    /**
     * Run a translate animation on a view.
     * Called from Ruby: call_java_method("animateTranslate", viewRegistryId, fromX, fromY, toX, toY, durationMs)
     */
    fun animateTranslate(viewRegistryId: Int, fromX: Double, fromY: Double, toX: Double, toY: Double, durationMs: Long) {
        val view = mruby.lookupJavaObject<View>(viewRegistryId)
            ?: return
        val anim = TranslateAnimation(
            fromX.toFloat(), toX.toFloat(),
            fromY.toFloat(), toY.toFloat()
        )
        anim.duration = durationMs
        anim.fillAfter = true
        view.startAnimation(anim)
    }

    /**
     * Run a scale animation on a view.
     * Called from Ruby: call_java_method("animateScale", viewRegistryId, fromX, fromY, toX, toY, durationMs)
     */
    fun animateScale(viewRegistryId: Int, fromX: Double, fromY: Double, toX: Double, toY: Double, durationMs: Long) {
        val view = mruby.lookupJavaObject<View>(viewRegistryId)
            ?: return
        val anim = ScaleAnimation(
            fromX.toFloat(), toX.toFloat(),
            fromY.toFloat(), toY.toFloat(),
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        anim.duration = durationMs
        anim.fillAfter = true
        view.startAnimation(anim)
    }
}
