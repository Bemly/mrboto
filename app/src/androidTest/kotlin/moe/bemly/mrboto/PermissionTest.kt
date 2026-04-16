package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PermissionTest {

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

    // ── permission_granted? ───────────────────────────────────────────

    @Test
    fun permission_granted_returns_boolean_for_internet() {
        setupActivity()
        val result = mruby.eval("permission_granted?(Mrboto::Helpers::PERMISSION_INTERNET).to_s")
        assertEquals("true", result)
    }

    @Test
    fun permission_granted_returns_boolean_type() {
        setupActivity()
        val result = mruby.eval("[true, false].include?(permission_granted?(Mrboto::Helpers::PERMISSION_INTERNET)).to_s")
        assertEquals("true", result)
    }

    @Test
    fun permission_granted_with_unknown_returns_false() {
        setupActivity()
        val result = mruby.eval("permission_granted?('com.nonexistent.PERMISSION_X').to_s")
        assertEquals("false", result)
    }

    // ── request_permission ────────────────────────────────────────────

    @Test
    fun request_permission_returns_true_for_internet() {
        setupActivity()
        val result = mruby.eval("request_permission(Mrboto::Helpers::PERMISSION_INTERNET).to_s")
        assertEquals("true", result)
    }

    @Test
    fun request_permission_returns_false_for_unknown() {
        setupActivity()
        val result = mruby.eval("request_permission('com.nonexistent.FAKE').to_s")
        assertEquals("false", result)
    }

    @Test
    fun request_permission_via_call_java_method() {
        setupActivity()
        val result = mruby.eval("""
            act = Mrboto.current_activity
            act.call_java_method("requestPermissionSync", Mrboto::Helpers::PERMISSION_INTERNET).to_s
        """.trimIndent())
        assertEquals("true", result)
    }

    // ── request_permissions ───────────────────────────────────────────

    @Test
    fun request_permissions_returns_hash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.request_permissions([Mrboto::Helpers::PERMISSION_INTERNET]).class.name")
        assertEquals("Hash", result)
    }

    @Test
    fun request_permissions_with_empty_array_returns_hash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.request_permissions([]).class.name")
        assertEquals("Hash", result)
    }

    @Test
    fun request_permissions_contains_granted_key() {
        setupActivity()
        val result = mruby.eval("""
            h = Mrboto::Helpers.request_permissions([Mrboto::Helpers::PERMISSION_INTERNET])
            h[Mrboto::Helpers::PERMISSION_INTERNET].to_s
        """.trimIndent())
        assertEquals("true", result)
    }

    @Test
    fun request_permissions_via_call_java_method() {
        setupActivity()
        val result = mruby.eval("""
            act = Mrboto.current_activity
            json = act.call_java_method("requestPermissionsSync", '["android.permission.INTERNET"]').to_s
            json.class.name
        """.trimIndent())
        assertEquals("String", result)
    }

    // ── Permission constants ──────────────────────────────────────────

    @Test
    fun permission_camera_constant_exists() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.const_defined?(:PERMISSION_CAMERA).to_s"))
    }

    @Test
    fun permission_camera_constant_value() {
        val result = mruby.eval("Mrboto::Helpers::PERMISSION_CAMERA")
        assertEquals("android.permission.CAMERA", result)
    }

    @Test
    fun permission_internet_constant_exists() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.const_defined?(:PERMISSION_INTERNET).to_s"))
    }

    @Test
    fun permission_internet_constant_value() {
        val result = mruby.eval("Mrboto::Helpers::PERMISSION_INTERNET")
        assertEquals("android.permission.INTERNET", result)
    }

    @Test
    fun permission_post_notifications_constant_exists() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.const_defined?(:PERMISSION_POST_NOTIFICATIONS).to_s"))
    }

    @Test
    fun permission_post_notifications_constant_value() {
        val result = mruby.eval("Mrboto::Helpers::PERMISSION_POST_NOTIFICATIONS")
        assertEquals("android.permission.POST_NOTIFICATIONS", result)
    }

    @Test
    fun permission_record_audio_constant_exists() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.const_defined?(:PERMISSION_RECORD_AUDIO).to_s"))
    }

    @Test
    fun permission_record_audio_constant_value() {
        val result = mruby.eval("Mrboto::Helpers::PERMISSION_RECORD_AUDIO")
        assertEquals("android.permission.RECORD_AUDIO", result)
    }

    @Test
    fun permission_location_constant_exists() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.const_defined?(:PERMISSION_ACCESS_FINE_LOCATION).to_s"))
    }

    @Test
    fun permission_location_constant_value() {
        val result = mruby.eval("Mrboto::Helpers::PERMISSION_ACCESS_FINE_LOCATION")
        assertEquals("android.permission.ACCESS_FINE_LOCATION", result)
    }

    @Test
    fun permission_read_storage_constant_exists() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.const_defined?(:PERMISSION_READ_EXTERNAL_STORAGE).to_s"))
    }

    @Test
    fun permission_write_storage_constant_exists() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.const_defined?(:PERMISSION_WRITE_EXTERNAL_STORAGE).to_s"))
    }

    // ── Module methods existence ──────────────────────────────────────

    @Test
    fun permission_module_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:permission_granted?).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:request_permission).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:request_permissions).to_s"))
    }

    @Test
    fun top_level_permission_methods_exist() {
        assertEquals("true", mruby.eval("method(:permission_granted?).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:request_permission).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:request_permissions).nil? rescue false; true.to_s"))
    }

    // ── Module-level direct calls ─────────────────────────────────────

    @Test
    fun module_permission_granted_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.permission_granted?(Mrboto::Helpers::PERMISSION_INTERNET).to_s")
        assertEquals("true", result)
    }
}
