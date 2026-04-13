package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for Mrboto.register_activity_class and the _ruby_activity_class accessor.
 */
class ActivityRegistrationTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    @Test
    fun `register_activity_class_sets_the_activity_class`() {
        mruby.loadScript(
            "class TestRegActivity < Mrboto::Activity\n" +
            "  def on_create(bundle); super; end\n" +
            "end\n" +
            "Mrboto.register_activity_class(TestRegActivity)"
        )
        assertEquals("true", mruby.eval("!Mrboto._ruby_activity_class.nil?"))
    }

    @Test
    fun `_ruby_activity_class_returns_the_registered_class`() {
        mruby.loadScript(
            "class TestRegActivity2 < Mrboto::Activity\n" +
            "  def on_create(bundle); super; end\n" +
            "end\n" +
            "Mrboto.register_activity_class(TestRegActivity2)"
        )
        assertEquals("TestRegActivity2", mruby.eval("Mrboto._ruby_activity_class.to_s"))
    }

    @Test
    fun `register_activity_class_overwrites_previous_value`() {
        mruby.loadScript(
            "class TestA < Mrboto::Activity; def on_create(b); super; end; end\n" +
            "class TestB < Mrboto::Activity; def on_create(b); super; end; end\n" +
            "Mrboto.register_activity_class(TestA)\n" +
            "Mrboto.register_activity_class(TestB)"
        )
        assertEquals("TestB", mruby.eval("Mrboto._ruby_activity_class.to_s"))
    }

    @Test
    fun `_ruby_activity_class_is_initially_nil`() {
        // Fresh Mrboto instance — no class registered yet
        assertEquals("true", mruby.eval("Mrboto._ruby_activity_class.nil?.to_s"))
    }

    @Test
    fun `backward_compat_direct_assignment_still_works`() {
        mruby.loadScript(
            "class TestCompat < Mrboto::Activity\n" +
            "  def on_create(bundle); super; end\n" +
            "end\n" +
            "Mrboto._ruby_activity_class = TestCompat"
        )
        assertEquals("TestCompat", mruby.eval("Mrboto._ruby_activity_class.to_s"))
    }
}
