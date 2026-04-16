package moe.bemly.mrboto

import android.os.Bundle

/**
 * Minimal MrbotoActivityBase subclass for instrumented tests.
 * Provides a real Activity instance so call_java_method can find
 * methods defined on MrbotoActivityBase via reflection.
 */
class TestMrbotoActivity : MrbotoActivityBase() {
    override fun getScriptPath(): String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Skip MrbotoActivityBase.onCreate which tries to load mruby
        // scripts and requires a MrbotoApplication. The base Context is
        // already set by reflection-based attachBaseContext() before
        // any onCreate runs.
    }
}
