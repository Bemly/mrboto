package moe.bemly.mrboto

import android.app.Application
import android.content.res.AssetManager
import android.util.Log

/**
 * Application subclass that bootstraps the global MRuby runtime
 * at app startup. All Activities share this single MRuby instance.
 *
 * To use, add to AndroidManifest.xml:
 *   <application android:name="moe.bemly.mrboto.MrbotoApplication" ...>
 */
class MrbotoApplication : Application() {

    companion object {
        private const val TAG = "MrbotoApp"
    }

    internal lateinit var mruby: MRuby

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Bootstrapping mruby VM")

        mruby = MRuby()
        mruby.registerAndroidClasses()

        // Load core mrboto Ruby library from assets
        loadCoreLibrary(assets)

        Log.i(TAG, "mrboto framework ready")
    }

    /**
     * Load all core mrboto Ruby scripts from assets/mrboto/.
     */
    private fun loadCoreLibrary(assets: AssetManager) {
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
            try {
                val result = mruby.loadAssetScript(assets, file)
                Log.d(TAG, "Loaded $file: $result")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $file: ${e.message}")
            }
        }
    }

    override fun onTerminate() {
        mruby.close()
        super.onTerminate()
    }
}
