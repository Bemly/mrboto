package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ThreadingTest {
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

    @Test fun threading_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:thread_start).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:thread_join).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:atomic_get).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:atomic_set).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:atomic_increment).to_s"))
    }

    @Test fun atomic_set_get_round_trip() {
        setupActivity()
        val result = mruby.eval("""
            Mrboto::Helpers.atomic_set(1, 42)
            Mrboto::Helpers.atomic_get(1).to_s
        """.trimIndent())
        assertEquals("42", result)
    }

    @Test fun atomic_increment_returns_incremented() {
        setupActivity()
        val result = mruby.eval("""
            Mrboto::Helpers.atomic_set(2, 10)
            Mrboto::Helpers.atomic_increment(2).to_s
        """.trimIndent())
        assertEquals("11", result)
    }

    @Test fun thread_join_invalid_id_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.thread_join(-1); 'ok'")
        assertEquals("ok", result)
    }
}
