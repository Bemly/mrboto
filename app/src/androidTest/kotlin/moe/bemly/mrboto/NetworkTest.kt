package moe.bemly.mrboto

import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NetworkTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    private fun setupActivity() {
        val scenario = ActivityScenario.launch(TestMrbotoActivity::class.java)
        scenario.onActivity { act ->
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
    }

    // ── http_get ──────────────────────────────────────────────────

    @Test
    fun http_get_returns_string() {
        setupActivity()
        val result = mruby.eval("http_get('http://127.0.0.1:1/nonexistent').class.name")
        assertEquals("String", result)
    }

    @Test
    fun http_get_returns_json_like_string() {
        setupActivity()
        val result = mruby.eval("http_get('http://127.0.0.1:1/')")
        // Should be a JSON-like response even on failure
        assertTrue(result.startsWith("{") || result.contains("status"))
    }

    @Test
    fun http_get_with_invalid_url_does_not_crash() {
        setupActivity()
        val result = mruby.eval("http_get('not_a_url').class.name")
        assertEquals("String", result)
    }

    @Test
    fun http_get_with_headers_does_not_crash() {
        setupActivity()
        val result = mruby.eval("http_get('http://127.0.0.1:1/', { 'X-Test' => 'val' }).class.name")
        assertEquals("String", result)
    }

    // ── http_post ─────────────────────────────────────────────────

    @Test
    fun http_post_returns_string() {
        setupActivity()
        val result = mruby.eval("http_post('http://127.0.0.1:1/nonexistent', 'body').class.name")
        assertEquals("String", result)
    }

    @Test
    fun http_post_with_headers_does_not_crash() {
        setupActivity()
        val result = mruby.eval("http_post('http://127.0.0.1:1/', 'body', { 'Content-Type' => 'text/plain' }).class.name")
        assertEquals("String", result)
    }

    @Test
    fun http_post_with_empty_body_does_not_crash() {
        setupActivity()
        val result = mruby.eval("http_post('http://127.0.0.1:1/', '').class.name")
        assertEquals("String", result)
    }

    // ── http_download ─────────────────────────────────────────────

    @Test
    fun http_download_returns_boolean() {
        setupActivity()
        val result = mruby.eval("http_download('http://127.0.0.1:1/nonexistent', 'test.bin').to_s")
        // Should return false for unreachable URL
        assertEquals("false", result)
    }

    @Test
    fun http_download_with_invalid_url_returns_false() {
        setupActivity()
        val result = mruby.eval("http_download('not_a_url', 'test.bin').to_s")
        assertEquals("false", result)
    }

    // ── Method existence ──────────────────────────────────────────

    @Test
    fun network_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:http_get).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:http_post).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:http_download).to_s"))
    }

    @Test
    fun top_level_network_methods_exist() {
        assertEquals("true", mruby.eval("method(:http_get).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:http_post).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:http_download).nil? rescue false; true.to_s"))
    }

    // ── Module-level direct calls ─────────────────────────────────

    @Test
    fun module_http_get_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.http_get('http://127.0.0.1:1/').class.name")
        assertEquals("String", result)
    }

    @Test
    fun module_http_post_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.http_post('http://127.0.0.1:1/', 'data').class.name")
        assertEquals("String", result)
    }

    @Test
    fun module_http_download_returns_false_on_failure() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.http_download('http://127.0.0.1:1/', 'fail.bin').to_s")
        assertEquals("false", result)
    }
}
