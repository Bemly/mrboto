package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for the widget DSL: widget creation, attributes,
 * nested widgets, and event callbacks.
 */
class WidgetsTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    private fun setupActivity() {
        val ctxId = mruby.registerJavaObject(mrbotoRule.context)
        val actId = mruby.registerJavaObject(mrbotoRule.context)
        mruby.eval("Mrboto.current_activity_id = $actId")
        mruby.eval("Mrboto._test_ctx_id = $ctxId")
        mruby.eval("""
            class TestActivity < Mrboto::Activity
              def on_create(bundle)
                super
              end
            end
            act = TestActivity.new(Mrboto.current_activity_id)
            Mrboto.current_activity = act
            Mrboto.current_activity.on_create(nil)
        """.trimIndent())
    }

    @Test
    fun `JAVA_CLASS_MAP_has_expected_widgets`() {
        val result = mruby.eval("Mrboto::Widgets::JAVA_CLASS_MAP.size")
        val size = result.toIntOrNull()
        assertTrue("Should have widget entries", size != null && size >= 10)
    }

    @Test
    fun `CLASS_TO_WIDGET_reverse_map_exists`() {
        val result = mruby.eval("Mrboto::Widgets::CLASS_TO_WIDGET.size")
        val size = result.toIntOrNull()
        assertTrue("Should have reverse map entries", size != null && size > 0)
    }

    @Test
    fun `View_class_exists`() {
        assertEquals("true", mruby.eval("Mrboto.const_defined?(:View).to_s"))
    }

    @Test
    fun `TextView_is_subclass_of_View`() {
        assertEquals("true", mruby.eval("(Mrboto::TextView < Mrboto::View).to_s"))
    }

    @Test
    fun `Button_is_subclass_of_TextView`() {
        assertEquals("true", mruby.eval("(Mrboto::Button < Mrboto::TextView).to_s"))
    }

    @Test
    fun `EditText_is_subclass_of_TextView`() {
        assertEquals("true", mruby.eval("(Mrboto::EditText < Mrboto::TextView).to_s"))
    }

    @Test
    fun `ImageView_exists_and_is_View_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::ImageView < Mrboto::View).to_s"))
    }

    @Test
    fun `LinearLayout_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::LinearLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `ScrollView_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::ScrollView < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `RelativeLayout_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::RelativeLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `FrameLayout_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::FrameLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `View_responds_to_common_attribute_setters`() {
        setupActivity()
        val setters = listOf("id=", "enabled=", "visibility=", "background_color=", "padding=", "gravity=")
        for (setter in setters) {
            val result = mruby.eval("Mrboto::View.instance_methods.include?(:$setter).to_s")
            assertEquals("View should respond to $setter", "true", result)
        }
    }

    @Test
    fun `TextView_responds_to_text_setters`() {
        val setters = listOf("text=", "text_size=", "text_color=", "hint=")
        for (s in setters) {
            val result = mruby.eval("Mrboto::TextView.instance_methods.include?(:$s).to_s")
            assertEquals("TextView should respond to $s", "true", result)
        }
    }

    @Test
    fun `View_from_registry_with_nil_returns_nil`() {
        assertEquals("true", mruby.eval("Mrboto::View.from_registry(nil).nil?.to_s"))
    }

    @Test
    fun `View_from_registry_with_0_returns_nil`() {
        assertEquals("true", mruby.eval("Mrboto::View.from_registry(0).nil?.to_s"))
    }

    @Test
    fun `View_from_registry_with_valid_ID_returns_View`() {
        val id = mruby.registerJavaObject(Any())
        val result = mruby.eval("Mrboto::View.from_registry($id).class.to_s")
        assertEquals("Mrboto::View", result)
    }

    @Test
    fun `visibility_accepts_gone_invisible_and_default`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        mruby.eval("""
            v = Mrboto::View.from_registry($ctxId)
            v.visibility = :gone
            v.visibility = :invisible
            v.visibility = 0
            'ok'
        """.trimIndent())
    }

    @Test
    fun `background_color_accepts_hex_string`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            v = Mrboto::View.from_registry($ctxId)
            v.background_color = "FF0000"
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `gravity_accepts_common_symbols`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val gravities = listOf("center", "center_vertical", "center_horizontal", "top", "bottom", "left", "right")
        for (g in gravities) {
            val result = mruby.eval("""
                v = Mrboto::View.from_registry($ctxId)
                v.gravity = :$g
                'ok'
            """.trimIndent())
            assertEquals("ok on $g", "ok", result)
        }
    }

    @Test
    fun `padding_applies_dp_conversion`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            v = Mrboto::View.from_registry($ctxId)
            v.padding = 16
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `enabled_setter_works`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            v = Mrboto::View.from_registry($ctxId)
            v.enabled = false
            v.enabled = true
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `LinearLayout_responds_to_orientation`() {
        assertEquals("true", mruby.eval("Mrboto::LinearLayout.instance_methods.include?(:orientation=).to_s"))
    }

    @Test
    fun `create_view_returns_positive_registry_ID`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.TextView', {})")
        val id = result.toIntOrNull()
        assertNotNull("Should return an integer, got: $result", id)
        assertTrue("View ID should be positive, got: $id", id!! > 0)
    }

    @Test
    fun `apply_attrs_does_not_crash_on_unknown_attr`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val viewId = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.TextView', {})")
        val result = mruby.eval("""
            view = Mrboto::TextView.from_registry($viewId)
            Mrboto::Widgets.apply_attrs(view, { unknown_attr: "test" })
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    // ── New Widget Class Hierarchy Tests ──────────────────────────

    // Standard Controls
    @Test
    fun `SeekBar_is_View_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::SeekBar < Mrboto::View).to_s"))
    }

    @Test
    fun `RatingBar_is_View_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::RatingBar < Mrboto::View).to_s"))
    }

    @Test
    fun `AutoCompleteTextView_is_EditText_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::AutoCompleteTextView < Mrboto::EditText).to_s"))
    }

    @Test
    fun `SearchView_is_View_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::SearchView < Mrboto::View).to_s"))
    }

    @Test
    fun `Toolbar_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::Toolbar < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `NumberPicker_is_View_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::NumberPicker < Mrboto::View).to_s"))
    }

    @Test
    fun `DatePicker_is_View_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::DatePicker < Mrboto::View).to_s"))
    }

    @Test
    fun `TimePicker_is_View_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::TimePicker < Mrboto::View).to_s"))
    }

    @Test
    fun `CalendarView_is_View_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::CalendarView < Mrboto::View).to_s"))
    }

    @Test
    fun `VideoView_is_View_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::VideoView < Mrboto::View).to_s"))
    }

    @Test
    fun `Chronometer_is_View_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::Chronometer < Mrboto::View).to_s"))
    }

    @Test
    fun `TextClock_is_View_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::TextClock < Mrboto::View).to_s"))
    }

    // Container Controls
    @Test
    fun `GridView_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::GridView < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `ListView_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::ListView < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `NestedScrollView_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::NestedScrollView < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `HorizontalScrollView_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::HorizontalScrollView < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `ViewPager_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::ViewPager < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `TabLayout_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::TabLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `ViewSwitcher_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::ViewSwitcher < Mrboto::ViewGroup).to_s"))
    }

    // Material Design Controls
    @Test
    fun `FloatingActionButton_is_View_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::FloatingActionButton < Mrboto::View).to_s"))
    }

    @Test
    fun `MaterialButton_is_Button_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::MaterialButton < Mrboto::Button).to_s"))
    }

    @Test
    fun `CardView_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::CardView < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `TextInputLayout_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::TextInputLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `TextInputEditText_is_EditText_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::TextInputEditText < Mrboto::EditText).to_s"))
    }

    @Test
    fun `BottomNavigationView_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::BottomNavigationView < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `AppBarLayout_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::AppBarLayout < Mrboto::ViewGroup).to_s"))
    }

    // New Dependency Containers
    @Test
    fun `DrawerLayout_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::DrawerLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `CoordinatorLayout_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::CoordinatorLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `NavigationView_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::NavigationView < Mrboto::ViewGroup).to_s"))
    }

    // ── New Widget Method Existence Tests ─────────────────────────

    @Test
    fun `SeekBar_has_progress_and_max_setters`() {
        val methods = listOf("progress=", "max=")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::SeekBar.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `RatingBar_has_rating_step_size_and_max_setters`() {
        val methods = listOf("rating=", "step_size=", "max=")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::RatingBar.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `SearchView_has_query_and_hint_setters`() {
        val methods = listOf("query=", "hint=")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::SearchView.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `Toolbar_has_title_and_subtitle_setters`() {
        val methods = listOf("title=", "subtitle=")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::Toolbar.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `NumberPicker_has_value_setters`() {
        val methods = listOf("min_value=", "max_value=", "value=")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::NumberPicker.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `VideoView_has_playback_methods`() {
        val methods = listOf("start", "pause", "stop")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::VideoView.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `Chronometer_has_start_and_stop_methods`() {
        val methods = listOf("start", "stop")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::Chronometer.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `TextClock_has_format_setters`() {
        val methods = listOf("format_12_hour=", "format_24_hour=")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::TextClock.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `CardView_has_elevation_and_radius_setters`() {
        val methods = listOf("card_elevation=", "card_corner_radius=")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::CardView.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `TextInputLayout_has_hint_and_error_setters`() {
        val methods = listOf("hint=", "error=")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::TextInputLayout.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `GridView_has_num_columns_setter`() {
        assertEquals("true", mruby.eval("Mrboto::GridView.instance_methods.include?(:num_columns=).to_s"))
    }

    @Test
    fun `ViewPager_has_current_item_setter`() {
        assertEquals("true", mruby.eval("Mrboto::ViewPager.instance_methods.include?(:current_item=).to_s"))
    }

    @Test
    fun `ViewSwitcher_has_show_methods`() {
        val methods = listOf("show_next", "show_previous")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::ViewSwitcher.instance_methods.include?(:$m).to_s"))
        }
    }

    // ── Functional Tests (no crash on setter calls) ──────────────

    @Test
    fun `SeekBar_setters_do_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            v = Mrboto::SeekBar.from_registry($ctxId)
            v.progress = 50
            v.max = 100
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `RatingBar_setters_do_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            v = Mrboto::RatingBar.from_registry($ctxId)
            v.rating = 3.5
            v.step_size = 0.5
            v.max = 5
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `NumberPicker_setters_do_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            v = Mrboto::NumberPicker.from_registry($ctxId)
            v.min_value = 0
            v.max_value = 10
            v.value = 5
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `TextClock_setters_do_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            v = Mrboto::TextClock.from_registry($ctxId)
            v.format_12_hour = "hh:mm a"
            v.format_24_hour = "HH:mm"
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `GridView_setter_does_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            v = Mrboto::GridView.from_registry($ctxId)
            v.num_columns = 3
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `CardView_setters_do_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            v = Mrboto::CardView.from_registry($ctxId)
            v.card_elevation = 4
            v.card_corner_radius = 8
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `TextInputLayout_setters_do_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            v = Mrboto::TextInputLayout.from_registry($ctxId)
            v.hint = "Enter text"
            v.error = "Invalid"
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `BottomNavigationView_setter_does_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            v = Mrboto::BottomNavigationView.from_registry($ctxId)
            v.selected_item = 1
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `ViewSwitcher_methods_do_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            v = Mrboto::ViewSwitcher.from_registry($ctxId)
            v.show_next
            v.show_previous
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `Chronometer_methods_do_not_crash`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            v = Mrboto::Chronometer.from_registry($ctxId)
            v.start
            v.stop
            v.format = "%s"
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `JAVA_CLASS_MAP_has_all_expected_widgets`() {
        val expectedMin = 40 // 15 original + ~29 new
        val result = mruby.eval("Mrboto::Widgets::JAVA_CLASS_MAP.size")
        val size = result.toIntOrNull()
        assertTrue("Should have at least $expectedMin widget entries, got $size", size != null && size >= expectedMin)
    }

    // ── Compound Drawable (icon) Tests ──────────────────────────────

    @Test
    fun `TextView_has_compound_drawable_left_setter`() {
        assertEquals("true", mruby.eval("Mrboto::TextView.instance_methods.include?(:compound_drawable_left=).to_s"))
    }

    @Test
    fun `TextView_has_compound_drawable_right_setter`() {
        assertEquals("true", mruby.eval("Mrboto::TextView.instance_methods.include?(:compound_drawable_right=).to_s"))
    }

    @Test
    fun `TextView_has_drawable_padding_setter`() {
        assertEquals("true", mruby.eval("Mrboto::TextView.instance_methods.include?(:drawable_padding=).to_s"))
    }

    @Test
    fun `compound_drawable_left_does_not_crash_with_system_icon_id`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            sys_id = Mrboto._get_sys_res_id(0, 'ic_menu_help', 'drawable')
            v = Mrboto::TextView.from_registry($ctxId)
            v.compound_drawable_left = sys_id
            v.drawable_padding = 8
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun `compound_drawable_right_does_not_crash_with_system_icon_id`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("""
            sys_id = Mrboto._get_sys_res_id(0, 'ic_menu_info_details', 'drawable')
            v = Mrboto::TextView.from_registry($ctxId)
            v.compound_drawable_right = sys_id
            v.drawable_padding = 8
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }
}
