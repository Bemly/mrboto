package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for the Ruby-side callback registry and dispatch.
 */
class CallbackDispatchTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    @Test
    fun `register_callback returns positive integer ID`() {
        val result = mruby.eval("Mrboto.register_callback { 'hello' }")
        val id = result.toIntOrNull()
        assertNotNull("Callback should return an integer ID", id)
        assertTrue("ID should be positive", id!! > 0)
    }

    @Test
    fun `register_callback returns unique IDs`() {
        mruby.eval("Mrboto.register_callback { 'a' }")
        mruby.eval("Mrboto.register_callback { 'b' }")
        val result = mruby.eval("ids = []; 2.times { ids << Mrboto.register_callback { 'c' } }; ids.uniq.size == 2")
        assertEquals("true", result)
    }

    @Test
    fun `dispatch_callback executes the registered block`() {
        mruby.eval("@cb_result = nil; Mrboto.register_callback { @cb_result = 'executed' }")
        mruby.eval("Mrboto.dispatch_callback(1)")
        assertEquals("executed", mruby.eval("@cb_result"))
    }

    @Test
    fun `dispatch_callback with non existent ID is silent`() {
        val result = mruby.eval("Mrboto.dispatch_callback(99999)")
        assertEquals("nil", result)
    }

    @Test
    fun `dispatch_callback passes arguments to block`() {
        mruby.eval("@received = nil; Mrboto.register_callback { |x| @received = x }")
        mruby.eval("Mrboto.dispatch_callback(1, 'test_arg')")
        assertEquals("test_arg", mruby.eval("@received"))
    }

    @Test
    fun `dispatch_text_changed passes text to block`() {
        mruby.eval("@text = nil; Mrboto.register_callback { |t| @text = t }")
        mruby.eval("Mrboto.dispatch_text_changed(1, 'hello world')")
        assertEquals("hello world", mruby.eval("@text"))
    }

    @Test
    fun `dispatch_checked passes boolean to block`() {
        mruby.eval("@checked = nil; Mrboto.register_callback { |c| @checked = c }")
        mruby.eval("Mrboto.dispatch_checked(1, true)")
        assertEquals("true", mruby.eval("@checked.to_s"))
    }

    @Test
    fun `multiple callbacks can be registered and dispatched independently`() {
        mruby.eval("""
            @results = {}
            Mrboto.register_callback { @results[:a] = 'alpha' }
            Mrboto.register_callback { @results[:b] = 'beta' }
        """.trimIndent())
        mruby.eval("Mrboto.dispatch_callback(1)")
        mruby.eval("Mrboto.dispatch_callback(2)")
        assertEquals("alpha", mruby.eval("@results[:a]"))
        assertEquals("beta", mruby.eval("@results[:b]"))
    }
}
