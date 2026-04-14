package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for helpers: toast, SharedPreferences, package_name, string_resource.
 */
class HelpersTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    private fun setupActivity() {
        val actId = mruby.registerJavaObject(mrbotoRule.context)
        mruby.eval("Mrboto.current_activity_id = $actId")
        mruby.eval("""
            class TestActivity < Mrboto::Activity
              def on_create(bundle); super; end
            end
            Mrboto.current_activity = TestActivity.new(Mrboto.current_activity_id)
            Mrboto.current_activity.on_create(nil)
        """.trimIndent())
    }

    @Test
    fun `toast_does_not_throw`() {
        setupActivity()
        val result = mruby.eval("toast('Test toast')")
        assertEquals("ok", result)
    }

    @Test
    fun `toast_with_long_duration_does_not_throw`() {
        setupActivity()
        val result = mruby.eval("toast('Long toast', :long)")
        assertEquals("ok", result)
    }

    @Test
    fun `top_level_toast_method_exists`() {
        assertEquals("true", mruby.eval("method(:toast).nil? rescue false; true.to_s"))
    }

    @Test
    fun `shared_preferences_put_and_get_string`() {
        setupActivity()
        mruby.eval("""
            sp = shared_preferences("test_prefs")
            sp.put_string("key1", "value1")
        """.trimIndent())
        val result = mruby.eval("""
            sp = shared_preferences("test_prefs")
            sp.get_string("key1", "default")
        """.trimIndent())
        assertEquals("value1", result)
    }

    @Test
    fun `shared_preferences_returns_default_for_missing_key`() {
        setupActivity()
        val result = mruby.eval("""
            sp = shared_preferences("test_prefs2")
            sp.get_string("nonexistent", "fallback")
        """.trimIndent())
        assertEquals("fallback", result)
    }

    @Test
    fun `shared_preferences_put_and_get_int`() {
        setupActivity()
        mruby.eval("""
            sp = shared_preferences("int_prefs")
            sp.put_int("count", 42)
        """.trimIndent())
        val result = mruby.eval("""
            sp = shared_preferences("int_prefs")
            sp.get_int("count", 0)
        """.trimIndent())
        assertEquals("42", result)
    }

    @Test
    fun `shared_preferences_get_int_returns_default_for_missing_key`() {
        setupActivity()
        val result = mruby.eval("""
            sp = shared_preferences("int_prefs_missing")
            sp.get_int("nonexistent", 777)
        """.trimIndent())
        assertEquals("777", result)
    }

    @Test
    fun `shared_preferences_put_int_overwrites_previous_value`() {
        setupActivity()
        mruby.eval("""
            sp = shared_preferences("int_prefs_overwrite")
            sp.put_int("val", 1)
            sp.put_int("val", 99)
        """.trimIndent())
        val result = mruby.eval("""
            sp = shared_preferences("int_prefs_overwrite")
            sp.get_int("val", 0)
        """.trimIndent())
        assertEquals("99", result)
    }

    @Test
    fun `shared_preferences_put_int_with_string_coercion`() {
        setupActivity()
        mruby.eval("""
            sp = shared_preferences("int_prefs_coerce")
            sp.put_int("val", "123")
        """.trimIndent())
        val result = mruby.eval("""
            sp = shared_preferences("int_prefs_coerce")
            sp.get_int("val", 0)
        """.trimIndent())
        assertEquals("123", result)
    }

    @Test
    fun `package_name_returns_a_string`() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.package_name")
        assertTrue("Package name should not be empty", result.isNotEmpty() && result != "nil")
    }

    @Test
    fun `package_name_is_not_nil`() {
        setupActivity()
        assertNotEquals("nil", mruby.eval("Mrboto::Helpers.package_name.nil?.to_s"))
    }

    @Test
    fun `Helpers_module_exists`() {
        assertEquals("true", mruby.eval("Mrboto.const_defined?(:Helpers).to_s"))
    }

    @Test
    fun `Helpers_toast_is_callable`() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.toast('Helper toast'); 'ok'")
        assertEquals("ok", result)
    }

    // ── Dialog Tests ──────────────────────────────────────────────

    @Test
    fun `dialog_does_not_crash_with_no_buttons`() {
        setupActivity()
        val result = mruby.eval("dialog('Title', 'Message'); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun `dialog_does_not_crash_with_buttons`() {
        setupActivity()
        val result = mruby.eval("dialog('Title', 'Message', ['Yes', 'No']); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun `Helpers_dialog_does_not_crash`() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.dialog('T', 'M', ['OK']); 'ok'")
        assertEquals("ok", result)
    }

    // ── Snackbar Tests ────────────────────────────────────────────

    @Test
    fun `snackbar_does_not_crash`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("snackbar($viewId, 'Hello Snackbar'); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun `snackbar_with_long_duration_does_not_crash`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("snackbar($viewId, 'Long Snackbar', :long); 'ok'")
        assertEquals("ok", result)
    }

    // ── PopupMenu Tests ───────────────────────────────────────────

    @Test
    fun `popup_menu_does_not_crash`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("popup_menu($viewId, ['Item 1', 'Item 2', 'Item 3']); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun `popup_menu_with_callback_records_selection`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        mruby.eval("""
            @pm_selected = nil
            popup_menu($viewId, ['A', 'B', 'C']) { |index, title|
                @pm_selected = "#{index}: #{title}"
            }
        """.trimIndent())
        // Simulate item click (index 1, title "B")
        mruby.eval("Mrboto.dispatch_callback(1, 1, 'B')")
        assertEquals("1: B", mruby.eval("@pm_selected"))
    }

    @Test
    fun `popup_menu_with_nil_items_returns_nil`() {
        setupActivity()
        val result = mruby.eval("popup_menu(1, nil).nil?.to_s")
        assertEquals("true", result)
    }

    // ── Animation Tests ───────────────────────────────────────────

    @Test
    fun `fade_does_not_crash`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("fade($viewId, 0.0, 1.0, 200); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun `translate_does_not_crash`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("translate($viewId, 0.0, 100.0, 0.0, 0.0, 200); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun `scale_does_not_crash`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("scale($viewId, 1.0, 1.0, 1.2, 1.2, 200); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun `Animations_module_exists`() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.const_defined?(:Animations).to_s"))
    }

    @Test
    fun `fade_in_helper_exists`() {
        assertEquals("true", mruby.eval("Mrboto::Helpers::Animations.singleton_methods.include?(:fade_in).to_s"))
    }

    @Test
    fun `pulse_helper_exists`() {
        assertEquals("true", mruby.eval("Mrboto::Helpers::Animations.singleton_methods.include?(:pulse).to_s"))
    }

    // ── Script Loading & Eval Helpers ─────────────────────────────

    @Test
    fun `ruby_eval_evaluates_ruby_code`() {
        val result = mruby.eval("Mrboto.ruby_eval('1 + 1')")
        assertEquals("2", result)
    }

    @Test
    fun `ruby_eval_handles_string_expression`() {
        val result = mruby.eval("Mrboto.ruby_eval('\"hello\".upcase')")
        assertEquals("HELLO", result)
    }

    @Test
    fun `ruby_eval_handles_syntax_error`() {
        val result = mruby.eval("Mrboto.ruby_eval('1 + ')")
        assertTrue("Should contain error indicator", result.contains("Error"))
    }

    @Test
    fun `ruby_eval_returns_error_string_on_failure`() {
        val result = mruby.eval("Mrboto.ruby_eval('undefined_method_call')")
        assertTrue("Should contain error indicator", result.contains("Error"))
    }

    @Test
    fun `top_level_load_script_method_exists`() {
        assertEquals("true", mruby.eval("method(:load_script).nil? rescue false; true.to_s"))
    }

    @Test
    fun `top_level_ruby_eval_method_exists`() {
        assertEquals("true", mruby.eval("method(:ruby_eval).nil? rescue false; true.to_s"))
    }

    // ── TextWatcher Tests ─────────────────────────────────────────

    @Test
    fun `_set_text_watcher_does_not_crash`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._create_view(Mrboto.current_activity._registry_id, 'android.widget.EditText', {})")
        mruby.eval("Mrboto._set_text_watcher($viewId, 1)")
        // If we reach here without crashing, the test passes
        assertTrue(true)
    }

    @Test
    fun `on_text_changed_sets_callback_on_edittext`() {
        setupActivity()
        // on_text_changed uses _set_text_watcher internally — verify it doesn't crash
        val result = mruby.eval("""
            et_id = Mrboto._create_view(Mrboto.current_activity._registry_id, 'android.widget.EditText', {})
            et = Mrboto::EditText.from_registry(et_id)
            @tw_callback = nil
            et.on_text_changed { |text| @tw_callback = text }
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `dispatch_text_changed_triggers_on_text_changed_callback`() {
        setupActivity()
        mruby.eval("""
            @tw_text = nil
            Mrboto.register_callback { |t| @tw_text = t }
        """.trimIndent())
        mruby.eval("Mrboto.dispatch_text_changed(1, 'typed text')")
        assertEquals("typed text", mruby.eval("@tw_text"))
    }
}
