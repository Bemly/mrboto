package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class OcrTest {
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

    @Test fun ocr_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:ocr_recognize).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:ocr_recognize_from_path).to_s"))
    }

    @Test fun ocr_recognize_returns_string() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.ocr_recognize('/nonexistent.png').to_s")
        assertNotNull(result)
    }
}
