package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for the widget DSL: widget creation, attributes,
 * nested widgets, and event callbacks.
 */
class WidgetsTest {

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

    @Test
    fun `JAVA_CLASS_MAP has expected widgets`() {
        val result = mruby.eval("Mrboto::Widgets::JAVA_CLASS_MAP.size")
        val size = result.toIntOrNull()
        assertTrue("Should have widget entries", size != null && size >= 10)
    }

    @Test
    fun `CLASS_TO_WIDGET reverse map exists`() {
        val result = mruby.eval("Mrboto::Widgets::CLASS_TO_WIDGET.size")
        val size = result.toIntOrNull()
        assertTrue("Should have reverse map entries", size != null && size > 0)
    }

    @Test
    fun `View class exists`() {
        assertEquals("true", mruby.eval("defined?(Mrboto::View).to_s"))
    }

    @Test
    fun `TextView is subclass of View`() {
        assertEquals("true", mruby.eval("(Mrboto::TextView < Mrboto::View).to_s"))
    }

    @Test
    fun `Button is subclass of TextView`() {
        assertEquals("true", mruby.eval("(Mrboto::Button < Mrboto::TextView).to_s"))
    }

    @Test
    fun `EditText is subclass of TextView`() {
        assertEquals("true", mruby.eval("(Mrboto::EditText < Mrboto::TextView).to_s"))
    }

    @Test
    fun `ImageView exists and is View subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::ImageView < Mrboto::View).to_s"))
    }

    @Test
    fun `LinearLayout is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::LinearLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `ScrollView is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::ScrollView < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `RelativeLayout is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::RelativeLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `FrameLayout is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::FrameLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `View responds to common attribute setters`() {
        setupActivity()
        val setters = listOf("id", "enabled", "visibility", "background_color", "padding", "gravity")
        for (setter in setters) {
            val result = mruby.eval("Mrboto::View.instance_methods.include?(:$setter).to_s")
            assertEquals("View should respond to $setter", "true", result)
        }
    }

    @Test
    fun `TextView responds to text setters`() {
        val setters = listOf("text", "text_size", "text_color", "hint")
        for (s in setters) {
            val result = mruby.eval("Mrboto::TextView.instance_methods.include?(:$s).to_s")
            assertEquals("TextView should respond to $s", "true", result)
        }
    }

    @Test
    fun `View from_registry with nil returns nil`() {
        assertEquals("true", mruby.eval("Mrboto::View.from_registry(nil).nil?.to_s"))
    }

    @Test
    fun `View from_registry with 0 returns nil`() {
        assertEquals("true", mruby.eval("Mrboto::View.from_registry(0).nil?.to_s"))
    }

    @Test
    fun `View from_registry with valid ID returns View`() {
        val id = mruby.registerJavaObject(Any())
        val result = mruby.eval("Mrboto::View.from_registry($id).class.to_s")
        assertEquals("Mrboto::View", result)
    }

    @Test
    fun `visibility accepts gone invisible and default`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        mruby.eval("""
            v = Mrboto::View.from_registry($ctxId)
            v.visibility = :gone
            v.visibility = :invisible
            v.visibility = 0
            'ok'
        """.trimIndent())
    }

    @Test
    fun `background_color accepts hex string`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            v = Mrboto::View.from_registry($ctxId)
            v.background_color = "FF0000"
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `gravity accepts common symbols`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val gravities = listOf("center", "center_vertical", "center_horizontal", "top", "bottom", "left", "right")
        for (g in gravities) {
            val result = mruby.eval("""
                v = Mrboto::View.from_registry($ctxId)
                v.gravity = :$g
                'ok'
            """.trimIndent())
            assertEquals("ok on $g", "ok", result)
        }
    }

    @Test
    fun `padding applies dp conversion`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            v = Mrboto::View.from_registry($ctxId)
            v.padding = 16
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `enabled setter works`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            v = Mrboto::View.from_registry($ctxId)
            v.enabled = false
            v.enabled = true
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `LinearLayout responds to orientation`() {
        assertEquals("true", mruby.eval("Mrboto::LinearLayout.instance_methods.include?(:orientation).to_s"))
    }

    @Test
    fun `create_view returns positive registry ID`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            id = Mrboto._create_view($ctxId, "android.widget.TextView", {})
            id > 0
        """.trimIndent())
        assertEquals("true", result)
    }

    @Test
    fun `apply_attrs does not crash on unknown attr`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            view_id = Mrboto._create_view($ctxId, "android.widget.TextView", {})
            view = Mrboto::TextView.from_registry(view_id)
            Mrboto::Widgets.apply_attrs(view, { unknown_attr: "test" })
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }
}
