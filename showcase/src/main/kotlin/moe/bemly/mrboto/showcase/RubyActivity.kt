package moe.bemly.mrboto.showcase

import moe.bemly.mrboto.MrbotoActivityBase

/**
 * Generic Ruby Activity — loads any Ruby script via Intent extra.
 * Used by Ruby's start_activity to dynamically route to any script.
 */
class RubyActivity : MrbotoActivityBase() {
    override fun getScriptPath(): String? = null
}
