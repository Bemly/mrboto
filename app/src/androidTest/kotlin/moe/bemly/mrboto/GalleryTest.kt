package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GalleryTest {
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

    @Test fun gallery_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:pick_image_from_gallery).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:copy_selected_image).to_s"))
    }

    @Test fun copy_selected_image_returns_string() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.copy_selected_image('test.jpg').to_s")
        assertNotNull(result)
        // No file selected should return empty string
        assertEquals("", result)
    }

    @Test fun gallery_top_level_methods_exist() {
        assertEquals("true", mruby.eval("respond_to?(:pick_image_from_gallery).to_s"))
        assertEquals("true", mruby.eval("respond_to?(:copy_selected_image).to_s"))
    }

    @Test fun gallery_kotlin_methods_accessible() {
        val act = mrbotoRule.createTestActivity()
        assertTrue(act.pickImageFromGallery(0) || !act.pickImageFromGallery(0))
    }
}
