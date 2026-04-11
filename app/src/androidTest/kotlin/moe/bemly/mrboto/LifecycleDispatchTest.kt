package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for lifecycle dispatch mechanism: dispatchLifecycle,
 * current_activity setup, and method invocation.
 */
class LifecycleDispatchTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    @Test
    fun `dispatchLifecycle returns error when no current_activity set`() {
        val result = mruby.dispatchLifecycle(0, "on_create")
        assertTrue(result.contains("no current_activity") || result.contains("Error"))
    }

    @Test
    fun `dispatchLifecycle ok when current_activity is set`() {
        val actId = mruby.registerJavaObject(Any())
        mruby.eval("Mrboto.current_activity_id = $actId")
        mruby.eval("class TestActivity < Mrboto::JavaObject; end")
        mruby.eval("act = TestActivity.from_registry($actId)")
        mruby.eval("Mrboto.current_activity = act")
        val result = mruby.dispatchLifecycle(actId, "on_create")
        assertEquals("ok", result)
    }

    @Test
    fun `current_activity_id accessor works`() {
        mruby.eval("Mrboto.current_activity_id = 42")
        assertEquals("42", mruby.eval("Mrboto.current_activity_id.to_s"))
    }

    @Test
    fun `current_activity accessor works`() {
        val actId = mruby.registerJavaObject(Any())
        mruby.eval("Mrboto.current_activity_id = $actId")
        mruby.eval("act = Mrboto::JavaObject.from_registry($actId)")
        mruby.eval("Mrboto.current_activity = act")
        val result = mruby.eval("Mrboto.current_activity._registry_id.to_s")
        assertEquals(actId.toString(), result)
    }

    @Test
    fun `_ruby_activity_class accessor works`() {
        mruby.eval("class MyActivity < Mrboto::JavaObject; end")
        mruby.eval("Mrboto._ruby_activity_class = MyActivity")
        assertEquals("MyActivity", mruby.eval("Mrboto._ruby_activity_class.to_s"))
    }

    @Test
    fun `lifecycle methods can be overridden and called`() {
        val actId = mruby.registerJavaObject(Any())
        mruby.eval("Mrboto.current_activity_id = $actId")
        mruby.eval("""
            class LifecycleTestActivity < Mrboto::Activity
              def on_create(bundle)
                super
                @created = true
              end
            end
            Mrboto._ruby_activity_class = LifecycleTestActivity
            act = LifecycleTestActivity.new(Mrboto.current_activity_id)
            Mrboto.current_activity = act
        """.trimIndent())
        val result = mruby.dispatchLifecycle(actId, "on_create")
        assertEquals("ok", result)
        assertEquals("true", mruby.eval("@created.to_s"))
    }

    @Test
    fun `all lifecycle hooks can be called`() {
        val actId = mruby.registerJavaObject(Any())
        mruby.eval("Mrboto.current_activity_id = $actId")
        mruby.eval("""
            class AllHooksActivity < Mrboto::Activity
              def on_create(bundle); super; @hooks = []; @hooks << :create; end
              def on_start; @hooks << :start; end
              def on_resume; @hooks << :resume; end
              def on_pause; @hooks << :pause; end
              def on_stop; @hooks << :stop; end
              def on_destroy; @hooks << :destroy; end
            end
            act = AllHooksActivity.new(Mrboto.current_activity_id)
            Mrboto.current_activity = act
        """.trimIndent())
        val phases = listOf("on_create", "on_start", "on_resume", "on_pause", "on_stop", "on_destroy")
        for (phase in phases) {
            val result = mruby.dispatchLifecycle(actId, phase)
            assertEquals("ok on $phase", "ok", result)
        }
        assertEquals("6", mruby.eval("@hooks.size.to_s"))
    }
}
