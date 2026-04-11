package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for the core MRuby VM: eval, version, gc, close,
 * registerJavaObject, lookupJavaObject, loadScript.
 */
class MRubyTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    @Test
    fun `eval basic arithmetic`() {
        assertEquals("3", mruby.eval("1 + 2"))
    }

    @Test
    fun `eval string concatenation`() {
        assertEquals("helloworld", mruby.eval("\"hello\" + \"world\""))
    }

    @Test
    fun `eval array creation`() {
        assertEquals("3", mruby.eval("[1, 2, 3].size"))
    }

    @Test
    fun `eval hash access`() {
        assertEquals("bar", mruby.eval("h = {foo: \"bar\"}; h[:foo]"))
    }

    @Test
    fun `eval returns nil string for nil constant`() {
        assertEquals("nil", mruby.eval("nil"))
    }

    @Test
    fun `eval syntax error returns error message`() {
        val result = mruby.eval("def foo(")
        assertTrue("Expected error message but got: $result", result.isNotEmpty() && result != "ok")
    }

    @Test
    fun `version returns mruby version string`() {
        val v = mruby.version()
        assertTrue("Version should not be empty", v.isNotEmpty())
    }

    @Test
    fun `gc does not throw`() {
        mruby.gc()
    }

    @Test
    fun `isOpen is true before close`() {
        assertTrue(mruby.isOpen)
    }

    @Test
    fun `registerJavaObject returns positive registry ID`() {
        val id = mruby.registerJavaObject("test string")
        assertTrue("Registry ID should be positive, got $id", id > 0)
    }

    @Test
    fun `lookupJavaObject retrieves same object`() {
        val obj = Any()
        val id = mruby.registerJavaObject(obj)
        val retrieved = mruby.lookupJavaObject<Any>(id)
        assertSame("Should retrieve the exact same object reference", obj, retrieved)
    }

    @Test
    fun `loadScript returns ok for valid script`() {
        val result = mruby.loadScript("x = 1 + 2")
        assertEquals("ok", result)
    }

    @Test
    fun `loadScript returns error for invalid script`() {
        val result = mruby.loadScript("invalid_syntax {{{")
        assertTrue("Expected error, got: $result", result.isNotEmpty() && result != "ok")
    }

    @Test
    fun `loadScript can define and call method`() {
        val result = mruby.loadScript(
            """
            def add(a, b)
              a + b
            end
            """.trimIndent()
        )
        assertEquals("ok", result)
        assertEquals("7", mruby.eval("add(3, 4).to_s"))
    }
}
