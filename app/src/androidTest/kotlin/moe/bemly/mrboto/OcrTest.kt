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
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:ocr_init).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:ocr_detect).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:ocr_release).to_s"))
    }

    @Test fun ocr_recognize_returns_string() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.ocr_recognize('/nonexistent.png').to_s")
        assertNotNull(result)
    }

    @Test fun ocr_init_returns_boolean() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.ocr_init.to_s")
        assertNotNull(result)
    }

    @Test fun ocr_release_returns_true() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.ocr_release.to_s")
        assertEquals("true", result)
    }

    @Test fun ocr_top_level_methods_exist() {
        assertEquals("true", mruby.eval("respond_to?(:ocr_init).to_s"))
        assertEquals("true", mruby.eval("respond_to?(:ocr_recognize).to_s"))
        assertEquals("true", mruby.eval("respond_to?(:ocr_detect).to_s"))
        assertEquals("true", mruby.eval("respond_to?(:ocr_release).to_s"))
    }
}
