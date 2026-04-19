package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TimeStrftimeTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    @Test
    fun `strftime_HH_MM_SS`() {
        val result = mruby.eval("Time.now.strftime('%H:%M:%S')")
        assertTrue("Should match HH:MM:SS pattern", result.matches(Regex("\\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun `strftime_YYYY_MM_DD`() {
        val result = mruby.eval("Time.now.strftime('%Y-%m-%d')")
        assertTrue("Should match YYYY-MM-DD pattern", result.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }

    @Test
    fun `strftime_literal_percent`() {
        val result = mruby.eval("Time.now.strftime('%%')")
        assertEquals("%", result)
    }

    @Test
    fun `strftime_full_datetime`() {
        val result = mruby.eval("Time.now.strftime('%Y-%m-%d %H:%M:%S')")
        assertTrue("Should match full datetime", result.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")))
    }

    @Test
    fun `strftime_day_of_week`() {
        val result = mruby.eval("Time.now.strftime('%w')")
        val dow = result.toIntOrNull()
        assertNotNull("Should be a digit", dow)
        assertTrue("Should be 0-6", dow!! in 0..6)
    }

    @Test
    fun `strftime_plain_text`() {
        val result = mruby.eval("Time.now.strftime('hello')")
        assertEquals("hello", result)
    }

    @Test
    fun `strftime_year`() {
        val result = mruby.eval("Time.now.strftime('%Y')")
        val year = result.toIntOrNull()
        assertNotNull("Should be a number", year)
        assertTrue("Should be >= 2026", year!! >= 2026)
    }

    @Test
    fun `strftime_padding`() {
        val hour = mruby.eval("Time.at(0).strftime('%H')")
        assertEquals("00", hour)
    }
}
