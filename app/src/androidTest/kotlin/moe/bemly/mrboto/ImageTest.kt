package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ImageTest {
    @get:Rule val mrbotoRule = MrbotoTestRule()
    private val mruby get() = mrbotoRule.mruby

    private fun setupActivity() {
        val act = mrbotoRule.createTestActivity()
        val actId = mruby.registerJavaObject(act)
        mruby.eval("Mrboto.current_activity_id = $actId")
        mruby.eval("""
            class TestActivity < Mrboto::Activity; def on_create(bundle); super; end; end
            Mrboto.current_activity = TestActivity.new(Mrboto.current_activity_id)
            Mrboto.current_activity.on_create(nil)
        """.trimIndent())
    }

    @Test fun image_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:image_crop).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:image_scale).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:image_rotate).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:image_to_base64).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:image_from_base64).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:image_pixel_color).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:image_width).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:image_height).to_s"))
    }

    @Test fun image_width_invalid_path_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.image_width('/nonexistent.png').to_s")
        assertNotNull(result)
    }

    @Test fun image_height_invalid_path_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.image_height('/nonexistent.png').to_s")
        assertNotNull(result)
    }
}
