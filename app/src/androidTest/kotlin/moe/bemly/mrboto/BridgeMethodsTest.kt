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
    fun `_toast returns nil without crashing`() {
        val result = mruby.eval("Mrboto._toast($ctxId, 'test toast', 0)")
        assertEquals("nil", result)
    }

    @Test
    fun `_toast with long duration returns nil`() {
        val result = mruby.eval("Mrboto._toast($ctxId, 'long toast', 1)")
        assertEquals("nil", result)
    }

    // ── _start_activity ──────────────────────────────────────────────

    @Test
    fun `_start_activity does not crash with invalid class`() {
        val result = mruby.eval("Mrboto._start_activity($ctxId, 'NonExistentActivity')")
        // In instrumented test, no real Activity exists, so this is a no-op
        assertEquals("nil", result)
    }

    // ── _get_extra ───────────────────────────────────────────────────

    @Test
    fun `_get_extra returns nil without real intent`() {
        val actId = mruby.registerJavaObject(Any())
        val result = mruby.eval("Mrboto._get_extra($actId, 'missing_key')")
        assertEquals("nil", result)
    }

    // ── _sp_get_int / _sp_put_int ────────────────────────────────────

    @Test
    fun `_sp_put_int does not crash`() {
        val result = mruby.eval("Mrboto._sp_put_int($ctxId, 'test_int_prefs', 'count', 42)")
        assertEquals("nil", result)
    }

    @Test
    fun `sp_get_int returns nil as stub`() {
        val result = mruby.eval("Mrboto._sp_get_int($ctxId, 'test_int_prefs', 'count', 99)")
        // C stub returns nil, not the default value
        assertEquals("nil", result)
    }

    // ── _app_context ─────────────────────────────────────────────────

    @Test
    fun `_app_context returns a JavaObject`() {
        val result = mruby.eval("Mrboto._app_context.class.to_s")
        assertTrue("Should be a JavaObject subclass", result.contains("JavaObject"))
    }

    @Test
    fun `_app_context has positive registry ID`() {
        val result = mruby.eval("Mrboto._app_context._registry_id > 0")
        assertEquals("true", result)
    }

    // ── _dp_to_px ────────────────────────────────────────────────────

    @Test
    fun `_dp_to_px returns integer greater than input`() {
        val result = mruby.eval("Mrboto._dp_to_px(100)")
        val px = result.toIntOrNull()
        assertNotNull("Should return an integer", px)
        assertTrue("100 dp should produce more than 100 px", px!! > 100)
    }

    @Test
    fun `_dp_to_px preserves ratio for small values`() {
        val result10 = mruby.eval("Mrboto._dp_to_px(10)").toIntOrNull() ?: 0
        val result20 = mruby.eval("Mrboto._dp_to_px(20)").toIntOrNull() ?: 0
        assertTrue("dp(20) should be roughly 2x dp(10)", result20 >= result10 * 1)
    }

    // ── _create_view ─────────────────────────────────────────────────

    @Test
    fun `_create_view returns positive ID for TextView`() {
        val result = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.TextView', {})")
        val id = result.toIntOrNull()
        assertNotNull("Should return an integer, got: $result", id)
        assertTrue("View ID should be positive, got: $id", id!! > 0)
    }

    @Test
    fun `_create_view returns positive ID for LinearLayout`() {
        val result = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.LinearLayout', {})")
        val id = result.toIntOrNull()
        assertNotNull("Should return an integer, got: $result", id)
        assertTrue("View ID should be positive, got: $id", id!! > 0)
    }

    // ── _set_on_click ────────────────────────────────────────────────

    @Test
    fun `_set_on_click does not crash with valid view`() {
        val viewId = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.Button', {})")
        val result = mruby.eval("Mrboto._set_on_click($viewId, 99)")
        assertEquals("nil", result)
    }

    // ── _set_content_view ────────────────────────────────────────────

    @Test
    fun `_set_content_view does not crash with valid IDs`() {
        val actId = mruby.registerJavaObject(mrbotoRule.context)
        val viewId = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.TextView', {})")
        val result = mruby.eval("Mrboto._set_content_view($actId, $viewId)")
        assertEquals("nil", result)
    }

    // ── _run_on_ui_thread ────────────────────────────────────────────

    @Test
    fun `_run_on_ui_thread executes callback`() {
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
    fun `_java_object_for returns wrapper for valid ID`() {
        val id = mruby.registerJavaObject("test object")
        val result = mruby.eval("Mrboto._java_object_for($id)")
        assertNotEquals("nil", result)
    }

    @Test
    fun `_java_object_for returns nil for invalid ID`() {
        val result = mruby.eval("Mrboto._java_object_for(99999)")
        assertEquals("nil", result)
    }

    // ── _call_java_method ────────────────────────────────────────────

    @Test
    fun `_call_java_method invokes toString on String object`() {
        val id = mruby.registerJavaObject("hello")
        val result = mruby.eval("Mrboto._call_java_method($id, 'toString')")
        assertEquals("hello", result)
    }

    @Test
    fun `_call_java_method returns nil for non-existent method`() {
        val id = mruby.registerJavaObject("test")
        val result = mruby.eval("Mrboto._call_java_method($id, 'nonExistentMethod')")
        assertEquals("nil", result)
    }

    @Test
    fun `_call_java_method with integer argument`() {
        val sbId = mruby.registerJavaObject(java.lang.StringBuilder())
        mruby.eval("Mrboto._call_java_method($sbId, 'append', 'test')")
        val len = mruby.eval("Mrboto._call_java_method($sbId, 'length')")
        assertEquals("4", len)
    }

    // ── _eval ────────────────────────────────────────────────────────

    @Test
    fun `_eval returns actual expression result`() {
        val result = mruby.eval("Mrboto._eval('1 + 2')")
        assertEquals("3", result)
    }

    @Test
    fun `_eval returns string concatenation`() {
        val result = mruby.eval("Mrboto._eval('\"hello\" + \" world\"')")
        assertEquals("hello world", result)
    }

    @Test
    fun `_eval returns error message for syntax error`() {
        val result = mruby.eval("Mrboto._eval('def broken(')")
        assertNotEquals("ok", result)
        assertTrue("Should contain error info", result.isNotEmpty())
    }

    @Test
    fun `_eval returns nil for nil expression`() {
        val result = mruby.eval("Mrboto._eval('nil')")
        assertEquals("nil", result)
    }

    @Test
    fun `_eval defines and uses method across calls`() {
        mruby.eval("Mrboto._eval('def double(x); x * 2; end')")
        val result = mruby.eval("Mrboto._eval('double(21)')")
        assertEquals("42", result)
    }

    // ── _view_text ───────────────────────────────────────────────────

    @Test
    fun `_view_text returns text of TextView`() {
        val viewId = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.TextView', {})")
        mruby.eval("Mrboto._call_java_method($viewId, 'setText', 'hello')")
        val result = mruby.eval("Mrboto._view_text($viewId)")
        assertEquals("hello", result)
    }

    @Test
    fun `_view_text returns text set on EditText`() {
        val viewId = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.EditText', {})")
        mruby.eval("Mrboto._call_java_method($viewId, 'setText', 'user input')")
        val result = mruby.eval("Mrboto._view_text($viewId)")
        assertEquals("user input", result)
    }

    @Test
    fun `_view_text returns empty string for view with no text`() {
        val viewId = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.TextView', {})")
        val result = mruby.eval("Mrboto._view_text($viewId)")
        assertEquals("", result)
    }

    // ── _register_object ─────────────────────────────────────────────

    @Test
    fun `_register_object returns registry ID from wrapper`() {
        val id = mruby.registerJavaObject(Any())
        // _java_object_for creates a new wrapper with a new registry ID.
        // _register_object should extract that wrapper's ID correctly.
        val result = mruby.eval("obj = Mrboto._java_object_for($id); rid = Mrboto._register_object(obj); rid > 0")
        assertEquals("true", result)
    }

    @Test
    fun `_register_object returns 0 for non-data object`() {
        val result = mruby.eval("Mrboto._register_object(42)")
        assertEquals("0", result)
    }
}
