package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for layout constants, gravity values, orientation, and dp conversion.
 */
class LayoutConstantsTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    @Test
    fun `MATCH_PARENT is -1`() {
        assertEquals("-1", mruby.eval("Mrboto::LayoutParams::MATCH_PARENT.to_s"))
    }

    @Test
    fun `FILL_PARENT is -1`() {
        assertEquals("-1", mruby.eval("Mrboto::LayoutParams::FILL_PARENT.to_s"))
    }

    @Test
    fun `WRAP_CONTENT is -2`() {
        assertEquals("-2", mruby.eval("Mrboto::LayoutParams::WRAP_CONTENT.to_s"))
    }

    @Test
    fun `top-level MATCH_PARENT matches LayoutParams`() {
        assertEquals("ok", mruby.eval("Mrboto::MATCH_PARENT == Mrboto::LayoutParams::MATCH_PARENT ? 'ok' : 'fail'"))
    }

    @Test
    fun `top-level WRAP_CONTENT matches LayoutParams`() {
        assertEquals("ok", mruby.eval("Mrboto::WRAP_CONTENT == Mrboto::LayoutParams::WRAP_CONTENT ? 'ok' : 'fail'"))
    }

    @Test
    fun `Gravity CENTER is 17`() {
        assertEquals("17", mruby.eval("Mrboto::Gravity::CENTER.to_s"))
    }

    @Test
    fun `Gravity CENTER_VERTICAL is 16`() {
        assertEquals("16", mruby.eval("Mrboto::Gravity::CENTER_VERTICAL.to_s"))
    }

    @Test
    fun `Gravity CENTER_HORIZONTAL is 1`() {
        assertEquals("1", mruby.eval("Mrboto::Gravity::CENTER_HORIZONTAL.to_s"))
    }

    @Test
    fun `Gravity TOP is 48`() {
        assertEquals("48", mruby.eval("Mrboto::Gravity::TOP.to_s"))
    }

    @Test
    fun `Gravity BOTTOM is 80`() {
        assertEquals("80", mruby.eval("Mrboto::Gravity::BOTTOM.to_s"))
    }

    @Test
    fun `Orientation VERTICAL is 1`() {
        assertEquals("1", mruby.eval("Mrboto::Orientation::VERTICAL.to_s"))
    }

    @Test
    fun `Orientation HORIZONTAL is 0`() {
        assertEquals("0", mruby.eval("Mrboto::Orientation::HORIZONTAL.to_s"))
    }

    @Test
    fun `dp converts a positive integer`() {
        val result = mruby.eval("dp(10)")
        val px = result.toIntOrNull()
        assertNotNull("dp should return an integer", px)
        assertTrue("dp(10) should be positive", px!! > 0)
    }

    @Test
    fun `dp returns roughly 1.5x multiplier`() {
        val result = mruby.eval("dp(100)")
        val px = result.toIntOrNull()
        assertNotNull(px)
        assertTrue("dp(100) should be around 150, got $px", px!! in 140..160)
    }

    @Test
    fun `Gravity LEFT is 3`() {
        assertEquals("3", mruby.eval("Mrboto::Gravity::LEFT.to_s"))
    }

    @Test
    fun `Gravity RIGHT is 5`() {
        assertEquals("5", mruby.eval("Mrboto::Gravity::RIGHT.to_s"))
    }
}
