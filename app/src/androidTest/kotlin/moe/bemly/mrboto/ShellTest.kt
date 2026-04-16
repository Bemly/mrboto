package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ShellTest {
    @get:Rule val mrbotoRule = MrbotoTestRule()
    private val mruby get() = mrbotoRule.mruby

    private fun setupActivity() {
        val act = mrbotoRule.createTestActivity()
        val actId = mruby.registerJavaObject(act)
        mruby.eval("Mrboto.current_activity_id = $actId")
        mruby.eval("""
            class TestActivity < Mrboto::Activity; def on_create(bundle); super; end; end
            Mrboto.current_activity = TestActivity.new(Mrboto.current_activity_id)
            Mrboto.current_activity.on_create(nil)
        """.trimIndent())
    }

    @Test fun shell_exec_echo() {
        setupActivity()
        val result = mruby.eval("shell_exec('echo hello')")
        assertEquals("hello", result)
    }

    @Test fun shell_exec_pwd() {
        setupActivity()
        val result = mruby.eval("shell_exec('pwd')")
        assertTrue(result.isNotEmpty())
    }

    @Test fun shell_exec_module_method() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.shell_exec('echo test')")
        assertEquals("test", result)
    }

    @Test fun shell_exec_timeout() {
        setupActivity()
        val result = mruby.eval("shell_exec('sleep 100', timeout: 1)")
        assertEquals("TIMEOUT", result)
    }
}
