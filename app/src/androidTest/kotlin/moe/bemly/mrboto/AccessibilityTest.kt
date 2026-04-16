package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AccessibilityTest {
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

    @Test fun accessibility_enabled_returns_boolean() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.accessibility_enabled?.to_s")
        assertNotNull(result)
    }

    @Test fun accessibility_touch_exploration_returns_boolean() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.accessibility_touch_exploration?.to_s")
        assertNotNull(result)
    }

    @Test fun accessibility_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:accessibility_enabled?).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:accessibility_touch_exploration?).to_s"))
    }
}
