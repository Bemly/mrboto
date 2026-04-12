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
     * Uses CharSequence param type because C side maps Ruby strings
     * to CharSequence for reflection (Java auto-converts String → CharSequence).
     */
    fun loadAssetScript(path: CharSequence): String {
        val script = assets.open(path.toString()).bufferedReader().use { it.readText() }
        return getMRuby().loadScript(script)
    }

    /**
     * Execute a raw Ruby string via mruby eval.
     * Used by the custom input field.
     */
    fun evalRuby(code: CharSequence): String {
        return getMRuby().eval(code.toString())
    }

    /**
     * Load and execute a Ruby script from the file system.
     * Used for importing external .rb files.
     */
    fun loadFileScript(path: CharSequence): String {
        val script = java.io.File(path.toString()).readText()
        return getMRuby().loadScript(script)
    }
}
