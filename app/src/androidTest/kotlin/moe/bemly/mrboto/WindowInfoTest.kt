package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WindowInfoTest {
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

    @Test fun window_info_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:current_activity_name).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:current_layout_info).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:top_activity_package).to_s"))
    }

    @Test fun current_activity_name_returns_string() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.current_activity_name.to_s")
        assertNotNull(result)
    }

    @Test fun current_layout_info_returns_string() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.current_layout_info.to_s")
        assertNotNull(result)
    }

    @Test fun top_activity_package_returns_string() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.top_activity_package.to_s")
        assertNotNull(result)
    }
}
