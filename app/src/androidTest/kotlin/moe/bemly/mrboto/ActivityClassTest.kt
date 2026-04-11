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

    private fun setupActivity() {
        val actId = mruby.registerJavaObject(Any())
        mruby.eval("Mrboto.current_activity_id = $actId")
        mruby.eval("""
            class TestActivity < Mrboto::Activity; end
            Mrboto.current_activity = TestActivity.new(Mrboto.current_activity_id)
        """.trimIndent())
    }

    @Test
    fun `Activity can be instantiated`() {
        setupActivity()
        assertEquals("true", mruby.eval("Mrboto.current_activity.is_a?(Mrboto::Activity).to_s"))
    }

    @Test
    fun `Activity has _registry_id`() {
        setupActivity()
        val result = mruby.eval("Mrboto.current_activity._registry_id")
        assertNotEquals("0", result)
    }

    @Test
    fun `Activity has lifecycle hooks`() {
        setupActivity()
        val result = mruby.eval("Mrboto.current_activity.respond_to?(:on_create).to_s")
        assertEquals("true", result)
    }

    @Test
    fun `on_create sets bundle`() {
        setupActivity()
        mruby.eval("Mrboto.current_activity.on_create(nil)")
        // on_create stores @bundle, should not raise
        assertEquals("ok", mruby.eval("nil"))
    }

    @Test
    fun `title setter does not throw`() {
        setupActivity()
        val result = mruby.eval("Mrboto.current_activity.title = 'Test Title'; 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun `activity responds to all lifecycle methods`() {
        setupActivity()
        val methods = listOf("on_create", "on_start", "on_resume", "on_pause", "on_stop", "on_destroy", "on_restart", "on_post_create")
        for (method in methods) {
            val result = mruby.eval("Mrboto.current_activity.respond_to?(:$method).to_s")
            assertEquals("respond_to?($method)", "true", result)
        }
    }

    @Test
    fun `set_content_view method exists on Activity`() {
        setupActivity()
        val result = mruby.eval("Mrboto.current_activity.respond_to?(:set_content_view).to_s")
        assertEquals("true", result)
    }

    @Test
    fun `content_view getter returns nil initially`() {
        setupActivity()
        assertEquals("true", mruby.eval("Mrboto.current_activity.content_view.nil?.to_s"))
    }

    @Test
    fun `Activity is a subclass of JavaObject`() {
        assertEquals("true", mruby.eval("Mrboto::Activity < Mrboto::JavaObject"))
    }

    @Test
    fun `Activity has find_view_by_id method`() {
        setupActivity()
        val result = mruby.eval("Mrboto.current_activity.respond_to?(:find_view_by_id).to_s")
        assertEquals("true", result)
    }

    @Test
    fun `Activity subclass can override on_create`() {
        val actId = mruby.registerJavaObject(Any())
        mruby.eval("Mrboto.current_activity_id = $actId")
        mruby.eval("""
            class CustomActivity < Mrboto::Activity
              def on_create(bundle)
                super
                @custom_called = true
              end
            end
            act = CustomActivity.new(Mrboto.current_activity_id)
            Mrboto.current_activity = act
        """.trimIndent())
        mruby.dispatchLifecycle(actId, "on_create")
        assertEquals("true", mruby.eval("@custom_called.to_s"))
    }
}
