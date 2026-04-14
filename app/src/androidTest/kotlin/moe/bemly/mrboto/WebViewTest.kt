package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for WebView widget: class hierarchy and method existence.
 *
 * WebView functional tests are not possible in instrumented tests because
 * android.webkit.WebView requires a Looper thread to initialize (its
 * constructor calls WebViewFactory.getProvider()), but AndroidJUnitRunner
 * runs on a non-Looper thread. Similar to PopupMenu.show() — see CLAUDE.md.
 */
class WebViewTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    // ── Class Hierarchy ──────────────────────────────────────────

    @Test
    fun `WebView_is_View_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::WebView < Mrboto::View).to_s"))
    }

    // ── Method Existence ─────────────────────────────────────────

    @Test
    fun `WebView_has_load_url_method`() {
        assertEquals("true", mruby.eval("Mrboto::WebView.instance_methods.include?(:load_url).to_s"))
    }

    @Test
    fun `WebView_has_load_data_method`() {
        assertEquals("true", mruby.eval("Mrboto::WebView.instance_methods.include?(:load_data).to_s"))
    }

    @Test
    fun `WebView_has_load_data_with_base_url_method`() {
        assertEquals("true", mruby.eval("Mrboto::WebView.instance_methods.include?(:load_data_with_base_url).to_s"))
    }

    @Test
    fun `WebView_has_javascript_enabled_setter`() {
        assertEquals("true", mruby.eval("Mrboto::WebView.instance_methods.include?(:javascript_enabled=).to_s"))
    }

    @Test
    fun `WebView_has_dom_storage_enabled_setter`() {
        assertEquals("true", mruby.eval("Mrboto::WebView.instance_methods.include?(:dom_storage_enabled=).to_s"))
    }

    @Test
    fun `WebView_has_go_back_method`() {
        assertEquals("true", mruby.eval("Mrboto::WebView.instance_methods.include?(:go_back).to_s"))
    }

    @Test
    fun `WebView_has_go_forward_method`() {
        assertEquals("true", mruby.eval("Mrboto::WebView.instance_methods.include?(:go_forward).to_s"))
    }

    @Test
    fun `WebView_has_reload_method`() {
        assertEquals("true", mruby.eval("Mrboto::WebView.instance_methods.include?(:reload).to_s"))
    }

    @Test
    fun `WebView_has_stop_loading_method`() {
        assertEquals("true", mruby.eval("Mrboto::WebView.instance_methods.include?(:stop_loading).to_s"))
    }

    @Test
    fun `WebView_has_can_go_back_method`() {
        assertEquals("true", mruby.eval("Mrboto::WebView.instance_methods.include?(:can_go_back).to_s"))
    }

    @Test
    fun `WebView_has_can_go_forward_method`() {
        assertEquals("true", mruby.eval("Mrboto::WebView.instance_methods.include?(:can_go_forward).to_s"))
    }
}
