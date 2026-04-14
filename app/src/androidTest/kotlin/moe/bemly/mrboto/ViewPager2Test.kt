package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for ViewPager2 widget: class hierarchy, method existence,
 * and functional tests including adapter setup.
 */
class ViewPager2Test {

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
    fun `ViewPager2_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::ViewPager2 < Mrboto::ViewGroup).to_s"))
    }

    // ── Method Existence ─────────────────────────────────────────

    @Test
    fun `ViewPager2_has_current_item_setter`() {
        assertEquals("true", mruby.eval("Mrboto::ViewPager2.instance_methods.include?(:current_item=).to_s"))
    }

    @Test
    fun `ViewPager2_has_offscreen_page_limit_setter`() {
        assertEquals("true", mruby.eval("Mrboto::ViewPager2.instance_methods.include?(:offscreen_page_limit=).to_s"))
    }

    @Test
    fun `ViewPager2_has_user_input_enabled_setter`() {
        assertEquals("true", mruby.eval("Mrboto::ViewPager2.instance_methods.include?(:user_input_enabled=).to_s"))
    }

    @Test
    fun `ViewPager2_has_orientation_setter`() {
        assertEquals("true", mruby.eval("Mrboto::ViewPager2.instance_methods.include?(:orientation=).to_s"))
    }

    @Test
    fun `ViewPager2_has_set_adapter_method`() {
        assertEquals("true", mruby.eval("Mrboto::ViewPager2.instance_methods.include?(:set_adapter).to_s"))
    }

    // ── Functional Tests ─────────────────────────────────────────

    @Test
    fun `ViewPager2_creation_does_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            id = Mrboto._create_view($ctxId, 'androidx.viewpager2.widget.ViewPager2', {})
            v = Mrboto::ViewPager2.from_registry(id)
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `ViewPager2_current_item_setter_does_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            id = Mrboto._create_view($ctxId, 'androidx.viewpager2.widget.ViewPager2', {})
            v = Mrboto::ViewPager2.from_registry(id)
            v.current_item = 0
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `ViewPager2_offscreen_page_limit_setter_does_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            id = Mrboto._create_view($ctxId, 'androidx.viewpager2.widget.ViewPager2', {})
            v = Mrboto::ViewPager2.from_registry(id)
            v.offscreen_page_limit = 1
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `ViewPager2_user_input_enabled_setter_does_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            id = Mrboto._create_view($ctxId, 'androidx.viewpager2.widget.ViewPager2', {})
            v = Mrboto::ViewPager2.from_registry(id)
            v.user_input_enabled = false
            v.user_input_enabled = true
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `ViewPager2_orientation_setter_does_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            id = Mrboto._create_view($ctxId, 'androidx.viewpager2.widget.ViewPager2', {})
            v = Mrboto::ViewPager2.from_registry(id)
            v.orientation = :horizontal
            v.orientation = :vertical
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `ViewPager2_set_adapter_does_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            id1 = Mrboto._create_view($ctxId, 'android.widget.TextView', {})
            id2 = Mrboto._create_view($ctxId, 'android.widget.TextView', {})
            vp_id = Mrboto._create_view($ctxId, 'androidx.viewpager2.widget.ViewPager2', {})
            v = Mrboto::ViewPager2.from_registry(vp_id)
            v.set_adapter([id1, id2])
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }
}
