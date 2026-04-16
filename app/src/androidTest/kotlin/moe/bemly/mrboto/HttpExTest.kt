package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class HttpExTest {
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

    @Test fun http_ex_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:http_get_ex).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:http_post_ex).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:http_upload).to_s"))
    }
}
