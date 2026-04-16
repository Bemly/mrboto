package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CoroutineTest {
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

    @Test fun run_async_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.run_async { }; 'ok'")
        assertEquals("ok", result)
    }

    @Test fun run_delayed_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.run_delayed(100) { }; 'ok'")
        assertEquals("ok", result)
    }

    @Test fun timer_start_returns_id() {
        setupActivity()
        val result = mruby.eval("""
            id = Mrboto::Helpers.timer_start(1000) { }
            Mrboto::Helpers.timer_stop(id)
            id.to_s
        """.trimIndent())
        assertNotEquals("-1", result)
    }

    @Test fun timer_once_returns_id() {
        setupActivity()
        val result = mruby.eval("""
            id = Mrboto::Helpers.timer_once(100) { }
            id.to_s
        """.trimIndent())
        assertNotEquals("-1", result)
    }

    @Test fun timer_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:run_async).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:run_delayed).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:timer_start).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:timer_stop).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:timer_once).to_s"))
    }
}
