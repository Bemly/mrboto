package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SensorTest {
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

    @Test fun sensor_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:start_gyroscope).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:stop_gyroscope).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:start_accelerometer).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:stop_accelerometer).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:start_proximity).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:stop_proximity).to_s"))
    }

    @Test fun stop_gyroscope_invalid_id_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.stop_gyroscope(-1); 'ok'")
        assertEquals("ok", result)
    }

    @Test fun stop_accelerometer_invalid_id_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.stop_accelerometer(-1); 'ok'")
        assertEquals("ok", result)
    }

    @Test fun stop_proximity_invalid_id_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.stop_proximity(-1); 'ok'")
        assertEquals("ok", result)
    }
}
