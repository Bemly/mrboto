package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for the JNI GlobalRef registry: capacity limits,
 * slot reuse, and lifecycle of registered references.
 */
class RegistryStressTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    @Test
    fun `registry returns sequential IDs starting from 1`() {
        val id1 = mruby.registerJavaObject("a")
        val id2 = mruby.registerJavaObject("b")
        assertEquals(1, id1)
        assertEquals(2, id2)
    }

    @Test
    fun `lookup returns same object that was registered`() {
        val obj = "hello registry"
        val id = mruby.registerJavaObject(obj)
        val retrieved = mruby.lookupJavaObject<String>(id)
        assertSame("Should retrieve the exact same object", obj, retrieved)
    }

    @Test
    fun `registered object can be looked up multiple times`() {
        val obj = Any()
        val id = mruby.registerJavaObject(obj)
        val first = mruby.lookupJavaObject<Any>(id)
        val second = mruby.lookupJavaObject<Any>(id)
        assertSame("Both lookups should return the same reference", first, second)
    }

    @Test
    fun `registry fills up at 4096 entries`() {
        val ids = mutableListOf<Int>()
        var filled = false
        for (i in 0..5000) {
            val id = mruby.registerJavaObject("fill-$i")
            if (id == 0) {
                filled = true
                break
            }
            ids.add(id)
        }
        assertTrue("Registry should be full (4096 max)", filled)
        // 4096 max means IDs 1..4095 (ID 0 means failure)
        assertTrue("Should have around 4095 registered entries", ids.size >= 4090)
        // After filling, next registration should fail
        val afterFull = mruby.registerJavaObject("overflow")
        assertEquals("Should return 0 when registry is full", 0, afterFull)
    }

    @Test
    fun `very large registry ID returns null on lookup`() {
        val result = mruby.lookupJavaObject<Any>(999999)
        assertNull("Out of range ID should return null", result)
    }

    @Test
    fun `negative registry ID returns null on lookup`() {
        val result = mruby.lookupJavaObject<Any>(-1)
        assertNull("Negative ID should return null", result)
    }
}
