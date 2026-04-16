package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ScreenCaptureTest {
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

    @Test fun screen_capture_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:request_screen_capture).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:capture_screen).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:start_record_screen).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:stop_record_screen).to_s"))
    }

    @Test fun stop_record_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.stop_record_screen; 'ok'")
        assertEquals("ok", result)
    }
}
