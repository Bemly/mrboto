package moe.bemly.mrboto

/**
 * Generic Ruby Activity — loads any Ruby script without app-specific Kotlin code.
 *
 * Script resolution order:
 *   1. Intent extra `mrboto_script_path` (from startRubyActivity)
 *   2. AndroidManifest meta-data `mrboto_script`
 *
 * Usage in AndroidManifest.xml:
 *   <activity android:name="moe.bemly.mrboto.RubyActivity">
 *     <meta-data android:name="mrboto_script" android:value="main_activity.rb" />
 *   </activity>
 */
class RubyActivity : MrbotoActivityBase() {
    override fun getScriptPath(): String? = null
}
