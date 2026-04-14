package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for WebView widget: class hierarchy, method existence,
 * and functional tests.
 */
class WebViewTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    private fun setupActivity() {
        val ctxId = mruby.registerJavaObject(mrbotoRule.context)
        val actId = mruby.registerJavaObject(mrbotoRule.context)
        mruby.eval("Mrboto.current_activity_id = $actId")
        mruby.eval("Mrboto._test_ctx_id = $ctxId")
        mruby.eval("""
            class TestActivity < Mrboto::Activity
              def on_create(bundle)
                super
              end
            end
            act = TestActivity.new(Mrboto.current_activity_id)
            Mrboto.current_activity = act
            Mrboto.current_activity.on_create(nil)
        """.trimIndent())
    }

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

    // ── Functional Tests ─────────────────────────────────────────

    @Test
    fun `WebView_creation_does_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            wv_id = Mrboto._create_view($ctxId, 'android.webkit.WebView', {})
            v = Mrboto::WebView.from_registry(wv_id)
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `WebView_load_data_does_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            wv_id = Mrboto._create_view($ctxId, 'android.webkit.WebView', {})
            v = Mrboto::WebView.from_registry(wv_id)
            v.load_data('<html><body>Hello</body></html>')
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `WebView_javascript_enabled_setter_does_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            wv_id = Mrboto._create_view($ctxId, 'android.webkit.WebView', {})
            v = Mrboto::WebView.from_registry(wv_id)
            v.javascript_enabled = true
            v.javascript_enabled = false
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `WebView_dom_storage_enabled_setter_does_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            wv_id = Mrboto._create_view($ctxId, 'android.webkit.WebView', {})
            v = Mrboto::WebView.from_registry(wv_id)
            v.dom_storage_enabled = true
            v.dom_storage_enabled = false
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `WebView_navigation_methods_do_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            wv_id = Mrboto._create_view($ctxId, 'android.webkit.WebView', {})
            v = Mrboto::WebView.from_registry(wv_id)
            v.reload
            v.stop_loading
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `WebView_can_go_back_returns_boolean`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            wv_id = Mrboto._create_view($ctxId, 'android.webkit.WebView', {})
            v = Mrboto::WebView.from_registry(wv_id)
            r = v.can_go_back
            r.class.to_s
        """.trimIndent())
        assertTrue("Expected TrueClass or FalseClass, got: $result",
            result == "TrueClass" || result == "FalseClass")
    }

    @Test
    fun `WebView_can_go_forward_returns_boolean`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            wv_id = Mrboto._create_view($ctxId, 'android.webkit.WebView', {})
            v = Mrboto::WebView.from_registry(wv_id)
            r = v.can_go_forward
            r.class.to_s
        """.trimIndent())
        assertTrue("Expected TrueClass or FalseClass, got: $result",
            result == "TrueClass" || result == "FalseClass")
    }
}
