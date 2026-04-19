package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QRCodeTest {
    @get:Rule val mrbotoRule = MrbotoTestRule()
    private val mruby get() = mrbotoRule.mruby

    private fun setupActivity() {
        val act = mrbotoRule.createTestActivity()
        val actId = mruby.registerJavaObject(act)
        mruby.eval("Mrboto.current_activity_id = $actId")
        mruby.eval("""
            class TestActivity < Mrboto::Activity; def on_create(bundle); super; end; end
            Mrboto.current_activity = TestActivity.new(Mrboto.current_activity_id)
            Mrboto.current_activity.on_create(nil)
        """.trimIndent())
    }

    @Test fun qrcode_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:scan_qr_code).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:generate_qr_code).to_s"))
    }

    @Test fun scan_qr_code_nonexistent_file() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.scan_qr_code('/nonexistent.png').to_s")
        assertNotNull(result)
        // Should return empty JSON array
        assertEquals("[]", result)
    }

    @Test fun generate_qr_code_returns_boolean() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.generate_qr_code('test', 'test_qr.png').to_s")
        // Should return "true" or "false" (as string)
        assertTrue(result == "true" || result == "false")
    }

    @Test fun generate_qr_code_default_size() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.generate_qr_code('test', 'test_qr.png', size: 300).to_s")
        assertTrue(result == "true" || result == "false")
    }

    @Test fun qrcode_top_level_methods_exist() {
        assertEquals("true", mruby.eval("respond_to?(:scan_qr_code).to_s"))
        assertEquals("true", mruby.eval("respond_to?(:generate_qr_code).to_s"))
    }

    @Test fun qrcode_scan_returns_json_format() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.scan_qr_code('/nonexistent.png').to_s")
        // Should be valid JSON array format
        assertTrue(result.startsWith("[") || result == "")
    }

    @Test fun qrcode_kotlin_methods_accessible() {
        val act = mrbotoRule.createTestActivity()
        val result = act.scanQRCode("/nonexistent.png")
        assertNotNull(result)
        // Should return empty JSON array for nonexistent file
        assertEquals("[]", result)
    }

    @Test fun qrcode_generate_kotlin_method_accessible() {
        val act = mrbotoRule.createTestActivity()
        val result = act.generateQRCode("test content", "test_kotlin_qr.png", 100)
        // Should return boolean
        assertNotNull(result)
    }
}
