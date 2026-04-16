package moe.bemly.mrboto

/**
 * Minimal MrbotoActivityBase subclass for instrumented tests.
 * Provides a real Activity instance so call_java_method can find
 * methods defined on MrbotoActivityBase via reflection.
 */
class TestMrbotoActivity : MrbotoActivityBase() {
    override fun getScriptPath(): String? = null
}
