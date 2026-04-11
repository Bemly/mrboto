package com.mrboto.demo

import com.mrboto.MrbotoActivityBase

/**
 * Demo Activity — fully driven by Ruby.
 *
 * The Ruby script at assets/main_activity.rb defines a class
 * inheriting from Mrboto::Activity and sets up the UI using
 * the widget DSL.
 */
class DemoActivity : MrbotoActivityBase() {

    override fun getScriptPath(): String = "main_activity.rb"
}
