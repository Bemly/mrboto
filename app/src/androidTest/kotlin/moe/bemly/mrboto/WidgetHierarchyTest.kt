package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Tests for widget class hierarchy and method existence.
 */
class WidgetHierarchyTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    // ── Standard Controls ────────────────────────────────────────

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

    // ── Container Controls ───────────────────────────────────────

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

    // ── Material Design Controls ─────────────────────────────────

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

    // ── Dependency Containers ────────────────────────────────────

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

    // ── Other Widgets ────────────────────────────────────────────

    @Test
    fun `CheckBox_is_TextView_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::CheckBox < Mrboto::TextView).to_s"))
    }

    @Test
    fun `SwitchWidget_is_TextView_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::SwitchWidget < Mrboto::TextView).to_s"))
    }

    @Test
    fun `ProgressBar_is_View_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::ProgressBar < Mrboto::View).to_s"))
    }

    @Test
    fun `TableLayout_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::TableLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `Spinner_is_View_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::Spinner < Mrboto::View).to_s"))
    }

    @Test
    fun `RadioGroup_is_ViewGroup_subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::RadioGroup < Mrboto::ViewGroup).to_s"))
    }

    // ── Method Existence Tests ───────────────────────────────────

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
    fun `ViewSwitcher_has_show_methods`() {
        val methods = listOf("show_next", "show_previous")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::ViewSwitcher.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `CheckBox_has_on_checked_changed_method`() {
        assertEquals("true", mruby.eval("Mrboto::CheckBox.instance_methods.include?(:on_checked_changed).to_s"))
    }

    @Test
    fun `SwitchWidget_has_on_checked_changed_method`() {
        assertEquals("true", mruby.eval("Mrboto::SwitchWidget.instance_methods.include?(:on_checked_changed).to_s"))
    }

    // ── Widget Count ─────────────────────────────────────────────

    @Test
    fun `JAVA_CLASS_MAP_has_all_expected_widgets`() {
        val expectedMin = 40
        val result = mruby.eval("Mrboto::Widgets::JAVA_CLASS_MAP.size")
        val size = result.toIntOrNull()
        assertTrue("Should have at least $expectedMin widget entries, got $size", size != null && size >= expectedMin)
    }
}
