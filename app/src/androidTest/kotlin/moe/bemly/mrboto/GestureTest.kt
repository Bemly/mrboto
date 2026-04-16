package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GestureTest {
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

    @Test fun gesture_click_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.gesture_click(100, 200); 'ok'")
        assertEquals("ok", result)
    }

    @Test fun gesture_long_click_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.gesture_long_click(100, 200); 'ok'")
        assertEquals("ok", result)
    }

    @Test fun gesture_swipe_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.gesture_swipe(0, 0, 100, 100); 'ok'")
        assertEquals("ok", result)
    }

    @Test fun gesture_scroll_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.gesture_scroll(0, 0, 100, 100); 'ok'")
        assertEquals("ok", result)
    }

    @Test fun gesture_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:gesture_click).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:gesture_long_click).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:gesture_swipe).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:gesture_scroll).to_s"))
    }
}
