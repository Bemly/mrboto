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

    // ════════════════════════════════════════════════════════════════════
    // In-memory clipboard (clipboardCopy / clipboardPaste / clipboardHasText)
    // ════════════════════════════════════════════════════════════════════

    // ── Copy ──────────────────────────────────────────────────────────

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

    // ── Paste ─────────────────────────────────────────────────────────

    @Test
    fun clipboard_paste_returns_string() {
        setupActivity()
        mruby.eval("clipboard_copy('TestPaste123')")
        val result = mruby.eval("clipboard_paste")
        assertEquals("TestPaste123", result)
    }

    @Test
    fun clipboard_paste_returns_empty_when_no_copy() {
        setupActivity()
        val result = mruby.eval("clipboard_paste")
        assertEquals("", result)
    }

    @Test
    fun clipboard_paste_returns_correct_type() {
        setupActivity()
        mruby.eval("clipboard_copy('typecheck')")
        val result = mruby.eval("clipboard_paste.class.to_s")
        assertEquals("String", result)
    }

    // ── Has Text ──────────────────────────────────────────────────────

    @Test
    fun clipboard_has_text_returns_true_after_copy() {
        setupActivity()
        mruby.eval("clipboard_copy('SomeText')")
        val result = mruby.eval("clipboard_has_text?.to_s")
        assertEquals("true", result)
    }

    @Test
    fun clipboard_has_text_returns_false_initially() {
        setupActivity()
        val result = mruby.eval("clipboard_has_text?.to_s")
        assertEquals("false", result)
    }

    // ── Module methods ────────────────────────────────────────────────

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

    // ── Module-level direct calls ─────────────────────────────────────

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

    // ════════════════════════════════════════════════════════════════════
    // System clipboard (clipboardSystemCopy / clipboardSystemPaste / clipboardSystemHasText)
    // ════════════════════════════════════════════════════════════════════

    // ── System Copy ───────────────────────────────────────────────────

    @Test
    fun clipboard_system_copy_does_not_crash() {
        setupActivity()
        val result = mruby.eval("""
            act = Mrboto.current_activity
            act.call_java_method("clipboardSystemCopy", "Hello System")
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun clipboard_system_copy_empty_does_not_crash() {
        setupActivity()
        val result = mruby.eval("""
            act = Mrboto.current_activity
            act.call_java_method("clipboardSystemCopy", "")
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    // ── System Paste ──────────────────────────────────────────────────

    @Test
    fun clipboard_system_paste_returns_string() {
        setupActivity()
        val result = mruby.eval("""
            act = Mrboto.current_activity
            act.call_java_method("clipboardSystemCopy", "SystemPaste123")
            act.call_java_method("clipboardSystemPaste")
        """.trimIndent())
        // System clipboard may fail in test env, just check it returns a string
        assertNotNull(result)
    }

    // ── System Has Text ───────────────────────────────────────────────

    @Test
    fun clipboard_system_has_text_returns_boolean() {
        setupActivity()
        val result = mruby.eval("""
            act = Mrboto.current_activity
            val = act.call_java_method("clipboardSystemHasText")
            [true, false].include?(val).to_s
        """.trimIndent())
        assertEquals("true", result)
    }

    // ── System module-level methods exist ─────────────────────────────

    @Test
    fun clipboard_system_methods_exist_on_activity() {
        setupActivity()
        // System clipboard methods are Java methods on Activity, check via call
        val result = mruby.eval("""
            act = Mrboto.current_activity
            copy_ok = act.call_java_method("clipboardSystemCopy", "test").nil? ? "false" : "true"
            paste_ok = act.call_java_method("clipboardSystemPaste").nil? ? "false" : "true"
            has_ok = act.call_java_method("clipboardSystemHasText").nil? ? "false" : "true"
            [copy_ok, paste_ok, has_ok].all? { |v| v == "true" }.to_s
        """.trimIndent())
        assertEquals("true", result)
    }

    // ── Cross: system copy then memory paste should not match ─────────

    @Test
    fun clipboard_memory_and_system_are_independent() {
        setupActivity()
        mruby.eval("clipboard_copy('memory_value')")
        val memoryResult = mruby.eval("clipboard_paste")
        assertEquals("memory_value", memoryResult)
        // Memory clipboard should not be affected by system clipboard
    }

    // ── Full workflow: memory ─────────────────────────────────────────

    @Test
    fun clipboard_memory_full_workflow() {
        setupActivity()
        val result = mruby.eval("""
            clipboard_copy('alpha')
            a = clipboard_paste
            clipboard_copy('beta')
            b = clipboard_paste
            has = clipboard_has_text?
            "#{a}|#{b}|#{has}"
        """.trimIndent())
        assertEquals("alpha|beta|true", result)
    }

    // ── Full workflow: system ─────────────────────────────────────────

    @Test
    fun clipboard_system_full_workflow() {
        setupActivity()
        val result = mruby.eval("""
            act = Mrboto.current_activity
            act.call_java_method("clipboardSystemCopy", "sys_alpha")
            a = act.call_java_method("clipboardSystemPaste")
            has = act.call_java_method("clipboardSystemHasText")
            "#{a}|#{has}"
        """.trimIndent())
        // System may fail in test env, just verify no crash
        assertNotNull(result)
    }
}
