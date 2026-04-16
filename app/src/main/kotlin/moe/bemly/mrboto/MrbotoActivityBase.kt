package moe.bemly.mrboto

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AlertDialog
import android.widget.PopupMenu
import androidx.viewpager2.widget.ViewPager2
import moe.bemly.mrboto.MrbotoCheckChangeListener
import moe.bemly.mrboto.MrbotoClickListener
import moe.bemly.mrboto.MrbotoTextWatcher
import moe.bemly.mrboto.ViewPagerAdapter

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
                "The script did not register an activity class.\n\n" +
                "Add this line at the end of your script:\n" +
                "  Mrboto.register_activity_class(YourClassName)")
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

        // Set activity holder for native-to-Kotlin callbacks (e.g. TextWatcher)
        mruby.setActivityForTextWatcher(this)

        // Check for widget creation errors (after dispatch, since that's when widgets are created)
        val widgetErrors = mruby.eval(
            "begin\n" +
            "  \$mrboto_widget_errors.to_a.size > 0 ? \$mrboto_widget_errors.join('\\n') : \"\"\n" +
            "rescue\n" +
            "  \"\"\n" +
            "end"
        )
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
        mruby.setActivityForTextWatcher(null)
        mruby.dispatchLifecycle(rubyInstanceId, "on_destroy")
        super.onDestroy()
    }

    /**
     * Called from native C code. Posts a Ruby callback to the UI thread.
     * Used by run_on_ui_thread to defer execution until after on_create returns
     * and the window is attached (e.g. WebView rendering engine is ready).
     */
    fun runOnUiThreadFromNative(callbackId: Int) {
        runOnUiThread {
            mruby.eval("Mrboto.dispatch_callback($callbackId, 0)")
        }
    }

    /**
     * Load mrboto core scripts into a fresh MRuby instance.
     */
    private fun loadMrbotoCore(assets: android.content.res.AssetManager, mruby: MRuby) {
        val coreFiles = listOf(
            "mrboto/regexp/mrblib/node.rb",
            "mrboto/regexp/mrblib/parser.rb",
            "mrboto/regexp/mrblib/regexp.rb",
            "mrboto/regexp/mrblib/string.rb",
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
     * Called from JNI (nativeSetTextWatcher) after the callback ID
     * has been stored in the View's tag.
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
     * Set a check listener on a CompoundButton (CheckBox, Switch) that dispatches to mruby.
     */
    fun setCheckListener(viewRegistryId: Int, callbackId: Int) {
        val view = mruby.lookupJavaObject<android.widget.CompoundButton>(viewRegistryId)
            ?: return
        view.setOnCheckedChangeListener(MrbotoCheckChangeListener(this, callbackId))
    }

    /**
     * Set a WebViewClient on a WebView to handle page loading callbacks.
     * Called from Ruby via call_java_method("setWebViewClient", viewRegistryId).
     */
    fun setWebViewClient(viewRegistryId: Int) {
        val webView = mruby.lookupJavaObject<android.webkit.WebView>(viewRegistryId)
            ?: return
        webView.webViewClient = android.webkit.WebViewClient()
    }

    /**
     * Set the rendering layer type on a View (e.g., WebView).
     * Layer types: "none"=LAYER_TYPE_NONE, "software"=LAYER_TYPE_SOFTWARE, "hardware"=LAYER_TYPE_HARDWARE
     * Called from Ruby via call_java_method("setLayerType", viewRegistryId, layerTypeString).
     */
    fun setLayerType(viewRegistryId: Int, layerTypeString: String) {
        val view = mruby.lookupJavaObject<View>(viewRegistryId)
            ?: return
        val layerType = when (layerTypeString) {
            "none" -> View.LAYER_TYPE_NONE
            "software" -> View.LAYER_TYPE_SOFTWARE
            "hardware" -> View.LAYER_TYPE_HARDWARE
            else -> View.LAYER_TYPE_NONE
        }
        view.setLayerType(layerType, null)
    }

    /**
     * Set a ViewPagerAdapter on a ViewPager2. Called from Ruby via
     * call_java_method("setViewPager2Adapter", vpRegistryId, json_of_view_ids).
     * json_of_view_ids: JSON array of view registry IDs, e.g. "[10,11,12]".
     */
    fun setViewPager2Adapter(vpRegistryId: Int, viewIdsJson: CharSequence) {
        Log.i("setViewPager2Adapter", "vpRegistryId=$vpRegistryId json=$viewIdsJson")
        val viewPager = mruby.lookupJavaObject<ViewPager2>(vpRegistryId)
            ?: run { Log.e("setViewPager2Adapter", "ViewPager2 not found for id=$vpRegistryId"); return }
        val ids = try {
            val arr = org.json.JSONArray(viewIdsJson.toString())
            (0 until arr.length()).map { arr.getInt(it) }
        } catch (e: Exception) {
            Log.e("setViewPager2Adapter", "JSON parse error: ${e.message}"); return
        }
        Log.i("setViewPager2Adapter", "ids=$ids viewPager=${viewPager.javaClass.simpleName} " +
            "w=${viewPager.width} h=${viewPager.height} mw=${viewPager.measuredWidth} mh=${viewPager.measuredHeight}")
        val adapter = ViewPagerAdapter(this, ids)
        viewPager.adapter = adapter
        Log.i("setViewPager2Adapter", "adapter set, itemCount=${adapter.itemCount} " +
            "adapter=${viewPager.adapter?.javaClass?.simpleName}")
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
            val rubyActivityClass = Class.forName(componentName.className)
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
     * call_java_method("showPopupMenu", anchorRegistryId, items_json, callbackId).
     * items_json: JSON array of item labels.
     * callbackId: mruby callback ID to dispatch on item selection (0 for none).
     */
    fun showPopupMenu(anchorRegistryId: Int, itemsJson: CharSequence, callbackId: Int) {
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
        if (callbackId > 0) {
            popup.setOnMenuItemClickListener { item ->
                val safeTitle = item.title.toString().replace("'", "\\\\'")
                mruby.eval("Mrboto.dispatch_callback($callbackId, ${item.itemId}, '$safeTitle')")
                true
            }
        }
        popup.show()
    }

}
