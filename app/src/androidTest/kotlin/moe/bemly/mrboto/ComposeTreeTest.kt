package moe.bemly.mrboto

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for the Compose tree parsing and JSON serialization.
 */
class ComposeTreeTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    // ── ComposeNode parsing ──

    @Test
    fun `parse column node with children`() {
        val json = """
            {"type":"column","props":{},"children":[
              {"type":"text","props":{},"children":[],"content":"Hello"},
              {"type":"button","props":{},"children":[],"content":"Click","callback_id":1}
            ]}
        """.trimIndent()
        val node = parseComposableTree(JSONObject(json))

        assertEquals("column", node.type)
        assertEquals(2, node.children.size)
        assertEquals("text", node.children[0].type)
        assertEquals("Hello", node.children[0].content)
        assertEquals("button", node.children[1].type)
        assertEquals("Click", node.children[1].content)
        assertEquals(1, node.children[1].callbackId)
    }

    @Test
    fun `parse scaffold with top_bar and bottom_bar`() {
        val json = """
            {"type":"scaffold","props":{
              "top_bar":{"type":"top_app_bar","props":{},"children":[],"content":"Title"},
              "bottom_bar":{"type":"row","props":{},"children":[]}
            },"children":[{"type":"column","props":{},"children":[]}]}
        """.trimIndent()
        val node = parseComposableTree(JSONObject(json))

        assertEquals("scaffold", node.type)
        assertNotNull(node.props["top_bar"])
        assertNotNull(node.props["bottom_bar"])
    }

    @Test
    fun `parse android_view with view_type prop`() {
        val json = """
            {"type":"android_view","props":{"view_type":"moe.bemly.mrboto.LiquidGlassView","blur_radius":25.0},"children":[]}
        """.trimIndent()
        val node = parseComposableTree(JSONObject(json))

        assertEquals("android_view", node.type)
        assertEquals("moe.bemly.mrboto.LiquidGlassView", node.props["view_type"])
        assertEquals(25.0, node.props["blur_radius"] as Double, 0.001)
    }

    @Test
    fun `parse modifier array in props`() {
        val json = """
            {"type":"text","props":{"modifier":[
              {"type":"padding","value":16},
              {"type":"fill_max_width"}
            ]},"children":[],"content":"Hello"}
        """.trimIndent()
        val node = parseComposableTree(JSONObject(json))

        val mods = node.props["modifier"] as org.json.JSONArray
        assertEquals(2, mods.length())
        assertEquals("padding", mods.getJSONObject(0).getString("type"))
        assertEquals(16.0, mods.getJSONObject(0).getDouble("value"), 0.001)
        assertEquals("fill_max_width", mods.getJSONObject(1).getString("type"))
    }

    // ── Ruby DSL tests ──

    @Test
    fun `ComposeBuilder exists`() {
        val result = mruby.eval("defined?(Mrboto::ComposeBuilder) ? 'ok' : 'fail'")
        assertEquals("ok", result)
    }

    @Test
    fun `ComposeActivity class exists`() {
        val result = mruby.eval("defined?(Mrboto::ComposeActivity) ? 'ok' : 'fail'")
        assertEquals("ok", result)
    }

    @Test
    fun `ComposeModifier chain builds array`() {
        val result = mruby.eval("""
            m = Mrboto::ComposeModifier.new
            m.padding(16).fill_max_width.fill_max_height(0.5)
            arr = m.to_a
            arr.size.to_s
        """.trimIndent())
        assertEquals("3", result)
    }

    @Test
    fun `compose_to_json serializes hash`() {
        val result = mruby.eval("""
            obj = {"type" => "column", "props" => {"padding" => 16}, "children" => []}
            Mrboto._compose_to_json(obj)
        """.trimIndent())
        assertTrue("should contain type key", result.contains("\"type\""))
        assertTrue("should contain column value", result.contains("column"))
    }

    @Test
    fun `compose_to_json serializes array`() {
        val result = mruby.eval("""
            arr = [{"type" => "text"}, {"type" => "button"}]
            Mrboto._compose_to_json(arr)
        """.trimIndent())
        assertTrue("should start with [", result.startsWith("["))
        assertTrue("should contain text", result.contains("text"))
        assertTrue("should contain button", result.contains("button"))
    }

    @Test
    fun `modifier helper creates ComposeModifier`() {
        val result = mruby.eval("""
            m = Mrboto::ComposeModifier.new
            m.padding(8).fill_max_width(0.5)
            arr = m.to_a
            arr.size.to_s + ":" + arr[0]["type"]
        """.trimIndent())
        assertEquals("2:padding", result)
    }

    @Test
    fun `color helper returns hex string`() {
        val result = mruby.eval("color('FF0000')")
        assertEquals("FF0000", result)
    }

    @Test
    fun `Kotlin parseColor handles hex`() {
        // Compose rendering test: parseColor should not crash
        val c = parseColor("FF0000")
        assertNotNull(c)
    }

    @Test
    fun `Kotlin parseColor handles 6-digit hex`() {
        val c = parseColor("6200EE")
        assertNotNull(c)
    }

    @Test
    fun `Kotlin parseColor handles null`() {
        val c = parseColor(null)
        assertEquals(androidx.compose.ui.graphics.Color.Unspecified, c)
    }

    @Test
    fun `Kotlin toDp converts number`() {
        val dp = toDp(16)
        assertEquals(16f, dp.value, 0.1f)
    }

    @Test
    fun `Kotlin toDp converts string`() {
        val dp = toDp("24")
        assertEquals(24f, dp.value, 0.1f)
    }

    @Test
    fun `Kotlin parseArrangement maps values`() {
        assertEquals(androidx.compose.foundation.layout.Arrangement.Center, parseArrangement("center"))
        assertEquals(androidx.compose.foundation.layout.Arrangement.SpaceEvenly, parseArrangement("space_evenly"))
    }

    @Test
    fun `Kotlin parseTextAlign maps values`() {
        assertEquals(androidx.compose.ui.text.style.TextAlign.Center, parseTextAlign("center"))
        assertEquals(androidx.compose.ui.text.style.TextAlign.Start, parseTextAlign("start"))
    }

    @Test
    fun `Kotlin parseFontFamily maps monospace`() {
        val family = parseFontFamily("monospace")
        assertNotNull(family)
    }

    @Test
    fun `Kotlin materialIcon returns vectors`() {
        assertNotNull(materialIcon("light_mode"))
        assertNotNull(materialIcon("dark_mode"))
        assertNotNull(materialIcon("info"))
        assertNotNull(materialIcon("settings"))
    }
}
