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

    // ── Creation via _create_view ───────────────────────────────────

    @Test
    fun `create LiquidGlassView via _create_view`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val viewId = mruby.eval(
            "Mrboto._create_view($ctxId, 'moe.bemly.mrboto.LiquidGlassView', {})")
        assertNotNull(viewId)
        assertTrue("Should have positive registry ID",
            viewId.toIntOrNull() != null && viewId.toInt() > 0)
    }

    @Test
    fun `create LiquidGlassView and wrap with from_registry`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            view_id = Mrboto._create_view($ctxId, 'moe.bemly.mrboto.LiquidGlassView', {})
            v = Mrboto::LiquidGlassView.from_registry(view_id)
            v.class.to_s
        """.trimIndent())
        assertEquals("Mrboto::LiquidGlassView", result)
    }

    // ── Setter methods ──────────────────────────────────────────────

    @Test
    fun `blur_radius setter exists`() {
        assertEquals("true", mruby.eval(
            "Mrboto::LiquidGlassView.instance_methods.include?(:blur_radius=).to_s"))
    }

    @Test
    fun `vibrancy setter exists`() {
        assertEquals("true", mruby.eval(
            "Mrboto::LiquidGlassView.instance_methods.include?(:vibrancy=).to_s"))
    }

    @Test
    fun `lens method exists`() {
        assertEquals("true", mruby.eval(
            "Mrboto::LiquidGlassView.instance_methods.include?(:lens).to_s"))
    }

    @Test
    fun `opacity setter exists`() {
        assertEquals("true", mruby.eval(
            "Mrboto::LiquidGlassView.instance_methods.include?(:opacity=).to_s"))
    }

    @Test
    fun `shape_type setter exists`() {
        assertEquals("true", mruby.eval(
            "Mrboto::LiquidGlassView.instance_methods.include?(:shape_type=).to_s"))
    }

    @Test
    fun `corner_radius setter exists`() {
        assertEquals("true", mruby.eval(
            "Mrboto::LiquidGlassView.instance_methods.include?(:corner_radius=).to_s"))
    }

    // ── Setter calls (no crash) ────────────────────────────────────

    @Test
    fun `set blur_radius does not crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        assertEquals("ok", mruby.eval("""
            v = Mrboto::LiquidGlassView.from_registry(Mrboto._create_view($ctxId, 'moe.bemly.mrboto.LiquidGlassView', {}))
            v.blur_radius = 15.0
            'ok'
        """.trimIndent()))
    }

    @Test
    fun `set vibrancy does not crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        assertEquals("ok", mruby.eval("""
            v = Mrboto::LiquidGlassView.from_registry(Mrboto._create_view($ctxId, 'moe.bemly.mrboto.LiquidGlassView', {}))
            v.vibrancy = true
            'ok'
        """.trimIndent()))
    }

    @Test
    fun `set lens does not crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        assertEquals("ok", mruby.eval("""
            v = Mrboto::LiquidGlassView.from_registry(Mrboto._create_view($ctxId, 'moe.bemly.mrboto.LiquidGlassView', {}))
            v.lens(16.0, 32.0)
            'ok'
        """.trimIndent()))
    }

    @Test
    fun `set opacity does not crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        assertEquals("ok", mruby.eval("""
            v = Mrboto::LiquidGlassView.from_registry(Mrboto._create_view($ctxId, 'moe.bemly.mrboto.LiquidGlassView', {}))
            v.opacity = 0.5
            'ok'
        """.trimIndent()))
    }

    @Test
    fun `set shape_type does not crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        assertEquals("ok", mruby.eval("""
            v = Mrboto::LiquidGlassView.from_registry(Mrboto._create_view($ctxId, 'moe.bemly.mrboto.LiquidGlassView', {}))
            v.shape_type = 'circle'
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
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        assertEquals("ok", mruby.eval("""
            v = Mrboto::TextView.from_registry(Mrboto._create_view($ctxId, 'android.widget.TextView', {}))
            v.apply_liquid_glass_effect(blur: 10.0, style: :blur)
            'ok'
        """.trimIndent()))
    }

    @Test
    fun `apply blur_vibrancy effect does not crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        assertEquals("ok", mruby.eval("""
            v = Mrboto::TextView.from_registry(Mrboto._create_view($ctxId, 'android.widget.TextView', {}))
            v.apply_liquid_glass_effect(blur: 8.0, style: :blur_vibrancy)
            'ok'
        """.trimIndent()))
    }

    @Test
    fun `remove effect does not crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        assertEquals("ok", mruby.eval("""
            v = Mrboto::TextView.from_registry(Mrboto._create_view($ctxId, 'android.widget.TextView', {}))
            v.apply_liquid_glass_effect(blur: 10.0)
            v.remove_liquid_glass_effect
            'ok'
        """.trimIndent()))
    }

    @Test
    fun `apply effect with default parameters`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        assertEquals("ok", mruby.eval("""
            v = Mrboto::TextView.from_registry(Mrboto._create_view($ctxId, 'android.widget.TextView', {}))
            v.apply_liquid_glass_effect
            'ok'
        """.trimIndent()))
    }
}
