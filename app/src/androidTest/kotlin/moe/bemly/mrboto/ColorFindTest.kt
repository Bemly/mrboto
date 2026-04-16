package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ColorFindTest {
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

    @Test fun color_find_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:get_color_at).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:find_color).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:find_color_fuzzy).to_s"))
    }

    @Test fun get_color_at_invalid_path_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.get_color_at('/nonexistent.png', 0, 0)")
        assertNotNull(result)
    }
}
