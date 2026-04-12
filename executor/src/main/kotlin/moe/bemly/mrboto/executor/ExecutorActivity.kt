package moe.bemly.mrboto.executor

import moe.bemly.mrboto.MrbotoActivityBase

/**
 * Ruby Executor Activity — a terminal-like UI for running Ruby code.
 * Fully driven by Ruby script at assets/ruby_executor.rb.
 */
class ExecutorActivity : MrbotoActivityBase() {

    override fun getScriptPath(): String = "ruby_executor.rb"
}
