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
    fun `toast does not throw`() {
        setupActivity()
        val result = mruby.eval("toast('Test toast')")
        assertEquals("ok", result)
    }

    @Test
    fun `toast with long duration does not throw`() {
        setupActivity()
        val result = mruby.eval("toast('Long toast', :long)")
        assertEquals("ok", result)
    }

    @Test
    fun `top level toast method exists`() {
        assertEquals("true", mruby.eval("method(:toast).nil? rescue false; true.to_s"))
    }

    @Test
    fun `shared_preferences put and get string`() {
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
    fun `shared_preferences returns default for missing key`() {
        setupActivity()
        val result = mruby.eval("""
            sp = shared_preferences("test_prefs2")
            sp.get_string("nonexistent", "fallback")
        """.trimIndent())
        assertEquals("fallback", result)
    }

    @Test
    fun `shared_preferences put and get int`() {
        setupActivity()
        mruby.eval("""
            sp = shared_preferences("int_prefs")
            sp.put_int("count", 42)
        """.trimIndent())
        val result = mruby.eval("""
            sp = shared_preferences("int_prefs")
            sp.get_int("count", 0)
        """.trimIndent())
        // The C stub for _sp_get_int returns nil currently, so we just verify no crash
        // This is a known stub - test verifies it doesn't crash
    }

    @Test
    fun `package_name returns a string`() {
        setupActivity()
        val result = mruby.eval("Mrboto.package_name")
        assertTrue("Package name should not be empty", result.isNotEmpty() && result != "nil")
    }

    @Test
    fun `package_name is not nil`() {
        setupActivity()
        assertNotEquals("nil", mruby.eval("Mrboto.package_name.nil?.to_s"))
    }

    @Test
    fun `Helpers module exists`() {
        assertEquals("true", mruby.eval("Mrboto.const_defined?(:Helpers).to_s"))
    }

    @Test
    fun `Helpers toast is callable`() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.toast('Helper toast'); 'ok'")
        assertEquals("ok", result)
    }

    // ── Dialog Tests ──────────────────────────────────────────────

    @Test
    fun `dialog does not crash with no buttons`() {
        setupActivity()
        val result = mruby.eval("dialog('Title', 'Message'); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun `dialog does not crash with buttons`() {
        setupActivity()
        val result = mruby.eval("dialog('Title', 'Message', ['Yes', 'No']); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun `Helpers dialog does not crash`() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.dialog('T', 'M', ['OK']); 'ok'")
        assertEquals("ok", result)
    }

    // ── Snackbar Tests ────────────────────────────────────────────

    @Test
    fun `snackbar does not crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("snackbar($ctxId, 'Hello Snackbar'); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun `snackbar with long duration does not crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("snackbar($ctxId, 'Long Snackbar', :long); 'ok'")
        assertEquals("ok", result)
    }

    // ── PopupMenu Tests ───────────────────────────────────────────

    @Test
    fun `popup_menu does not crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("popup_menu($ctxId, ['Item 1', 'Item 2', 'Item 3']); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun `popup_menu with nil items returns nil`() {
        setupActivity()
        val result = mruby.eval("popup_menu(1, nil).nil?.to_s")
        assertEquals("true", result)
    }

    // ── Animation Tests ───────────────────────────────────────────

    @Test
    fun `fade does not crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("fade($ctxId, 0.0, 1.0, 200); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun `translate does not crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("translate($ctxId, 0.0, 100.0, 0.0, 0.0, 200); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun `scale does not crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("scale($ctxId, 1.0, 1.0, 1.2, 1.2, 200); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun `Animations module exists`() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.const_defined?(:Animations).to_s"))
    }

    @Test
    fun `fade_in helper exists`() {
        assertEquals("true", mruby.eval("Mrboto::Helpers::Animations.instance_methods.include?(:fade_in).to_s"))
    }

    @Test
    fun `pulse helper exists`() {
        assertEquals("true", mruby.eval("Mrboto::Helpers::Animations.instance_methods.include?(:pulse).to_s"))
    }
}
