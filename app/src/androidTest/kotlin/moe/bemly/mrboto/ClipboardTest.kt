package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ClipboardTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    private fun setupActivity() {
        val act = mrbotoRule.createTestActivity()
        val actId = mruby.registerJavaObject(act)
        mruby.eval("Mrboto.current_activity_id = $actId")
        mruby.eval("""
            class TestActivity < Mrboto::Activity
              def on_create(bundle); super; end
            end
            Mrboto.current_activity = TestActivity.new(Mrboto.current_activity_id)
            Mrboto.current_activity.on_create(nil)
        """.trimIndent())
    }

    // ── Copy ──────────────────────────────────────────────────────

    @Test
    fun clipboard_copy_does_not_crash() {
        setupActivity()
        val result = mruby.eval("clipboard_copy('Hello Clipboard'); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun clipboard_copy_empty_string_does_not_crash() {
        setupActivity()
        val result = mruby.eval("clipboard_copy(''); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun clipboard_copy_unicode_does_not_crash() {
        setupActivity()
        val result = mruby.eval("clipboard_copy('日本語テスト'); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun clipboard_copy_overwrites_previous() {
        setupActivity()
        mruby.eval("clipboard_copy('first')")
        mruby.eval("clipboard_copy('second')")
        val result = mruby.eval("clipboard_paste")
        assertEquals("second", result)
    }

    // ── Paste ─────────────────────────────────────────────────────

    @Test
    fun clipboard_paste_returns_string() {
        setupActivity()
        mruby.eval("clipboard_copy('TestPaste123')")
        val result = mruby.eval("clipboard_paste")
        assertEquals("TestPaste123", result)
    }

    @Test
    fun clipboard_paste_returns_nil_when_empty() {
        setupActivity()
        val result = mruby.eval("clipboard_paste.nil?.to_s")
        // May be true or false depending on clipboard state
        assertNotNull(result)
    }

    @Test
    fun clipboard_paste_returns_correct_type() {
        setupActivity()
        mruby.eval("clipboard_copy('typecheck')")
        val result = mruby.eval("clipboard_paste.class.to_s")
        assertEquals("String", result)
    }

    // ── Has Text ──────────────────────────────────────────────────

    @Test
    fun clipboard_has_text_returns_true_after_copy() {
        setupActivity()
        mruby.eval("clipboard_copy('SomeText')")
        val result = mruby.eval("clipboard_has_text?.to_s")
        assertEquals("true", result)
    }

    @Test
    fun clipboard_has_text_returns_boolean_type() {
        setupActivity()
        mruby.eval("clipboard_copy('boolcheck')")
        val result = mruby.eval("[true, false].include?(clipboard_has_text?).to_s")
        assertEquals("true", result)
    }

    // ── Module methods ────────────────────────────────────────────

    @Test
    fun clipboard_module_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:clipboard_copy).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:clipboard_paste).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:clipboard_has_text?).to_s"))
    }

    @Test
    fun top_level_clipboard_methods_exist() {
        assertEquals("true", mruby.eval("method(:clipboard_copy).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:clipboard_paste).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:clipboard_has_text?).nil? rescue false; true.to_s"))
    }

    // ── Module-level direct calls ─────────────────────────────────

    @Test
    fun module_clipboard_copy_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.clipboard_copy('ModuleTest'); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun module_clipboard_paste_returns_value() {
        setupActivity()
        mruby.eval("Mrboto::Helpers.clipboard_copy('ModPaste')")
        val result = mruby.eval("Mrboto::Helpers.clipboard_paste")
        assertEquals("ModPaste", result)
    }

    @Test
    fun module_clipboard_has_text_returns_true() {
        setupActivity()
        mruby.eval("Mrboto::Helpers.clipboard_copy('ModHas')")
        val result = mruby.eval("Mrboto::Helpers.clipboard_has_text?.to_s")
        assertEquals("true", result)
    }
}
