package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for LiquidGlassView (ComposeView bridge) and
 * apply_liquid_glass_effect (RenderEffect API).
 */
class LiquidGlassTest {

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

    // ── JAVA_CLASS_MAP ──────────────────────────────────────────────

    @Test
    fun `liquid_glass_view in JAVA_CLASS_MAP`() {
        assertEquals("true", mruby.eval(
            "Mrboto::Widgets::JAVA_CLASS_MAP.key?(:liquid_glass_view).to_s"))
    }

    @Test
    fun `liquid_glass_view maps to correct class`() {
        assertEquals("moe.bemly.mrboto.LiquidGlassView", mruby.eval(
            "Mrboto::Widgets::JAVA_CLASS_MAP[:liquid_glass_view]"))
    }

    // ── Class hierarchy ─────────────────────────────────────────────

    @Test
    fun `LiquidGlassView class exists`() {
        assertEquals("true", mruby.eval("Mrboto.const_defined?(:LiquidGlassView).to_s"))
    }

    @Test
    fun `LiquidGlassView extends ViewGroup`() {
        assertEquals("true", mruby.eval(
            "(Mrboto::LiquidGlassView < Mrboto::ViewGroup).to_s"))
    }

    // ── DSL creation ────────────────────────────────────────────────

    @Test
    fun `create LiquidGlassView via DSL`() {
        setupActivity()
        val result = mruby.eval("""
            v = liquid_glass_view
            v.nil? ? 'nil' : v._registry_id.to_s
        """.trimIndent())
        assertNotNull("Should not be nil", result)
        assertTrue("Should have positive registry ID",
            result != "nil" && result.toIntOrNull() != null && result.toInt() > 0)
    }

    // ── Setter methods ──────────────────────────────────────────────

    @Test
    fun `blur_radius setter exists`() {
        setupActivity()
        assertEquals("true", mruby.eval("""
            v = liquid_glass_view
            v.respond_to?(:blur_radius=).to_s
        """.trimIndent()))
    }

    @Test
    fun `vibrancy setter exists`() {
        setupActivity()
        assertEquals("true", mruby.eval("""
            v = liquid_glass_view
            v.respond_to?(:vibrancy=).to_s
        """.trimIndent()))
    }

    @Test
    fun `lens method exists`() {
        setupActivity()
        assertEquals("true", mruby.eval("""
            v = liquid_glass_view
            v.respond_to?(:lens).to_s
        """.trimIndent()))
    }

    @Test
    fun `opacity setter exists`() {
        setupActivity()
        assertEquals("true", mruby.eval("""
            v = liquid_glass_view
            v.respond_to?(:opacity=).to_s
        """.trimIndent()))
    }

    @Test
    fun `shape_type setter exists`() {
        setupActivity()
        assertEquals("true", mruby.eval("""
            v = liquid_glass_view
            v.respond_to?(:shape_type=).to_s
        """.trimIndent()))
    }

    @Test
    fun `corner_radius setter exists`() {
        setupActivity()
        assertEquals("true", mruby.eval("""
            v = liquid_glass_view
            v.respond_to?(:corner_radius=).to_s
        """.trimIndent()))
    }

    // ── Setter calls (no crash) ────────────────────────────────────

    @Test
    fun `set blur_radius does not crash`() {
        setupActivity()
        assertEquals("ok", mruby.eval("""
            v = liquid_glass_view
            v.blur_radius = 15.0
            'ok'
        """.trimIndent()))
    }

    @Test
    fun `set vibrancy does not crash`() {
        setupActivity()
        assertEquals("ok", mruby.eval("""
            v = liquid_glass_view
            v.vibrancy = true
            'ok'
        """.trimIndent()))
    }

    @Test
    fun `set lens does not crash`() {
        setupActivity()
        assertEquals("ok", mruby.eval("""
            v = liquid_glass_view
            v.lens(16.0, 32.0)
            'ok'
        """.trimIndent()))
    }

    @Test
    fun `set opacity does not crash`() {
        setupActivity()
        assertEquals("ok", mruby.eval("""
            v = liquid_glass_view
            v.opacity = 0.5
            'ok'
        """.trimIndent()))
    }

    @Test
    fun `set shape_type does not crash`() {
        setupActivity()
        assertEquals("ok", mruby.eval("""
            v = liquid_glass_view
            v.shape_type = 'circle'
            'ok'
        """.trimIndent()))
    }

    // ── Child views ────────────────────────────────────────────────

    @Test
    fun `can add child views to LiquidGlassView`() {
        setupActivity()
        assertEquals("ok", mruby.eval("""
            v = liquid_glass_view do
              text_view(text: "Inside glass")
            end
            'ok'
        """.trimIndent()))
    }

    // ── RenderEffect API (apply_liquid_glass_effect) ───────────────

    @Test
    fun `apply_liquid_glass_effect method exists on View`() {
        assertEquals("false", mruby.eval(
            "Mrboto::View.instance_method(:apply_liquid_glass_effect).nil?.to_s"))
    }

    @Test
    fun `remove_liquid_glass_effect method exists on View`() {
        assertEquals("false", mruby.eval(
            "Mrboto::View.instance_method(:remove_liquid_glass_effect).nil?.to_s"))
    }

    @Test
    fun `apply blur effect does not crash`() {
        setupActivity()
        assertEquals("ok", mruby.eval("""
            v = text_view(text: "Blur me")
            v.apply_liquid_glass_effect(blur: 10.0, style: :blur)
            'ok'
        """.trimIndent()))
    }

    @Test
    fun `apply blur_vibrancy effect does not crash`() {
        setupActivity()
        assertEquals("ok", mruby.eval("""
            v = text_view(text: "Vibrant")
            v.apply_liquid_glass_effect(blur: 8.0, style: :blur_vibrancy)
            'ok'
        """.trimIndent()))
    }

    @Test
    fun `remove effect does not crash`() {
        setupActivity()
        assertEquals("ok", mruby.eval("""
            v = text_view(text: "Clear me")
            v.apply_liquid_glass_effect(blur: 10.0)
            v.remove_liquid_glass_effect
            'ok'
        """.trimIndent()))
    }

    @Test
    fun `apply effect with default parameters`() {
        setupActivity()
        assertEquals("ok", mruby.eval("""
            v = text_view(text: "Default")
            v.apply_liquid_glass_effect
            'ok'
        """.trimIndent()))
    }
}
