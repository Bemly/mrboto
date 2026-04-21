package moe.bemly.mrboto

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for the glass_bar and kyant.backdrop low-level API nodes.
 */
class GlassBarTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    // ── glass_bar node parsing ──────────────────────────────────────

    @Test
    fun `parse glass_bar node with props`() {
        val json = """
            {"type":"glass_bar","props":{
              "shape_type":"rounded_rect",
              "corner_radius":24.0,
              "blur_radius":25.0,
              "vibrancy":true,
              "top_bar":{"type":"top_app_bar","props":{},"children":[],"content":"Title"}
            },"children":[
              {"type":"column","props":{},"children":[]},
              {"type":"text_button","props":{"icon":"play_arrow"},"children":[],"content":"Run","callback_id":1}
            ]}
        """.trimIndent()
        val node = parseComposableTree(JSONObject(json))

        assertEquals("glass_bar", node.type)
        assertEquals("rounded_rect", node.props["shape_type"])
        assertEquals(24.0, (node.props["corner_radius"] as Number).toDouble(), 0.001)
        assertEquals(25.0, (node.props["blur_radius"] as Number).toDouble(), 0.001)
        assertEquals(true, node.props["vibrancy"])
        assertNotNull(node.props["top_bar"])
        assertEquals(2, node.children.size)
        assertEquals("column", node.children[0].type)
        assertEquals("text_button", node.children[1].type)
        assertEquals(1, node.children[1].callbackId)
    }

    @Test
    fun `parse glass_bar with circle shape`() {
        val json = """
            {"type":"glass_bar","props":{"shape_type":"circle","corner_radius":32.0,"blur_radius":20.0,"vibrancy":false},"children":[]}
        """.trimIndent()
        val node = parseComposableTree(JSONObject(json))

        assertEquals("circle", node.props["shape_type"])
        assertEquals(false, node.props["vibrancy"])
    }

    @Test
    fun `parse glass_bar with surface_color and surface_alpha`() {
        val json = """
            {"type":"glass_bar","props":{"surface_color":"1C1C1E","surface_alpha":0.3},"children":[]}
        """.trimIndent()
        val node = parseComposableTree(JSONObject(json))

        assertEquals("1C1C1E", node.props["surface_color"])
        assertEquals(0.3, (node.props["surface_alpha"] as Number).toDouble(), 0.001)
    }

    // ── layer_backdrop node parsing ────────────────────────────────

    @Test
    fun `parse layer_backdrop node`() {
        val json = """
            {"type":"layer_backdrop","props":{"backdrop_id":1},"children":[
              {"type":"text","props":{},"children":[],"content":"Content"}
            ]}
        """.trimIndent()
        val node = parseComposableTree(JSONObject(json))

        assertEquals("layer_backdrop", node.type)
        assertEquals(1, (node.props["backdrop_id"] as Number).toInt())
        assertEquals(1, node.children.size)
        assertEquals("Content", node.children[0].content)
    }

    @Test
    fun `parse layer_backdrop without id`() {
        val json = """
            {"type":"layer_backdrop","props":{},"children":[]}
        """.trimIndent()
        val node = parseComposableTree(JSONObject(json))

        assertEquals("layer_backdrop", node.type)
        assertNull(node.props["backdrop_id"])
    }

    // ── draw_backdrop_glass node parsing ───────────────────────────

    @Test
    fun `parse draw_backdrop_glass node with all props`() {
        val json = """
            {"type":"draw_backdrop_glass","props":{
              "backdrop_id":1,
              "shape_type":"rounded_rect",
              "corner_radius":16.0,
              "blur_radius":30.0,
              "vibrancy":true,
              "surface_color":"FFFFFF",
              "surface_alpha":0.4
            },"children":[
              {"type":"text_button","props":{},"children":[],"content":"Btn","callback_id":5}
            ]}
        """.trimIndent()
        val node = parseComposableTree(JSONObject(json))

        assertEquals("draw_backdrop_glass", node.type)
        assertEquals(1, (node.props["backdrop_id"] as Number).toInt())
        assertEquals(30.0, (node.props["blur_radius"] as Number).toDouble(), 0.001)
        assertEquals(true, node.props["vibrancy"])
        assertEquals(5, node.children[0].callbackId)
    }

    // ── remember_layer_backdrop node parsing ───────────────────────

    @Test
    fun `parse remember_layer_backdrop node`() {
        val json = """
            {"type":"remember_layer_backdrop","props":{"backdrop_id":42},"children":[
              {"type":"text","props":{},"children":[],"content":"Child"}
            ]}
        """.trimIndent()
        val node = parseComposableTree(JSONObject(json))

        assertEquals("remember_layer_backdrop", node.type)
        assertEquals(42, (node.props["backdrop_id"] as Number).toInt())
    }

    // ── Ruby DSL: glass_bar ────────────────────────────────────────

    @Test
    fun `glass_bar DSL method exists`() {
        val result = mruby.eval("respond_to?(:glass_bar) ? 'ok' : 'fail'")
        assertEquals("ok", result)
    }

    @Test
    fun `glass_bar builds correct node structure`() {
        // Reset builder state
        mruby.eval("""
            Mrboto::ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])
            Mrboto::ComposeBuilder.instance_variable_set(:@_compose_root, nil)
        """.trimIndent())

        val result = mruby.eval("""
            glass_bar(shape_type: :circle, corner_radius: 32.0, blur_radius: 20.0) {
              text("content")
              text_button("Run", icon: :play_arrow) { }
            }
            root = Mrboto::ComposeBuilder.root
            "type=#{root['type']},shape=#{root['props']['shape_type']},children=#{root['children'].size}"
        """.trimIndent())

        assertEquals("type=glass_bar,shape=circle,children=2", result)
    }

    @Test
    fun `glass_bar DSL with top_bar prop`() {
        mruby.eval("""
            Mrboto::ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])
            Mrboto::ComposeBuilder.instance_variable_set(:@_compose_root, nil)
        """.trimIndent())

        val result = mruby.eval("""
            glass_bar(top_bar: -> { top_app_bar("Test") }) {
              text("content")
            }
            root = Mrboto::ComposeBuilder.root
            has_top = root['props']['top_bar'] != nil
            "type=#{root['type']},top_bar=#{has_top}"
        """.trimIndent())

        assertEquals("type=glass_bar,top_bar=true", result)
    }

    // ── Ruby DSL: low-level backdrop API ───────────────────────────

    @Test
    fun `remember_layer_backdrop DSL exists`() {
        val result = mruby.eval("respond_to?(:remember_layer_backdrop) ? 'ok' : 'fail'")
        assertEquals("ok", result)
    }

    @Test
    fun `layer_backdrop DSL exists`() {
        val result = mruby.eval("respond_to?(:layer_backdrop) ? 'ok' : 'fail'")
        assertEquals("ok", result)
    }

    @Test
    fun `draw_backdrop_glass DSL exists`() {
        val result = mruby.eval("respond_to?(:draw_backdrop_glass) ? 'ok' : 'fail'")
        assertEquals("ok", result)
    }

    @Test
    fun `remember_layer_backdrop creates correct node`() {
        mruby.eval("""
            Mrboto::ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])
            Mrboto::ComposeBuilder.instance_variable_set(:@_compose_root, nil)
        """.trimIndent())

        val result = mruby.eval("""
            remember_layer_backdrop(5) {
              text("inside")
            }
            root = Mrboto::ComposeBuilder.root
            "type=#{root['type']},id=#{root['props']['backdrop_id']},children=#{root['children'].size}"
        """.trimIndent())

        assertEquals("type=remember_layer_backdrop,id=5,children=1", result)
    }

    @Test
    fun `draw_backdrop_glass builds correct node`() {
        mruby.eval("""
            Mrboto::ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])
            Mrboto::ComposeBuilder.instance_variable_set(:@_compose_root, nil)
        """.trimIndent())

        val result = mruby.eval("""
            draw_backdrop_glass(backdrop_id: 2, shape: :rounded_rect, corner_radius: 16.0, blur_radius: 25.0) {
              text_button("Btn") { }
            }
            root = Mrboto::ComposeBuilder.root
            "type=#{root['type']},id=#{root['props']['backdrop_id']},blur=#{root['props']['blur_radius']},children=#{root['children'].size}"
        """.trimIndent())

        assertEquals("type=draw_backdrop_glass,id=2,blur=25.0,children=1", result)
    }

    // ── JSON serialization ─────────────────────────────────────────

    @Test
    fun `glass_bar serializes to valid JSON`() {
        mruby.eval("""
            Mrboto::ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])
            Mrboto::ComposeBuilder.instance_variable_set(:@_compose_root, nil)
        """.trimIndent())

        val json = mruby.eval("""
            glass_bar(blur_radius: 25.0, vibrancy: true) {
              text("content")
              text_button("Run") { }
            }
            Mrboto._compose_to_json(Mrboto::ComposeBuilder.root)
        """.trimIndent())

        assertTrue(json.contains("\"type\":\"glass_bar\""))
        assertTrue(json.contains("\"blur_radius\""))
        assertTrue(json.contains("25"))
    }

    // ── glass_cell: per-button config ─────────────────────────────────────

    @Test
    fun `glass_cell DSL exists`() {
        val result = mruby.eval("respond_to?(:glass_cell) ? 'ok' : 'fail'")
        assertEquals("ok", result)
    }

    @Test
    fun `glass_cell creates node with per-button props`() {
        mruby.eval("""
            Mrboto::ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])
            Mrboto::ComposeBuilder.instance_variable_set(:@_compose_root, nil)
        """.trimIndent())

        val result = mruby.eval("""
            glass_cell(shape: :circle, layout: :aspect_ratio, surface_color: "0088FF", blend_mode: :hue) {
              text_button("Btn") { }
            }
            root = Mrboto::ComposeBuilder.root
            "type=#{root['type']},shape=#{root['props']['glass_shape']},layout=#{root['props']['glass_layout']},color=#{root['props']['glass_surface_color']},blend=#{root['props']['glass_blend_mode']}"
        """.trimIndent())

        assertEquals("type=glass_cell,shape=circle,layout=aspect_ratio,color=0088FF,blend=hue", result)
    }

    @Test
    fun `glass_bar with glass_cell children renders cells`() {
        mruby.eval("""
            Mrboto::ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])
            Mrboto::ComposeBuilder.instance_variable_set(:@_compose_root, nil)
        """.trimIndent())

        val json = mruby.eval("""
            glass_bar(blur_radius: 25.0) {
              text("content")
              glass_cell(shape: :circle) {
                text_button("Run") { }
              }
              glass_cell(shape: :continuous_capsule, layout: :aspect_ratio) {
                text_button("Save") { }
              }
            }
            Mrboto._compose_to_json(Mrboto::ComposeBuilder.root)
        """.trimIndent())

        assertTrue(json.contains("\"type\":\"glass_bar\""))
        assertTrue(json.contains("\"type\":\"glass_cell\""))
        assertTrue(json.contains("\"glass_shape\":\"circle\""))
        assertTrue(json.contains("\"glass_shape\":\"continuous_capsule\""))
        assertTrue(json.contains("\"glass_layout\":\"aspect_ratio\""))
    }

    @Test
    fun `glass_bar without glass_cell still works (backward compat)`() {
        mruby.eval("""
            Mrboto::ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])
            Mrboto::ComposeBuilder.instance_variable_set(:@_compose_root, nil)
        """.trimIndent())

        val json = mruby.eval("""
            glass_bar(blur_radius: 25.0) {
              text("content")
              text_button("Run") { }
              text_button("Save") { }
            }
            Mrboto._compose_to_json(Mrboto::ComposeBuilder.root)
        """.trimIndent())

        assertTrue(json.contains("\"type\":\"glass_bar\""))
        // children should be text_button, not glass_cell
        assertFalse(json.contains("\"glass_cell\""))
    }
}
