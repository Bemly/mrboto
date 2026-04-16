package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PredictiveBackTest {
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

    @Test fun predictive_back_enabled_returns_boolean() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.predictive_back_enabled?.to_s")
        assertNotNull(result)
    }

    @Test fun predictive_back_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:predictive_back_enabled?).to_s"))
    }
}
