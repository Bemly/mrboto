package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IntentTest {
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

    @Test fun intent_view_does_not_crash() {
        setupActivity()
        val result = mruby.eval("""
            Mrboto::Helpers.intent_view("https://example.com"); 'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test fun intent_send_does_not_crash() {
        setupActivity()
        val result = mruby.eval("""
            Mrboto::Helpers.intent_send("Hello", subject: "Test"); 'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test fun intent_action_does_not_crash() {
        setupActivity()
        val result = mruby.eval("""
            Mrboto::Helpers.intent_action("android.intent.action.MAIN"); 'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test fun intent_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:intent_view).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:intent_send).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:intent_action).to_s"))
    }
}
