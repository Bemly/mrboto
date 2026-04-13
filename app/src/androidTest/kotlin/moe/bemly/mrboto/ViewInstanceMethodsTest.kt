package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for View and Activity instance methods added in widgets.rb and activity.rb.
 * Tests that view.fade_in, view.animate_translate, activity.show_dialog, etc.
 * work without throwing exceptions.
 */
class ViewInstanceMethodsTest {

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

    // ── View Animation Methods ──────────────────────────────────────

    @Test
    fun `view_fade_in_does_not_throw`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("view = Mrboto::View.from_registry($viewId); view.fade_in(100)")
        assertEquals("ok", result)
    }

    @Test
    fun `view_fade_out_does_not_throw`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("view = Mrboto::View.from_registry($viewId); view.fade_out(100)")
        assertEquals("ok", result)
    }

    @Test
    fun `view_animate_translate_does_not_throw`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("""
            view = Mrboto::View.from_registry($viewId)
            view.animate_translate(0.0, 0.0, 100.0, 0.0, 100)
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `view_animate_scale_does_not_throw`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("""
            view = Mrboto::View.from_registry($viewId)
            view.animate_scale(1.0, 1.0, 1.5, 1.5, 100)
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `view_slide_in_bottom_does_not_throw`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("""
            view = Mrboto::View.from_registry($viewId)
            view.slide_in_bottom(100)
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `view_pulse_does_not_throw`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("""
            view = Mrboto::View.from_registry($viewId)
            view.pulse(1.3, 100)
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `view_clear_animation_does_not_throw`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("""
            view = Mrboto::View.from_registry($viewId)
            view.fade_in(50)
            view.clear_animation
        """.trimIndent())
        // clear_animation returns nil from call_java_method
        assertNull(result)
    }

    // ── View Info Methods ───────────────────────────────────────────

    @Test
    fun `view_width_returns_integer`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("""
            view = Mrboto::View.from_registry($viewId)
            view.width.class.to_s
        """.trimIndent())
        assertEquals("Integer", result)
    }

    @Test
    fun `view_height_returns_integer`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("""
            view = Mrboto::View.from_registry($viewId)
            view.height.class.to_s
        """.trimIndent())
        assertEquals("Integer", result)
    }

    @Test
    fun `view_visible_question_mark_returns_boolean`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("""
            view = Mrboto::View.from_registry($viewId)
            view.visible?.class.to_s
        """.trimIndent())
        assertEquals("TrueClass", result)
    }

    @Test
    fun `view_show_does_not_throw`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("""
            view = Mrboto::View.from_registry($viewId)
            view.show; 'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `view_hide_does_not_throw`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("""
            view = Mrboto::View.from_registry($viewId)
            view.hide; 'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `view_request_focus_does_not_throw`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("""
            view = Mrboto::View.from_registry($viewId)
            view.request_focus; 'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `view_perform_click_does_not_throw`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("""
            view = Mrboto::View.from_registry($viewId)
            view.perform_click; 'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    // ── Activity Instance Methods ───────────────────────────────────

    @Test
    fun `activity_show_dialog_does_not_throw`() {
        setupActivity()
        val result = mruby.eval("""
            activity = Mrboto.current_activity
            activity.show_dialog('Test', 'Message', ['OK'])
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `activity_show_snackbar_does_not_throw`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("""
            activity = Mrboto.current_activity
            view = Mrboto::View.from_registry($viewId)
            activity.show_snackbar(view, 'Snackbar message', :short)
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `activity_show_popup_menu_does_not_throw`() {
        setupActivity()
        val viewId = mruby.eval("Mrboto._test_view_id")
        val result = mruby.eval("""
            activity = Mrboto.current_activity
            view = Mrboto::View.from_registry($viewId)
            activity.show_popup_menu(view, ['Item 1', 'Item 2'])
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }
}
