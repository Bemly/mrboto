package moe.bemly.mrboto

import android.accessibilityservice.GestureDescription
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Path
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioManager
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.snackbar.Snackbar
import moe.bemly.mrboto.MrbotoCheckChangeListener
import moe.bemly.mrboto.MrbotoClickListener
import moe.bemly.mrboto.MrbotoTextWatcher
import moe.bemly.mrboto.ViewPagerAdapter
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

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

    // ── SQLite ─────────────────────────────────────────────────────────

    private val sqliteDbs = mutableMapOf<Int, android.database.sqlite.SQLiteDatabase>()
    private var nextSqliteId = 1

    fun sqliteOpen(name: CharSequence): Int {
        val db = openOrCreateDatabase(name.toString(), MODE_PRIVATE, null)
        val id = nextSqliteId++
        sqliteDbs[id] = db
        return id
    }

    fun sqliteExecute(dbId: Int, sql: CharSequence): Boolean {
        val db = sqliteDbs[dbId] ?: return false
        return try {
            db.execSQL(sql.toString())
            true
        } catch (e: Exception) {
            Log.w(TAG, "sqliteExecute failed: ${e.message}")
            false
        }
    }

    fun sqliteInsert(dbId: Int, table: CharSequence, valuesJson: CharSequence): Int {
        val db = sqliteDbs[dbId] ?: return -1
        return try {
            val json = org.json.JSONObject(valuesJson.toString())
            val cv = android.content.ContentValues()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.get(key)
                when (value) {
                    is Int -> cv.put(key, value)
                    is Long -> cv.put(key, value)
                    is Double -> cv.put(key, value)
                    is Boolean -> cv.put(key, value)
                    is String -> cv.put(key, value)
                    org.json.JSONObject.NULL -> cv.putNull(key)
                    else -> cv.put(key, value.toString())
                }
            }
            db.insert(table.toString(), null, cv).toInt()
        } catch (e: Exception) {
            Log.w(TAG, "sqliteInsert failed: ${e.message}")
            -1
        }
    }

    fun sqliteQuery(dbId: Int, sql: CharSequence): String {
        val db = sqliteDbs[dbId] ?: return "[]"
        return try {
            val cursor = db.rawQuery(sql.toString(), null)
            val jsonArray = org.json.JSONArray()
            while (cursor.moveToNext()) {
                val obj = org.json.JSONObject()
                for (i in 0 until cursor.columnCount) {
                    val colName = cursor.getColumnName(i)
                    when (cursor.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_INTEGER -> obj.put(colName, cursor.getLong(i))
                        android.database.Cursor.FIELD_TYPE_FLOAT -> obj.put(colName, cursor.getDouble(i))
                        android.database.Cursor.FIELD_TYPE_STRING -> obj.put(colName, cursor.getString(i))
                        android.database.Cursor.FIELD_TYPE_NULL -> obj.put(colName, org.json.JSONObject.NULL)
                        else -> obj.put(colName, cursor.getString(i))
                    }
                }
                jsonArray.put(obj)
            }
            cursor.close()
            jsonArray.toString()
        } catch (e: Exception) {
            Log.w(TAG, "sqliteQuery failed: ${e.message}")
            "[]"
        }
    }

    fun sqliteUpdate(dbId: Int, table: CharSequence, valuesJson: CharSequence, whereClause: CharSequence): Int {
        val db = sqliteDbs[dbId] ?: return -1
        return try {
            val json = org.json.JSONObject(valuesJson.toString())
            val cv = android.content.ContentValues()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.get(key)
                when (value) {
                    is Int -> cv.put(key, value)
                    is Long -> cv.put(key, value)
                    is Double -> cv.put(key, value)
                    is Boolean -> cv.put(key, value)
                    is String -> cv.put(key, value)
                    org.json.JSONObject.NULL -> cv.putNull(key)
                    else -> cv.put(key, value.toString())
                }
            }
            val where = if (whereClause.isEmpty()) null else whereClause.toString()
            db.update(table.toString(), cv, where, null)
        } catch (e: Exception) {
            Log.w(TAG, "sqliteUpdate failed: ${e.message}")
            -1
        }
    }

    fun sqliteDelete(dbId: Int, table: CharSequence, whereClause: CharSequence): Int {
        val db = sqliteDbs[dbId] ?: return -1
        return try {
            val where = if (whereClause.isEmpty()) null else whereClause.toString()
            db.delete(table.toString(), where, null)
        } catch (e: Exception) {
            Log.w(TAG, "sqliteDelete failed: ${e.message}")
            -1
        }
    }

    fun sqliteClose(dbId: Int) {
        sqliteDbs.remove(dbId)?.close()
    }

    // ── Permission ──────────────────────────────────────────────────────
    fun checkPermissionGranted(permission: CharSequence): Boolean {
        return try {
            packageManager.checkPermission(
                permission.toString(), packageName
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.w(TAG, "checkPermissionGranted failed: ${e.message}")
            false
        }
    }

    // ── Notification ─────────────────────────────────────────────────────
    private fun ensureNotificationChannel(channelId: String, channelName: String = channelId) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val mgr = getSystemService(android.app.NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(channelId) == null) {
                val channel = android.app.NotificationChannel(
                    channelId, channelName, android.app.NotificationManager.IMPORTANCE_DEFAULT)
                mgr.createNotificationChannel(channel)
            }
        }
    }

    fun notifyShow(id: Int, title: CharSequence, message: CharSequence, channel: CharSequence) {
        try {
            val channelId = channel.toString()
            ensureNotificationChannel(channelId)
            val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title.toString())
                .setContentText(message.toString())
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            val mgr = getSystemService(android.app.NotificationManager::class.java) ?: return
            mgr.notify(id, builder.build())
        } catch (e: Exception) {
            Log.w(TAG, "notifyShow failed: ${e.message}")
        }
    }

    fun notifyCancel(id: Int) {
        try {
            val mgr = getSystemService(android.app.NotificationManager::class.java) ?: return
            mgr.cancel(id)
        } catch (e: Exception) {
            Log.w(TAG, "notifyCancel failed: ${e.message}")
        }
    }

    fun notifyBig(id: Int, title: CharSequence, bigText: CharSequence, channel: CharSequence) {
        try {
            val channelId = channel.toString()
            ensureNotificationChannel(channelId)
            val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title.toString())
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(bigText.toString()))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            val mgr = getSystemService(android.app.NotificationManager::class.java) ?: return
            mgr.notify(id, builder.build())
        } catch (e: Exception) {
            Log.w(TAG, "notifyBig failed: ${e.message}")
        }
    }

    fun notifyProgress(id: Int, title: CharSequence, message: CharSequence,
                       progress: Int, max: Int, channel: CharSequence) {
        try {
            val channelId = channel.toString()
            ensureNotificationChannel(channelId)
            val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title.toString())
                .setContentText(message.toString())
                .setProgress(max, progress, false)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            val mgr = getSystemService(android.app.NotificationManager::class.java) ?: return
            mgr.notify(id, builder.build())
        } catch (e: Exception) {
            Log.w(TAG, "notifyProgress failed: ${e.message}")
        }
    }

    // ── Network ──────────────────────────────────────────────────────────
    fun httpGet(url: CharSequence, headersJson: CharSequence?): String {
        return try {
            val conn = java.net.URL(url.toString()).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (headersJson != null && headersJson.isNotEmpty() && headersJson != "null") {
                val hdr = org.json.JSONObject(headersJson.toString())
                val keys = hdr.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    conn.setRequestProperty(key, hdr.getString(key))
                }
            }
            val status = conn.responseCode
            val body = try {
                conn.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }
            conn.disconnect()
            val result = org.json.JSONObject()
            result.put("status", status)
            result.put("body", body)
            result.put("headers", org.json.JSONObject())
            result.toString()
        } catch (e: Exception) {
            Log.w(TAG, "httpGet failed: ${e.message}")
            "{\"status\":0,\"body\":\"\",\"headers\":{}}"
        }
    }

    fun httpPost(url: CharSequence, body: CharSequence, headersJson: CharSequence?): String {
        return try {
            val conn = java.net.URL(url.toString()).openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true
            if (headersJson != null && headersJson.isNotEmpty() && headersJson != "null") {
                val hdr = org.json.JSONObject(headersJson.toString())
                val keys = hdr.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    conn.setRequestProperty(key, hdr.getString(key))
                }
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val status = conn.responseCode
            val respBody = try {
                conn.inputStream.bufferedReader().readText()
            } catch (e: Exception) {
                conn.errorStream?.bufferedReader()?.readText() ?: ""
            }
            conn.disconnect()
            val result = org.json.JSONObject()
            result.put("status", status)
            result.put("body", respBody)
            result.put("headers", org.json.JSONObject())
            result.toString()
        } catch (e: Exception) {
            Log.w(TAG, "httpPost failed: ${e.message}")
            "{\"status\":0,\"body\":\"\",\"headers\":{}}"
        }
    }

    fun httpDownload(url: CharSequence, filepath: CharSequence): Boolean {
        return try {
            val conn = java.net.URL(url.toString()).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 30000
            conn.connect()
            if (conn.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return false
            }
            val file = java.io.File(filesDir, filepath.toString())
            file.parentFile?.mkdirs()
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            conn.disconnect()
            true
        } catch (e: Exception) {
            Log.w(TAG, "httpDownload failed: ${e.message}")
            false
        }
    }

    // ── File Operations ──────────────────────────────────────────────────
    fun fileWrite(name: CharSequence, content: CharSequence): Boolean {
        return try {
            openFileOutput(name.toString(), android.content.Context.MODE_PRIVATE).use {
                it.write(content.toString().toByteArray())
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "fileWrite failed: ${e.message}")
            false
        }
    }

    fun fileRead(name: CharSequence): String {
        return try {
            openFileInput(name.toString()).bufferedReader().readText()
        } catch (e: Exception) {
            Log.w(TAG, "fileRead failed: ${e.message}")
            ""
        }
    }

    fun fileExists(name: CharSequence): Boolean {
        return getFileStreamPath(name.toString()).exists()
    }

    fun fileDelete(name: CharSequence): Boolean {
        return try {
            deleteFile(name.toString())
        } catch (e: Exception) {
            Log.w(TAG, "fileDelete failed: ${e.message}")
            false
        }
    }

    fun fileList(dir: CharSequence): String {
        return try {
            val files = if (dir.isEmpty()) {
                fileList() ?: emptyArray()
            } else {
                getDir(dir.toString(), android.content.Context.MODE_PRIVATE).list() ?: emptyArray()
            }
            val arr = org.json.JSONArray()
            for (f in files) arr.put(f)
            arr.toString()
        } catch (e: Exception) {
            Log.w(TAG, "fileList failed: ${e.message}")
            "[]"
        }
    }

    fun fileSize(name: CharSequence): Int {
        return try {
            getFileStreamPath(name.toString()).length().toInt()
        } catch (e: Exception) {
            -1
        }
    }

    fun externalFileWrite(name: CharSequence, content: CharSequence): Boolean {
        return try {
            val file = java.io.File(getExternalFilesDir(null), name.toString())
            file.parentFile?.mkdirs()
            file.writeText(content.toString())
            true
        } catch (e: Exception) {
            Log.w(TAG, "externalFileWrite failed: ${e.message}")
            false
        }
    }

    fun externalFileRead(name: CharSequence): String {
        return try {
            java.io.File(getExternalFilesDir(null), name.toString()).readText()
        } catch (e: Exception) {
            Log.w(TAG, "externalFileRead failed: ${e.message}")
            ""
        }
    }

    fun cacheWrite(name: CharSequence, content: CharSequence): Boolean {
        return try {
            val file = java.io.File(cacheDir, name.toString())
            file.parentFile?.mkdirs()
            file.writeText(content.toString())
            true
        } catch (e: Exception) {
            Log.w(TAG, "cacheWrite failed: ${e.message}")
            false
        }
    }

    fun cacheRead(name: CharSequence): String {
        return try {
            java.io.File(cacheDir, name.toString()).readText()
        } catch (e: Exception) {
            Log.w(TAG, "cacheRead failed: ${e.message}")
            ""
        }
    }

    // ── Intent Extras ──────────────────────────────────────────────────────
    fun getExtraInt(key: CharSequence): Int {
        return try {
            intent?.extras?.getInt(key.toString(), 0) ?: 0
        } catch (_: Exception) { 0 }
    }

    fun getExtraBool(key: CharSequence): Boolean {
        return try {
            intent?.extras?.getBoolean(key.toString(), false) ?: false
        } catch (_: Exception) { false }
    }

    fun getExtraFloat(key: CharSequence): Float {
        return try {
            intent?.extras?.getFloat(key.toString(), 0f) ?: 0f
        } catch (_: Exception) { 0f }
    }

    fun getAllExtras(): String {
        return try {
            val extras = intent?.extras ?: return "{}"
            val map = mutableMapOf<String, Any?>()
            for (key in extras.keySet()) {
                @Suppress("DEPRECATION")
                map[key] = extras.get(key)
            }
            org.json.JSONObject(map).toString()
        } catch (_: Exception) { "{}" }
    }

    // ── Permissions ───────────────────────────────────────────────────────
    fun requestPermissionSync(permission: CharSequence): Boolean {
        return try {
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
                packageManager.checkPermission(permission.toString(), packageName)
        } catch (_: Exception) { false }
    }

    fun requestPermissionsSync(permissionsJson: CharSequence): String {
        return try {
            val arr = org.json.JSONArray(permissionsJson.toString())
            val result = org.json.JSONObject()
            for (i in 0 until arr.length()) {
                val perm = arr.getString(i)
                val granted = android.content.pm.PackageManager.PERMISSION_GRANTED ==
                    packageManager.checkPermission(perm, packageName)
                result.put(perm, granted)
            }
            result.toString()
        } catch (_: Exception) { "{}" }
    }

    // ── Start Activity ────────────────────────────────────────────────────
    fun startActivityWithExtras(className: CharSequence, extrasJson: CharSequence) {
        try {
            val intent = android.content.Intent(this, Class.forName(className.toString()))
            val json = org.json.JSONObject(extrasJson.toString())
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.get(key)
                when (value) {
                    is String -> intent.putExtra(key, value)
                    is Int -> intent.putExtra(key, value)
                    is Long -> intent.putExtra(key, value)
                    is Double -> intent.putExtra(key, value)
                    is Boolean -> intent.putExtra(key, value)
                    is Float -> intent.putExtra(key, value)
                    else -> intent.putExtra(key, value.toString())
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "startActivityWithExtras failed: ${e.message}")
        }
    }

    // ── Clipboard (in-memory) ────────────────────────────────────────────
    private var _clipboardText: String = ""

    fun clipboardCopy(text: CharSequence): Boolean {
        _clipboardText = text.toString()
        return true
    }

    fun clipboardPaste(): String = _clipboardText

    fun clipboardHasText(): Boolean = _clipboardText.isNotEmpty()

    // ── Clipboard (system) ──────────────────────────────────────────────
    fun clipboardSystemCopy(text: CharSequence): Boolean {
        return try {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("text", text))
            true
        } catch (_: Exception) { false }
    }

    fun clipboardSystemPaste(): String {
        return try {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0)
                clip.getItemAt(0).coerceToText(this)?.toString() ?: ""
            else ""
        } catch (_: Exception) { "" }
    }

    fun clipboardSystemHasText(): Boolean {
        return try {
            val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.hasPrimaryClip() && cm.primaryClip!!.itemCount > 0
        } catch (_: Exception) { false }
    }

    // ── OCR (PaddleOCR v5 NCNN) ──────────────────────────────────────
    private var ocrInstance: com.equationl.ncnnandroidppocr.OCR? = null

    fun ocrInit(): Boolean {
        if (ocrInstance != null) return true
        return try {
            val ocr = com.equationl.ncnnandroidppocr.OCR()
            val ok = ocr.initModelFromAssert(
                assets,
                com.equationl.ncnnandroidppocr.bean.ModelType.Mobile,
                com.equationl.ncnnandroidppocr.bean.ImageSize.Size720,
                com.equationl.ncnnandroidppocr.bean.Device.CPU
            )
            if (ok) ocrInstance = ocr
            ok
        } catch (e: Exception) {
            Log.w("Mrboto", "ocrInit failed: ${e.message}")
            false
        }
    }

    fun ocrRecognize(imagePath: CharSequence): String {
        val ocr = ocrInstance ?: return ""
        return try {
            val result = ocr.detectImagePath(imagePath.toString(), com.equationl.ncnnandroidppocr.bean.DrawModel.None)
            result?.text ?: ""
        } catch (e: Exception) {
            Log.w("Mrboto", "ocrRecognize failed: ${e.message}")
            ""
        }
    }

    fun ocrRecognizeFromPath(imagePath: CharSequence): String {
        return ocrRecognize(imagePath)
    }

    fun ocrDetect(imagePath: CharSequence): String {
        return ocrRecognize(imagePath)
    }

    fun ocrDetectBitmap(bitmap: android.graphics.Bitmap): String {
        val ocr = ocrInstance ?: return ""
        return try {
            val result = ocr.detectBitmap(bitmap, com.equationl.ncnnandroidppocr.bean.DrawModel.None)
            result?.text ?: ""
        } catch (e: Exception) {
            Log.w("Mrboto", "ocrDetectBitmap failed: ${e.message}")
            ""
        }
    }

    fun ocrRelease(): Boolean {
        return try {
            ocrInstance?.release()
            ocrInstance = null
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "ocrRelease failed: ${e.message}")
            false
        }
    }

    // ── Accessibility (from AccessibilityExtensions) ────────────────────
    fun accessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.isEnabled
    }

    fun accessibilityTouchExploration(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.isTouchExplorationEnabled
    }

    // ── Camera (from CameraExtensions) ──────────────────────────────────
    private var photoUri: Uri? = null
    private var photoCallbackId: Int = -1
    private var videoCallbackId: Int = -1

    fun cameraAvailable(): Boolean {
        return try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.cameraIdList.isNotEmpty()
        } catch (_: Exception) { false }
    }

    fun cameraInfo(): String {
        return try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val arr = org.json.JSONArray()
            for (id in cm.cameraIdList) {
                val chars = cm.getCameraCharacteristics(id)
                val obj = org.json.JSONObject()
                obj.put("id", id)
                obj.put("facing", chars.get(CameraCharacteristics.LENS_FACING) ?: -1)
                obj.put("hasFlash", chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false)
                arr.put(obj)
            }
            arr.toString()
        } catch (_: Exception) { "[]" }
    }

    fun cameraTakePhoto(callbackId: Int): Boolean {
        return try {
            val file = File(cacheDir, "mrboto_photo_${System.currentTimeMillis()}.jpg")
            photoUri = FileProvider.getUriForFile(
                this, "${packageName}.mrboto.fileprovider", file
            )
            photoCallbackId = callbackId
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            }
            startActivityForResult(intent, 9001)
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "cameraTakePhoto failed: ${e.message}")
            false
        }
    }

    fun cameraRecordVideo(callbackId: Int): Boolean {
        return try {
            videoCallbackId = callbackId
            val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            startActivityForResult(intent, 9002)
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "cameraRecordVideo failed: ${e.message}")
            false
        }
    }

    fun cameraOpenPreview(surfaceViewId: Int): Boolean {
        // Camera2 preview requires SurfaceView/TextureView — placeholder
        return false
    }

    fun cameraClosePreview() {
        // placeholder
    }

    // ── Color Find (from ColorFindExtensions) ───────────────────────────
    fun getColorAt(bitmapPath: CharSequence, x: Int, y: Int): String {
        return try {
            val bmp = BitmapFactory.decodeFile(bitmapPath.toString())
                ?: return ""
            val pixel = bmp.getPixel(x, y)
            bmp.recycle()
            String.format("#%06X", 0xFFFFFF and pixel)
        } catch (_: Exception) { "" }
    }

    fun findColor(bitmapPath: CharSequence, colorHex: CharSequence, region: CharSequence = ""): String {
        return try {
            val bmp = BitmapFactory.decodeFile(bitmapPath.toString()) ?: return "[]"
            val targetColor = Color.parseColor(colorHex.toString())
            val r = parseRegion(region.toString(), bmp.width, bmp.height)
            val results = org.json.JSONArray()
            for (y in r[1] until r[3]) {
                for (x in r[0] until r[2]) {
                    if (bmp.getPixel(x, y) == targetColor) {
                        results.put(org.json.JSONObject().put("x", x).put("y", y))
                    }
                }
            }
            bmp.recycle()
            results.toString()
        } catch (_: Exception) { "[]" }
    }

    fun findColorFuzzy(bitmapPath: CharSequence, colorHex: CharSequence, threshold: Int = 32, region: CharSequence = ""): String {
        return try {
            val bmp = BitmapFactory.decodeFile(bitmapPath.toString()) ?: return "[]"
            val targetColor = Color.parseColor(colorHex.toString())
            val tr = Color.red(targetColor)
            val tg = Color.green(targetColor)
            val tb = Color.blue(targetColor)
            val r = parseRegion(region.toString(), bmp.width, bmp.height)
            val results = org.json.JSONArray()
            for (y in r[1] until r[3]) {
                for (x in r[0] until r[2]) {
                    val pixel = bmp.getPixel(x, y)
                    if (kotlin.math.abs(Color.red(pixel) - tr) <= threshold &&
                        kotlin.math.abs(Color.green(pixel) - tg) <= threshold &&
                        kotlin.math.abs(Color.blue(pixel) - tb) <= threshold) {
                        results.put(org.json.JSONObject().put("x", x).put("y", y))
                    }
                }
            }
            bmp.recycle()
            results.toString()
        } catch (_: Exception) { "[]" }
    }

    private fun parseRegion(region: String, w: Int, h: Int): IntArray {
        if (region.isEmpty()) return intArrayOf(0, 0, w, h)
        return try {
            val arr = org.json.JSONArray(region)
            intArrayOf(arr.getInt(0), arr.getInt(1), arr.getInt(2), arr.getInt(3))
        } catch (_: Exception) { intArrayOf(0, 0, w, h) }
    }

    // ── Coroutine (from CoroutineExtensions) ────────────────────────────
    private val bgExecutor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeTimers = ConcurrentHashMap<Int, Timer>()
    private val timerIdGen = AtomicInteger(1)

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

    // ── Device Control (from DeviceControlExtensions) ────────────────────
    fun setVolume(streamType: Int, level: Int, showUi: Boolean = false): Boolean {
        return try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val flags = if (showUi) AudioManager.FLAG_SHOW_UI else 0
            am.setStreamVolume(streamType, level, flags)
            true
        } catch (_: Exception) { false }
    }

    fun getVolume(streamType: Int): Int {
        return try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.getStreamVolume(streamType)
        } catch (_: Exception) { -1 }
    }

    fun setBrightness(level: Int): Boolean {
        return try {
            val lp = window.attributes
            lp.screenBrightness = level / 255f
            window.attributes = lp
            true
        } catch (_: Exception) { false }
    }

    fun getBrightness(): Int {
        return try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Exception) { -1 }
    }

    fun vibrate(durationMs: Int = 200): Boolean {
        return try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "vibrate failed: ${e.message}")
            false
        }
    }

    fun vibratePattern(patternJson: CharSequence, repeat: Int = -1): Boolean {
        return try {
            val arr = org.json.JSONArray(patternJson.toString())
            val pattern = LongArray(arr.length()) { arr.getLong(it) }
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, repeat))
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "vibratePattern failed: ${e.message}")
            false
        }
    }

    // ── Event Listener (from EventListenerExtensions) ────────────────────
    private var batteryReceiver: BroadcastReceiver? = null
    private var batteryCallbackId: Int = -1
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun observeBattery(callbackId: Int): Boolean {
        return try {
            batteryCallbackId = callbackId
            batteryReceiver?.let { unregisterReceiver(it) }
            val mrubyRef = mruby
            batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val pct = if (scale > 0) (level * 100 / scale) else -1
                    mrubyRef.eval("Mrboto.dispatch_callback($batteryCallbackId, $pct)")
                }
            }
            registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "observeBattery failed: ${e.message}")
            false
        }
    }

    fun observeNetwork(callbackId: Int): Boolean {
        return try {
            val mrubyRef = mruby
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let { cm.unregisterNetworkCallback(it) }
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    mrubyRef.eval("Mrboto.dispatch_callback($callbackId, true)")
                }
                override fun onLost(network: Network) {
                    mrubyRef.eval("Mrboto.dispatch_callback($callbackId, false)")
                }
            }
            networkCallback = cb
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, cb)
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "observeNetwork failed: ${e.message}")
            false
        }
    }

    fun isNetworkConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ── File Encoding (from FileEncodingExtensions) ──────────────────────
    fun fileReadEncoding(name: CharSequence, encoding: CharSequence): String {
        return try {
            val charset = Charset.forName(encoding.toString())
            openFileInput(name.toString()).readBytes().toString(charset)
        } catch (e: Exception) {
            Log.w("Mrboto", "fileReadEncoding failed: ${e.message}")
            ""
        }
    }

    fun fileWriteEncoding(name: CharSequence, content: CharSequence, encoding: CharSequence): Boolean {
        return try {
            val charset = Charset.forName(encoding.toString())
            openFileOutput(name.toString(), Context.MODE_PRIVATE).use {
                it.write(content.toString().toByteArray(charset))
            }
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "fileWriteEncoding failed: ${e.message}")
            false
        }
    }

    fun fileListDir(path: CharSequence): String {
        return try {
            val dir = if (path.isEmpty()) filesDir else File(path.toString())
            val arr = org.json.JSONArray()
            (dir.listFiles() ?: emptyArray()).forEach { arr.put(it.name) }
            arr.toString()
        } catch (e: Exception) {
            "[]"
        }
    }

    fun fileMkdir(path: CharSequence): Boolean {
        return try {
            File(path.toString()).mkdirs()
        } catch (_: Exception) { false }
    }

    fun fileDeleteDir(path: CharSequence): Boolean {
        return try {
            val dir = File(path.toString())
            dir.deleteRecursively()
        } catch (_: Exception) { false }
    }

    fun fileExistsPath(path: CharSequence): Boolean {
        return File(path.toString()).exists()
    }

    fun fileIsDir(path: CharSequence): Boolean {
        return File(path.toString()).isDirectory
    }

    // ── Gesture (from GestureExtensions) ─────────────────────────────────
    fun gestureClick(x: Int, y: Int): Boolean {
        return dispatchGesture {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            GestureDescription.Builder().addStroke(
                GestureDescription.StrokeDescription(path, 0, 100)
            ).build()
        }
    }

    fun gestureLongClick(x: Int, y: Int): Boolean {
        return dispatchGesture {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            GestureDescription.Builder().addStroke(
                GestureDescription.StrokeDescription(path, 0, ViewConfiguration.getLongPressTimeout().toLong())
            ).build()
        }
    }

    fun gestureSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300): Boolean {
        return dispatchGesture {
            val path = Path().apply {
                moveTo(x1.toFloat(), y1.toFloat())
                lineTo(x2.toFloat(), y2.toFloat())
            }
            GestureDescription.Builder().addStroke(
                GestureDescription.StrokeDescription(path, 0, durationMs.toLong())
            ).build()
        }
    }

    fun gestureScroll(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Int = 300): Boolean {
        return gestureSwipe(x1, y1, x2, y2, durationMs)
    }

    private fun dispatchGesture(builder: () -> GestureDescription): Boolean {
        return try {
            // Requires AccessibilityService — placeholder for now
            Log.d("Mrboto", "gesture dispatch requires AccessibilityService")
            false
        } catch (_: Exception) { false }
    }

    // ── Image (from ImageExtensions) ─────────────────────────────────────
    fun imageCrop(path: CharSequence, x: Int, y: Int, w: Int, h: Int, outPath: CharSequence): Boolean {
        return try {
            val bmp = BitmapFactory.decodeFile(path.toString()) ?: return false
            val cropped = Bitmap.createBitmap(bmp, x, y, w, h)
            bmp.recycle()
            File(outPath.toString()).outputStream().use {
                cropped.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            cropped.recycle()
            true
        } catch (_: Exception) { false }
    }

    fun imageScale(path: CharSequence, newW: Int, newH: Int, outPath: CharSequence): Boolean {
        return try {
            val bmp = BitmapFactory.decodeFile(path.toString()) ?: return false
            val scaled = Bitmap.createScaledBitmap(bmp, newW, newH, true)
            bmp.recycle()
            File(outPath.toString()).outputStream().use {
                scaled.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            scaled.recycle()
            true
        } catch (_: Exception) { false }
    }

    fun imageRotate(path: CharSequence, degrees: Float, outPath: CharSequence): Boolean {
        return try {
            val bmp = BitmapFactory.decodeFile(path.toString()) ?: return false
            val matrix = Matrix().apply { postRotate(degrees) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            bmp.recycle()
            File(outPath.toString()).outputStream().use {
                rotated.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            rotated.recycle()
            true
        } catch (_: Exception) { false }
    }

    fun imageToBase64(path: CharSequence, format: CharSequence = "png"): String {
        return try {
            val bmp = BitmapFactory.decodeFile(path.toString()) ?: return ""
            val baos = ByteArrayOutputStream()
            val fmt = if (format.toString().lowercase() == "jpg") Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
            bmp.compress(fmt, 90, baos)
            bmp.recycle()
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) { "" }
    }

    fun imageFromBase64(base64: CharSequence, outPath: CharSequence): Boolean {
        return try {
            val bytes = Base64.decode(base64.toString(), Base64.NO_WRAP)
            File(outPath.toString()).writeBytes(bytes)
            true
        } catch (_: Exception) { false }
    }

    fun imageSave(path: CharSequence, outPath: CharSequence): Boolean {
        return try {
            File(path.toString()).copyTo(File(outPath.toString()), overwrite = true)
            true
        } catch (_: Exception) { false }
    }

    fun imagePixelColor(path: CharSequence, x: Int, y: Int): String {
        return try {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bmp = BitmapFactory.decodeFile(path.toString(), opts) ?: return ""
            val pixel = bmp.getPixel(x, y)
            bmp.recycle()
            String.format("#%08X", pixel)
        } catch (_: Exception) { "" }
    }

    fun imageWidth(path: CharSequence): Int {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path.toString(), opts)
            opts.outWidth
        } catch (_: Exception) { -1 }
    }

    fun imageHeight(path: CharSequence): Int {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path.toString(), opts)
            opts.outHeight
        } catch (_: Exception) { -1 }
    }

    // ── Intent (from IntentExtensions) ──────────────────────────────────
    fun intentView(url: CharSequence): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url.toString()))
            startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "intentView failed: ${e.message}")
            false
        }
    }

    fun intentSend(text: CharSequence, subject: CharSequence = ""): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text.toString())
                if (subject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject.toString())
            }
            startActivity(Intent.createChooser(intent, "Share"))
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "intentSend failed: ${e.message}")
            false
        }
    }

    fun intentAction(action: CharSequence, data: CharSequence = "", type: CharSequence = ""): Boolean {
        return try {
            val intent = Intent(action.toString()).apply {
                if (data.isNotEmpty()) setData(Uri.parse(data.toString()))
                if (type.isNotEmpty()) setType(type.toString())
            }
            startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "intentAction failed: ${e.message}")
            false
        }
    }

    // ── Network Extended (from NetworkExtensions) ────────────────────────
    fun httpGetEx(url: CharSequence, headersJson: CharSequence? = null): String {
        return try {
            val conn = URL(url.toString()).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (headersJson != null) {
                val json = org.json.JSONObject(headersJson.toString())
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    conn.setRequestProperty(key, json.getString(key))
                }
            }
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            Log.w("Mrboto", "httpGetEx failed: ${e.message}")
            ""
        }
    }

    fun httpPostEx(url: CharSequence, body: CharSequence, headersJson: CharSequence? = null): String {
        return try {
            val conn = URL(url.toString()).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            if (headersJson != null) {
                val json = org.json.JSONObject(headersJson.toString())
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    conn.setRequestProperty(key, json.getString(key))
                }
            }
            DataOutputStream(conn.outputStream).use { it.write(body.toString().toByteArray()) }
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            Log.w("Mrboto", "httpPostEx failed: ${e.message}")
            ""
        }
    }

    fun httpUpload(url: CharSequence, filePath: CharSequence, fieldName: CharSequence = "file"): String {
        return try {
            val boundary = "----MrbotoBoundary${System.currentTimeMillis()}"
            val conn = URL(url.toString()).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.connectTimeout = 30000
            conn.readTimeout = 30000
            val file = File(filePath.toString())
            val output = DataOutputStream(conn.outputStream)
            output.writeBytes("--$boundary\r\n")
            output.writeBytes("Content-Disposition: form-data; name=\"${fieldName}\"; filename=\"${file.name}\"\r\n\r\n")
            file.inputStream().use { it.copyTo(output) }
            output.writeBytes("\r\n--$boundary--\r\n")
            output.flush()
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            Log.w("Mrboto", "httpUpload failed: ${e.message}")
            ""
        }
    }

    fun httpStream(url: CharSequence, headersJson: CharSequence? = null): String {
        return httpGetEx(url, headersJson)
    }

    // ── Overlay (from OverlayExtensions) ─────────────────────────────────
    private val overlayViews = ConcurrentHashMap<Int, View>()

    fun overlayShow(viewId: Int, x: Int, y: Int, width: Int = -2, height: Int = -2): Int {
        return try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val overlayView = View(this)
            val params = WindowManager.LayoutParams(
                width, height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.START
            params.x = x
            params.y = y
            wm.addView(overlayView, params)
            val id = overlayView.hashCode()
            overlayViews[id] = overlayView
            id
        } catch (e: Exception) {
            Log.w("Mrboto", "overlayShow failed: ${e.message}")
            -1
        }
    }

    fun overlayRemove(overlayId: Int): Boolean {
        return try {
            val view = overlayViews.remove(overlayId) ?: return false
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
            true
        } catch (_: Exception) { false }
    }

    // ── Predictive Back (from PredictiveBackExtensions) ──────────────────
    fun predictiveBackEnabled(): Boolean {
        return Build.VERSION.SDK_INT >= 33
    }

    // ── Screen Capture (from ScreenCaptureExtensions) ────────────────────
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var mediaRecorder: MediaRecorder? = null

    fun requestScreenCapture(callbackId: Int): Boolean {
        return try {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val intent = mpm.createScreenCaptureIntent()
            startActivityForResult(intent, 9100)
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "requestScreenCapture failed: ${e.message}")
            false
        }
    }

    fun captureScreen(outPath: CharSequence): Boolean {
        return try {
            val mp = mediaProjection ?: return false
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            val reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, 0x1, 2)
            val vd = mp.createVirtualDisplay(
                "MrbotoCapture", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null
            )
            Thread.sleep(200)
            val image = reader.acquireLatestImage()
            val plane = image?.planes?.get(0)
            val buffer = plane?.buffer
            if (buffer != null) {
                val pixels = ByteArray(buffer.remaining())
                buffer.get(pixels)
                val bmp = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888)
                bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(pixels))
                File(outPath.toString()).outputStream().use {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bmp.recycle()
            }
            image?.close()
            vd?.release()
            reader.close()
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "captureScreen failed: ${e.message}")
            false
        }
    }

    fun startRecordScreen(outPath: CharSequence): Boolean {
        return try {
            val mp = mediaProjection ?: return false
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            recorder.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(metrics.widthPixels, metrics.heightPixels)
                setVideoFrameRate(30)
                setOutputFile(File(outPath.toString()).absolutePath)
                prepare()
            }
            val vd = mp.createVirtualDisplay(
                "MrbotoRecord", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, recorder.surface, null, null
            )
            recorder.start()
            mediaRecorder = recorder
            virtualDisplay = vd
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "startRecordScreen failed: ${e.message}")
            false
        }
    }

    fun stopRecordScreen(): Boolean {
        return try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            virtualDisplay?.release()
            virtualDisplay = null
            true
        } catch (_: Exception) { false }
    }

    // ── Sensor (from SensorExtensions) ───────────────────────────────────
    private val sensorManagers = ConcurrentHashMap<Int, Pair<SensorManager, SensorEventListener>>()
    private val sensorIdGen = AtomicInteger(1)

    fun startGyroscope(callbackId: Int): Int {
        return startSensor(Sensor.TYPE_GYROSCOPE, callbackId)
    }

    fun stopGyroscope(sensorId: Int) {
        stopSensor(sensorId)
    }

    fun startAccelerometer(callbackId: Int): Int {
        return startSensor(Sensor.TYPE_ACCELEROMETER, callbackId)
    }

    fun stopAccelerometer(sensorId: Int) {
        stopSensor(sensorId)
    }

    fun startProximity(callbackId: Int): Int {
        return startSensor(Sensor.TYPE_PROXIMITY, callbackId)
    }

    fun stopProximity(sensorId: Int) {
        stopSensor(sensorId)
    }

    private fun startSensor(type: Int, callbackId: Int): Int {
        return try {
            val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sm.getDefaultSensor(type) ?: return -1
            val id = sensorIdGen.getAndIncrement()
            val mrubyRef = mruby
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val args = event.values.joinToString(",") { it.toString() }
                    mrubyRef.eval("Mrboto.dispatch_callback($callbackId, $args)")
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            sensorManagers[id] = Pair(sm, listener)
            id
        } catch (e: Exception) {
            Log.w("Mrboto", "startSensor failed: ${e.message}")
            -1
        }
    }

    private fun stopSensor(sensorId: Int) {
        val pair = sensorManagers.remove(sensorId) ?: return
        pair.first.unregisterListener(pair.second)
    }

    // ── Shell (from ShellExtensions) ─────────────────────────────────────
    fun shellExec(command: CharSequence, timeout: Int = 10): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command.toString()))
            val finished = process.waitFor((timeout * 1000).toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            if (finished) {
                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                if (stderr.isNotEmpty()) "$stdout\n$stderr".trim() else stdout.trim()
            } else {
                process.destroyForcibly()
                "TIMEOUT"
            }
        } catch (e: Exception) {
            Log.w("Mrboto", "shellExec failed: ${e.message}")
            "ERROR: ${e.message}"
        }
    }

    // ── Threading (from ThreadingExtensions) ─────────────────────────────
    private val threadIdGen = AtomicInteger(1)
    private val activeThreads = ConcurrentHashMap<Int, Thread>()
    private val atomicCounters = ConcurrentHashMap<Int, AtomicInteger>()

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

    // ── Window Info (from WindowInfoExtensions) ──────────────────────────
    fun getCurrentActivityName(): String {
        return try {
            javaClass.name
        } catch (e: Exception) {
            Log.w("Mrboto", "getCurrentActivityName failed: ${e.message}")
            ""
        }
    }

    fun getTopActivityPackage(): String {
        return try {
            packageName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun getCurrentLayoutInfo(): String {
        return try {
            val wm = window ?: return "{}"
            val decorView = wm.decorView
            val obj = org.json.JSONObject()
            obj.put("width", decorView.width)
            obj.put("height", decorView.height)
            obj.put("class", decorView.javaClass.name)
            val vg = decorView as? android.view.ViewGroup
            if (vg != null) {
                obj.put("childCount", vg.childCount)
            }
            obj.toString()
        } catch (_: Exception) { "{}" }
    }

}
