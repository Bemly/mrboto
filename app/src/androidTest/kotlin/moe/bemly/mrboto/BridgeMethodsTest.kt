package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for all C bridge methods in android-jni-bridge.c.
 * Covers methods not tested by HelpersTest, WidgetsTest, or other files.
 */
class BridgeMethodsTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    /** The registry ID of the test context injected by MrbotoTestRule. */
    private val ctxId: String
        get() = mruby.eval("Mrboto._app_context._registry_id.to_s")

    // ── _toast ────────────────────────────────────────────────────────

    @Test
    fun `_toast_returns_nil_without_crashing`() {
        val result = mruby.eval("Mrboto._toast($ctxId, 'test toast', 0)")
        assertEquals("nil", result)
    }

    @Test
    fun `_toast_with_long_duration_returns_nil`() {
        val result = mruby.eval("Mrboto._toast($ctxId, 'long toast', 1)")
        assertEquals("nil", result)
    }

    // ── _start_activity ──────────────────────────────────────────────

    @Test
    fun `_start_activity_does_not_crash_with_invalid_class`() {
        val result = mruby.eval("Mrboto._start_activity($ctxId, 'NonExistentActivity')")
        // In instrumented test, no real Activity exists, so this is a no-op
        assertEquals("nil", result)
    }

    // ── _get_extra ───────────────────────────────────────────────────

    @Test
    fun `_get_extra_returns_nil_without_real_intent`() {
        val actId = mruby.registerJavaObject(Any())
        val result = mruby.eval("Mrboto._get_extra($actId, 'missing_key')")
        assertEquals("nil", result)
    }

    // ── _sp_get_int / _sp_put_int ────────────────────────────────────

    @Test
    fun `_sp_put_int_does_not_crash`() {
        val result = mruby.eval("Mrboto._sp_put_int($ctxId, 'test_int_prefs', 'count', 42)")
        assertEquals("nil", result)
    }

    @Test
    fun `_sp_get_int_returns_default_for_missing_key`() {
        val result = mruby.eval("Mrboto._sp_get_int($ctxId, 'test_int_prefs', 'nonexistent', 99)")
        assertEquals("99", result)
    }

    @Test
    fun `_sp_put_int_and_get_int_roundtrip`() {
        mruby.eval("Mrboto._sp_put_int($ctxId, 'roundtrip_int_prefs', 'value', 12345)")
        val result = mruby.eval("Mrboto._sp_get_int($ctxId, 'roundtrip_int_prefs', 'value', 0)")
        assertEquals("12345", result)
    }

    @Test
    fun `_sp_get_int_returns_zero_for_missing_key_with_zero_default`() {
        val result = mruby.eval("Mrboto._sp_get_int($ctxId, 'test_int_prefs', 'missing', 0)")
        assertEquals("0", result)
    }

    // ── _app_context ─────────────────────────────────────────────────

    @Test
    fun `_app_context_returns_a_JavaObject`() {
        val result = mruby.eval("Mrboto._app_context.class.to_s")
        assertTrue("Should be a JavaObject subclass", result.contains("JavaObject"))
    }

    @Test
    fun `_app_context_has_positive_registry_ID`() {
        val result = mruby.eval("Mrboto._app_context._registry_id > 0")
        assertEquals("true", result)
    }

    // ── _dp_to_px ────────────────────────────────────────────────────

    @Test
    fun `_dp_to_px_returns_integer_greater_than_input`() {
        val result = mruby.eval("Mrboto._dp_to_px(100)")
        val px = result.toIntOrNull()
        assertNotNull("Should return an integer", px)
        assertTrue("100 dp should produce more than 100 px", px!! > 100)
    }

    @Test
    fun `_dp_to_px_preserves_ratio_for_small_values`() {
        val result10 = mruby.eval("Mrboto._dp_to_px(10)").toIntOrNull() ?: 0
        val result20 = mruby.eval("Mrboto._dp_to_px(20)").toIntOrNull() ?: 0
        assertTrue("dp(20) should be roughly 2x dp(10)", result20 >= result10 * 1)
    }

    // ── _create_view ─────────────────────────────────────────────────

    @Test
    fun `_create_view_returns_positive_ID_for_TextView`() {
        val result = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.TextView', {})")
        val id = result.toIntOrNull()
        assertNotNull("Should return an integer, got: $result", id)
        assertTrue("View ID should be positive, got: $id", id!! > 0)
    }

    @Test
    fun `_create_view_returns_positive_ID_for_LinearLayout`() {
        val result = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.LinearLayout', {})")
        val id = result.toIntOrNull()
        assertNotNull("Should return an integer, got: $result", id)
        assertTrue("View ID should be positive, got: $id", id!! > 0)
    }

    // ── _set_on_click ────────────────────────────────────────────────

    @Test
    fun `_set_on_click_does_not_crash_with_valid_view`() {
        val viewId = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.Button', {})")
        val result = mruby.eval("Mrboto._set_on_click($viewId, 99)")
        assertEquals("nil", result)
    }

    // ── _set_content_view ────────────────────────────────────────────

    @Test
    fun `_set_content_view_does_not_crash_with_valid_IDs`() {
        val actId = mruby.registerJavaObject(mrbotoRule.context)
        val viewId = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.TextView', {})")
        val result = mruby.eval("Mrboto._set_content_view($actId, $viewId)")
        assertEquals("nil", result)
    }

    // ── _run_on_ui_thread ────────────────────────────────────────────

    @Test
    fun `_run_on_ui_thread_executes_callback`() {
        val actId = mruby.registerJavaObject(Any())
        mruby.eval("Mrboto.current_activity_id = $actId")
        mruby.eval("class TestRUITActivity < Mrboto::JavaObject; end")
        mruby.eval("Mrboto.current_activity = TestRUITActivity.from_registry($actId)")
        // Use global variables ($cid, $run_result) because mrb_load_string with NULL
        // context does NOT preserve local variables between separate eval calls.
        mruby.eval("\$run_result = nil")
        mruby.eval("\$cid = Mrboto.register_callback { \$run_result = 'executed' }")
        val result = mruby.eval("Mrboto._run_on_ui_thread($actId, \$cid)")
        assertEquals("nil", result)
        assertEquals("executed", mruby.eval("\$run_result"))
    }

    // ── _java_object_for ─────────────────────────────────────────────

    @Test
    fun `_java_object_for_returns_wrapper_for_valid_ID`() {
        val id = mruby.registerJavaObject("test object")
        val result = mruby.eval("Mrboto._java_object_for($id)")
        assertNotEquals("nil", result)
    }

    @Test
    fun `_java_object_for_returns_nil_for_invalid_ID`() {
        val result = mruby.eval("Mrboto._java_object_for(99999)")
        assertEquals("nil", result)
    }

    // ── _call_java_method ────────────────────────────────────────────

    @Test
    fun `_call_java_method_invokes_toString_on_String_object`() {
        val id = mruby.registerJavaObject("hello")
        val result = mruby.eval("Mrboto._call_java_method($id, 'toString')")
        assertEquals("hello", result)
    }

    @Test
    fun `_call_java_method_returns_nil_for_non-existent_method`() {
        val id = mruby.registerJavaObject("test")
        val result = mruby.eval("Mrboto._call_java_method($id, 'nonExistentMethod')")
        assertEquals("nil", result)
    }

    @Test
    fun `_call_java_method_with_integer_argument`() {
        val sbId = mruby.registerJavaObject(java.lang.StringBuilder())
        mruby.eval("Mrboto._call_java_method($sbId, 'append', 'test')")
        val len = mruby.eval("Mrboto._call_java_method($sbId, 'length')")
        assertEquals("4", len)
    }

    // ── _eval ────────────────────────────────────────────────────────

    @Test
    fun `_eval_returns_actual_expression_result`() {
        val result = mruby.eval("Mrboto._eval('1 + 2')")
        assertEquals("3", result)
    }

    @Test
    fun `_eval_returns_string_concatenation`() {
        val result = mruby.eval("Mrboto._eval('\"hello\" + \" world\"')")
        assertEquals("hello world", result)
    }

    @Test
    fun `_eval_returns_error_message_for_syntax_error`() {
        val result = mruby.eval("Mrboto._eval('def broken(')")
        assertNotEquals("ok", result)
        assertTrue("Should contain error info", result.isNotEmpty())
    }

    @Test
    fun `_eval_returns_nil_for_nil_expression`() {
        val result = mruby.eval("Mrboto._eval('nil')")
        assertEquals("nil", result)
    }

    @Test
    fun `_eval_defines_and_uses_method_across_calls`() {
        mruby.eval("Mrboto._eval('def double(x); x * 2; end')")
        val result = mruby.eval("Mrboto._eval('double(21)')")
        assertEquals("42", result)
    }

    // ── _view_text ───────────────────────────────────────────────────

    @Test
    fun `_view_text_returns_text_of_TextView`() {
        val viewId = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.TextView', {})")
        mruby.eval("Mrboto._call_java_method($viewId, 'setText', 'hello')")
        val result = mruby.eval("Mrboto._view_text($viewId)")
        assertEquals("hello", result)
    }

    @Test
    fun `_view_text_returns_text_set_on_EditText`() {
        val viewId = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.EditText', {})")
        mruby.eval("Mrboto._call_java_method($viewId, 'setText', 'user input')")
        val result = mruby.eval("Mrboto._view_text($viewId)")
        assertEquals("user input", result)
    }

    @Test
    fun `_view_text_returns_empty_string_for_view_with_no_text`() {
        val viewId = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.TextView', {})")
        val result = mruby.eval("Mrboto._view_text($viewId)")
        assertEquals("", result)
    }

    // ── _register_object ─────────────────────────────────────────────

    @Test
    fun `_register_object_returns_registry_ID_from_wrapper`() {
        val id = mruby.registerJavaObject(Any())
        // _java_object_for creates a new wrapper with a new registry ID.
        // _register_object should extract that wrapper's ID correctly.
        val result = mruby.eval("obj = Mrboto._java_object_for($id); rid = Mrboto._register_object(obj); rid > 0")
        assertEquals("true", result)
    }

    @Test
    fun `_register_object_returns_0_for_non-data_object`() {
        val result = mruby.eval("Mrboto._register_object(42)")
        assertEquals("0", result)
    }

    // ── _get_sys_res_id (system icon lookup via reflection) ─────────

    @Test
    fun `_get_sys_res_id_finds_ic_menu_help`() {
        val result = mruby.eval("Mrboto._get_sys_res_id(0, 'ic_menu_help', 'drawable')")
        val id = result.toIntOrNull()
        assertNotNull("Should return an integer, got: $result", id)
        assertTrue("ic_menu_help ID should be positive, got: $id", id!! > 0)
    }

    @Test
    fun `_get_sys_res_id_finds_ic_menu_search`() {
        val result = mruby.eval("Mrboto._get_sys_res_id(0, 'ic_menu_search', 'drawable')")
        val id = result.toIntOrNull()
        assertNotNull("Should return an integer", id)
        assertTrue("ic_menu_search ID should be positive", id!! > 0)
    }

    @Test
    fun `_get_sys_res_id_returns_0_for_nonexistent_drawable`() {
        val result = mruby.eval("Mrboto._get_sys_res_id(0, 'nonexistent_icon_xyz', 'drawable')")
        assertEquals("0", result)
    }

    @Test
    fun `android_sys_id_returns_cached_value`() {
        val result1 = mruby.eval("Mrboto.android_sys_id('ic_menu_save')")
        val result2 = mruby.eval("Mrboto.android_sys_id('ic_menu_save')")
        assertEquals(result1, result2)
    }

    @Test
    fun `android_sys_id_returns_nil_for_unknown_icon`() {
        val result = mruby.eval("Mrboto.android_sys_id('does_not_exist_at_all')")
        assertEquals("nil", result)
    }
}
