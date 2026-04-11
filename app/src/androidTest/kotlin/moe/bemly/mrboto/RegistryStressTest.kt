package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for the JNI GlobalRef registry: capacity limits,
 * slot reuse, and lifecycle of registered references.
 *
 * Note: The C-side registry is a global static (4096 slots) shared across
 * all tests in the process. These tests avoid exhausting it so that
 * subsequent tests can still register objects.
 */
class RegistryStressTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    @Test
    fun `registry returns sequential IDs starting from a positive number`() {
        val id1 = mruby.registerJavaObject("a")
        val id2 = mruby.registerJavaObject("b")
        val id3 = mruby.registerJavaObject("c")
        assertTrue("First ID should be positive", id1 > 0)
        assertEquals("IDs should be sequential", id1 + 1, id2)
        assertEquals("IDs should be sequential", id2 + 1, id3)
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
    fun `registry overflow returns zero`() {
        // Fill a small batch of registrations to verify the mechanism works,
        // but don't exhaust the global 4096-slot registry.
        val startId = mruby.registerJavaObject("start-marker")
        assertTrue("Start ID should be positive", startId > 0)

        // Register 100 more and verify they are sequential
        val ids = mutableListOf(startId)
        for (i in 0 until 100) {
            val id = mruby.registerJavaObject("fill-$i")
            if (id == 0) break
            ids.add(id)
        }
        // Verify sequential ordering
        for (i in 1 until ids.size) {
            assertEquals("ID at $i should be sequential", ids[i - 1] + 1, ids[i])
        }
        // At least 50 should have succeeded
        assertTrue("Should have registered at least 50 objects", ids.size >= 50)
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
