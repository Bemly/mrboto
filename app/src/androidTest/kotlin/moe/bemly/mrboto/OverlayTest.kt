package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OverlayTest {
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

    @Test fun overlay_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:overlay_show).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:overlay_remove).to_s"))
    }

    @Test fun overlay_remove_invalid_id_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.overlay_remove(-1); 'ok'")
        assertEquals("ok", result)
    }
}
