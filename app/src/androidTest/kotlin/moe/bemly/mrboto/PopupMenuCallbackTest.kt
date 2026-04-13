package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for PopupMenu callback dispatch: dispatch_callback with popup menu item args.
 */
class PopupMenuCallbackTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    private fun setupActivity() {
        val actId = mruby.registerJavaObject(mrbotoRule.context)
        mruby.eval("Mrboto.current_activity_id = $actId")
        mruby.eval("""
            class TestPopupActivity < Mrboto::Activity
              def on_create(bundle); super; end
            end
            Mrboto.current_activity = TestPopupActivity.new(Mrboto.current_activity_id)
            Mrboto.current_activity.on_create(nil)
        """.trimIndent())
    }

    @Test
    fun `dispatch_callback_with_popup_menu_args_records_index_and_title`() {
        setupActivity()
        mruby.eval("""
            @popup_index = nil
            @popup_title = nil
            Mrboto.register_callback { |index, title|
                @popup_index = index
                @popup_title = title
            }
        """.trimIndent())
        // Simulate the Kotlin-side dispatch that happens on item click
        mruby.eval("Mrboto.dispatch_callback(1, 0, 'Item 1')")
        assertEquals("0", mruby.eval("@popup_index.to_s"))
        assertEquals("Item 1", mruby.eval("@popup_title"))
    }

    @Test
    fun `dispatch_callback_passes_numeric_item_id`() {
        setupActivity()
        mruby.eval("""
            @item_id = nil
            Mrboto.register_callback { |id, title| @item_id = id }
            Mrboto.dispatch_callback(1, 2, 'Second Item')
        """.trimIndent())
        assertEquals("2", mruby.eval("@item_id.to_s"))
    }

    @Test
    fun `dispatch_callback_with_nil_title_handles_gracefully`() {
        setupActivity()
        mruby.eval("""
            @title = "unset"
            Mrboto.register_callback { |id, t| @title = t }
            Mrboto.dispatch_callback(1, 0)
        """.trimIndent())
        // nil is passed for title, callback receives nil — @title.nil? is true
        assertEquals("true", mruby.eval("@title.nil?.to_s"))
    }
}
