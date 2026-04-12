package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for error handling: syntax errors, runtime errors,
 * invalid registry IDs, and closed VM behavior.
 */
class ErrorHandlingTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    @Test
    fun `syntax error returns error message not ok`() {
        val result = mruby.eval("def incomplete(")
        assertNotEquals("ok", result)
    }

    @Test
    fun `undefined variable returns error`() {
        val result = mruby.eval("undefined_variable_xyz")
        assertNotEquals("ok", result)
    }

    @Test
    fun `undefined method returns error`() {
        val result = mruby.eval("42.nonexistent_method")
        assertNotEquals("ok", result)
    }

    @Test
    fun `nil constant eval returns nil string`() {
        assertEquals("nil", mruby.eval("nil"))
    }

    @Test
    fun `exception class returns ok`() {
        assertEquals("ok", mruby.eval("begin; raise 'test'; rescue; 'ok'; end"))
    }

    @Test
    fun `invalid registry lookup returns null`() {
        val obj = mruby.lookupJavaObject<Any>(-1)
        assertNull("Invalid registry ID should return null", obj)
    }

    @Test
    fun `zero registry lookup returns null`() {
        val obj = mruby.lookupJavaObject<Any>(0)
        assertNull("Registry ID 0 should return null", obj)
    }

    @Test
    fun `registerJavaObject with null returns positive ID`() {
        val id = mruby.registerJavaObject("test")
        assertTrue(id > 0)
    }

    @Test
    fun `lookupJavaObject with very large ID returns null`() {
        val obj = mruby.lookupJavaObject<Any>(999999)
        assertNull("Out of range ID should return null", obj)
    }

    @Test
    fun `loadScript with empty string returns empty`() {
        val result = mruby.loadScript("")
        assertTrue("Should return empty or error", result.isEmpty() || result.startsWith("Error:"))
    }

    @Test
    fun `loadScript with only whitespace returns empty`() {
        val result = mruby.loadScript("   ")
        assertTrue("Should return empty or error", result.isEmpty() || result.startsWith("Error:"))
    }

    @Test
    fun `eval with very long string works`() {
        val longString = "a".repeat(10000)
        val result = mruby.eval("'$longString'.length")
        assertEquals("10000", result)
    }

    @Test
    fun `dispatchLifecycle with invalid callback name does not crash`() {
        val actId = mruby.registerJavaObject(Any())
        mruby.eval("Mrboto.current_activity_id = $actId")
        mruby.eval("Mrboto.current_activity = Mrboto::JavaObject.from_registry($actId)")
        val result = mruby.dispatchLifecycle(actId, "nonexistent_method")
        // Should return error message, not crash
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `View from_registry with invalid ID returns nil`() {
        assertEquals("true", mruby.eval("Mrboto::View.from_registry(99999).nil?.to_s"))
    }

    @Test
    fun `division by zero returns error message not crash`() {
        val result = mruby.eval("1 / 0")
        assertNotEquals("ok", result)
        assertTrue("Should contain error info", result.isNotEmpty() && result != "nil")
    }

    @Test
    fun `runtime error then view_text does not crash`() {
        // Trigger a runtime error first, leaving mrb->exc potentially set
        mruby.eval("1 / 0")
        // Then call _view_text — should NOT crash even after error
        val id = mruby.registerJavaObject(Any())
        // _view_text with non-view object should handle gracefully
        val result = mruby.eval("Mrboto._view_text($id)")
        // Should return something (possibly nil) rather than crashing
        assertNotNull("Should not crash", result)
    }

    @Test
    fun `closed MRuby throws on eval`() {
        val tempMruby = MRuby()
        tempMruby.registerAndroidClasses()
        tempMruby.close()
        try {
            tempMruby.eval("1 + 1")
            fail("Should have thrown after close")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }
}
