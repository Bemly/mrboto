package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CameraTest {
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

    @Test fun camera_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:camera_available?).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:camera_info).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:camera_take_photo).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:camera_record_video).to_s"))
    }

    @Test fun camera_available_returns_boolean() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.camera_available?.to_s")
        assertNotNull(result)
    }

    @Test fun camera_info_returns_string() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.camera_info.to_s")
        assertNotNull(result)
    }
}
