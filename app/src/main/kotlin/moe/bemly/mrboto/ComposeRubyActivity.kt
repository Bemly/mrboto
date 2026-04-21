package moe.bemly.mrboto

/**
 * Generic Compose-backed Ruby Activity — loads any Ruby script using the Compose DSL.
 *
 * Script resolution order:
 *   1. Intent extra `mrboto_script_path` (from startRubyActivity)
 *   2. AndroidManifest meta-data `mrboto_script`
 *
 * Usage in AndroidManifest.xml:
 *   <activity android:name="moe.bemly.mrboto.ComposeRubyActivity">
 *     <meta-data android:name="mrboto_script" android:value="my_compose_activity.rb" />
 *   </activity>
 */
class ComposeRubyActivity : MrbotoComposeActivityBase() {
    override fun getScriptPath(): String? = null
}
