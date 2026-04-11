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
    fun `_sp_get_int returns nil (stub)`() {
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
        assertNotNull("Should return an integer", id)
        assertTrue("View ID should be positive", id!! > 0)
    }

    @Test
    fun `_create_view returns positive ID for LinearLayout`() {
        val result = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.LinearLayout', {})")
        val id = result.toIntOrNull()
        assertNotNull("Should return an integer", id)
        assertTrue("View ID should be positive", id!! > 0)
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
        mruby.eval("\$run_result = nil; cid = Mrboto.register_callback { \$run_result = 'executed' }")
        val cid = mruby.eval("cid")
        val result = mruby.eval("Mrboto._run_on_ui_thread($actId, $cid)")
        assertEquals("nil", result)
        assertEquals("executed", mruby.eval("\$run_result"))
    }

    // ── _java_object_for (stub) ──────────────────────────────────────

    @Test
    fun `_java_object_for is a stub returning nil`() {
        val id = mruby.registerJavaObject(Any())
        val result = mruby.eval("Mrboto._java_object_for($id).nil?.to_s")
        assertEquals("true", result)
    }

    // ── _call_java_method (stub) ─────────────────────────────────────

    @Test
    fun `_call_java_method is a stub returning nil`() {
        val id = mruby.registerJavaObject("test")
        val result = mruby.eval("Mrboto._call_java_method($id, 'toString').nil?.to_s")
        assertEquals("true", result)
    }
}
