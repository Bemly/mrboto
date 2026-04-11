package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for Mrboto::Activity class: instantiation, content_view,
 * title, find_view_by_id, and lifecycle hooks.
 */
class ActivityClassTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    @Test
    fun `Activity class is defined`() {
        // Use Mrboto.const_defined? instead of Object::const_defined? for mruby compatibility
        assertEquals("true", mruby.eval("Mrboto.const_defined?(:Activity).to_s"))
    }

    @Test
    fun `Activity is a subclass of JavaObject`() {
        assertEquals("true", mruby.eval("(Mrboto::Activity < Mrboto::JavaObject).to_s"))
    }

    @Test
    fun `Activity can be instantiated with registry id`() {
        val id = mruby.registerJavaObject(Any())
        val result = mruby.eval("act = Mrboto::Activity.new($id); act._registry_id.to_s")
        assertEquals(id.toString(), result)
    }

    @Test
    fun `Activity instance has _registry_id`() {
        val id = mruby.registerJavaObject(Any())
        val result = mruby.eval("act = Mrboto::Activity.new($id); act._registry_id > 0")
        assertEquals("true", result)
    }

    @Test
    fun `Activity has lifecycle methods`() {
        val methods = listOf("on_create", "on_start", "on_resume", "on_pause", "on_stop", "on_destroy", "on_restart", "on_post_create")
        for (method in methods) {
            val result = mruby.eval("Mrboto::Activity.instance_methods.include?(:$method).to_s")
            assertEquals("Activity should have $method", "true", result)
        }
    }

    @Test
    fun `Activity has content_view method`() {
        assertEquals("true", mruby.eval("Mrboto::Activity.instance_methods.include?(:content_view).to_s"))
    }

    @Test
    fun `Activity has set_content_view method`() {
        assertEquals("true", mruby.eval("Mrboto::Activity.instance_methods.include?(:set_content_view).to_s"))
    }

    @Test
    fun `Activity has find_view_by_id method`() {
        assertEquals("true", mruby.eval("Mrboto::Activity.instance_methods.include?(:find_view_by_id).to_s"))
    }

    @Test
    fun `Activity on_create can be called`() {
        val id = mruby.registerJavaObject(Any())
        val result = mruby.eval("act = Mrboto::Activity.new($id); act.on_create(nil); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun `Activity subclass can override on_create`() {
        val id = mruby.registerJavaObject(Any())
        mruby.loadScript(
            "class TestOverrideActivity < Mrboto::Activity\n" +
            "  def on_create(bundle)\n" +
            "    super\n" +
            "    @custom_flag = true\n" +
            "  end\n" +
            "end\n" +
            "\$test_act = TestOverrideActivity.new($id)\n" +
            "\$test_act.on_create(nil)"
        )
        assertEquals("true", mruby.eval("\$test_act.instance_variable_get(:@custom_flag).to_s"))
    }

    @Test
    fun `Activity content_view is nil before setting`() {
        val id = mruby.registerJavaObject(Any())
        val result = mruby.eval("act = Mrboto::Activity.new($id); act.content_view.nil?.to_s")
        assertEquals("true", result)
    }
}
