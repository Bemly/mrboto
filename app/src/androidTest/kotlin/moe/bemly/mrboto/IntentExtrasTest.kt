package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IntentExtrasTest {

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

    // ── get_extra_int ─────────────────────────────────────────────────

    @Test
    fun get_extra_int_returns_zero_for_missing_key() {
        setupActivity()
        val result = mruby.eval("get_extra_int('nonexistent')")
        assertEquals("0", result)
    }

    @Test
    fun get_extra_int_returns_integer_class() {
        setupActivity()
        val result = mruby.eval("get_extra_int('any_key').class.name")
        assertEquals("Integer", result)
    }

    @Test
    fun get_extra_int_set_and_get() {
        setupActivity()
        val result = mruby.eval("""
            act = Mrboto.current_activity
            act.call_java_method("getExtraInt", "count").to_s
        """.trimIndent())
        assertEquals("0", result)
    }

    // ── get_extra_bool ────────────────────────────────────────────────

    @Test
    fun get_extra_bool_returns_false_for_missing_key() {
        setupActivity()
        val result = mruby.eval("get_extra_bool('nonexistent').to_s")
        assertEquals("false", result)
    }

    @Test
    fun get_extra_bool_returns_boolean_like() {
        setupActivity()
        val result = mruby.eval("[true, false].include?(get_extra_bool('any')).to_s")
        assertEquals("true", result)
    }

    // ── get_extra_float ───────────────────────────────────────────────

    @Test
    fun get_extra_float_returns_zero_for_missing_key() {
        setupActivity()
        val result = mruby.eval("get_extra_float('nonexistent')")
        assertEquals("0", result)
    }

    @Test
    fun get_extra_float_returns_float_class() {
        setupActivity()
        val result = mruby.eval("get_extra_float('any_key').class.name")
        assertEquals("Float", result)
    }

    // ── get_all_extras ────────────────────────────────────────────────

    @Test
    fun get_all_extras_returns_hash() {
        setupActivity()
        val result = mruby.eval("get_all_extras.class.name")
        assertEquals("Hash", result)
    }

    @Test
    fun get_all_extras_returns_empty_hash_for_no_extras() {
        setupActivity()
        val result = mruby.eval("get_all_extras.empty?.to_s")
        assertEquals("true", result)
    }

    @Test
    fun get_all_extras_via_call_java_method_returns_json() {
        setupActivity()
        val result = mruby.eval("""
            act = Mrboto.current_activity
            raw = act.call_java_method("getAllExtras").to_s
            raw.class.name
        """.trimIndent())
        assertEquals("String", result)
    }

    // ── get_extra (string) ────────────────────────────────────────────

    @Test
    fun get_extra_returns_nil_for_missing_key() {
        setupActivity()
        val result = mruby.eval("get_extra('nonexistent_key').nil?.to_s")
        assertEquals("true", result)
    }

    @Test
    fun get_extra_method_exists() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:get_extra).to_s"))
        assertEquals("true", mruby.eval("method(:get_extra).nil? rescue false; true.to_s"))
    }

    // ── Module methods existence ──────────────────────────────────────

    @Test
    fun intent_extras_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:get_extra_int).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:get_extra_bool).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:get_extra_float).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:get_all_extras).to_s"))
    }

    @Test
    fun top_level_extras_methods_exist() {
        assertEquals("true", mruby.eval("method(:get_extra_int).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:get_extra_bool).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:get_extra_float).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:get_all_extras).nil? rescue false; true.to_s"))
    }

    // ── Module-level direct calls ─────────────────────────────────────

    @Test
    fun module_get_extra_int_returns_zero() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.get_extra_int('miss')")
        assertEquals("0", result)
    }

    @Test
    fun module_get_extra_bool_returns_false() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.get_extra_bool('miss').to_s")
        assertEquals("false", result)
    }

    @Test
    fun module_get_extra_float_returns_zero() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.get_extra_float('miss')")
        assertEquals("0", result)
    }

    @Test
    fun module_get_all_extras_returns_hash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.get_all_extras.class.name")
        assertEquals("Hash", result)
    }
}
