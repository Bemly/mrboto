package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DeviceControlTest {
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

    @Test fun device_control_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:set_volume).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:get_volume).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:set_brightness).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:get_brightness).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:vibrate).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:vibrate_pattern).to_s"))
    }

    @Test fun get_volume_returns_number() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.get_volume(3).to_s")
        assertNotNull(result)
    }

    @Test fun get_brightness_returns_number() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.get_brightness.to_s")
        assertNotNull(result)
    }

    @Test fun vibrate_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.vibrate(duration: 50); 'ok'")
        assertEquals("ok", result)
    }
}
