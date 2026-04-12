package moe.bemly.mrboto.executor

import moe.bemly.mrboto.MrbotoActivityBase

/**
 * Ruby Executor Activity — a terminal-like UI for running Ruby code.
 * Fully driven by Ruby script at assets/ruby_executor.rb.
 */
class ExecutorActivity : MrbotoActivityBase() {

    override fun getScriptPath(): String = "ruby_executor.rb"

    /**
     * Load and execute a Ruby script from assets.
     * Called from Ruby via call_java_method("loadAssetScript", path).
     */
    fun loadAssetScript(path: String): String {
        val script = assets.open(path).bufferedReader().use { it.readText() }
        return getMRuby().loadScript(script)
    }
}
