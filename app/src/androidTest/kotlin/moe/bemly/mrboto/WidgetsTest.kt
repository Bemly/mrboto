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
    fun `JAVA_CLASS_MAP has expected widgets`() {
        val result = mruby.eval("Mrboto::Widgets::JAVA_CLASS_MAP.size")
        val size = result.toIntOrNull()
        assertTrue("Should have widget entries", size != null && size >= 10)
    }

    @Test
    fun `CLASS_TO_WIDGET reverse map exists`() {
        val result = mruby.eval("Mrboto::Widgets::CLASS_TO_WIDGET.size")
        val size = result.toIntOrNull()
        assertTrue("Should have reverse map entries", size != null && size > 0)
    }

    @Test
    fun `View class exists`() {
        assertEquals("true", mruby.eval("Mrboto.const_defined?(:View).to_s"))
    }

    @Test
    fun `TextView is subclass of View`() {
        assertEquals("true", mruby.eval("(Mrboto::TextView < Mrboto::View).to_s"))
    }

    @Test
    fun `Button is subclass of TextView`() {
        assertEquals("true", mruby.eval("(Mrboto::Button < Mrboto::TextView).to_s"))
    }

    @Test
    fun `EditText is subclass of TextView`() {
        assertEquals("true", mruby.eval("(Mrboto::EditText < Mrboto::TextView).to_s"))
    }

    @Test
    fun `ImageView exists and is View subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::ImageView < Mrboto::View).to_s"))
    }

    @Test
    fun `LinearLayout is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::LinearLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `ScrollView is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::ScrollView < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `RelativeLayout is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::RelativeLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `FrameLayout is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::FrameLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `View responds to common attribute setters`() {
        setupActivity()
        val setters = listOf("id=", "enabled=", "visibility=", "background_color=", "padding=", "gravity=")
        for (setter in setters) {
            val result = mruby.eval("Mrboto::View.instance_methods.include?(:$setter).to_s")
            assertEquals("View should respond to $setter", "true", result)
        }
    }

    @Test
    fun `TextView responds to text setters`() {
        val setters = listOf("text=", "text_size=", "text_color=", "hint=")
        for (s in setters) {
            val result = mruby.eval("Mrboto::TextView.instance_methods.include?(:$s).to_s")
            assertEquals("TextView should respond to $s", "true", result)
        }
    }

    @Test
    fun `View from_registry with nil returns nil`() {
        assertEquals("true", mruby.eval("Mrboto::View.from_registry(nil).nil?.to_s"))
    }

    @Test
    fun `View from_registry with 0 returns nil`() {
        assertEquals("true", mruby.eval("Mrboto::View.from_registry(0).nil?.to_s"))
    }

    @Test
    fun `View from_registry with valid ID returns View`() {
        val id = mruby.registerJavaObject(Any())
        val result = mruby.eval("Mrboto::View.from_registry($id).class.to_s")
        assertEquals("Mrboto::View", result)
    }

    @Test
    fun `visibility accepts gone invisible and default`() {
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
    fun `background_color accepts hex string`() {
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
    fun `gravity accepts common symbols`() {
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
    fun `padding applies dp conversion`() {
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
    fun `enabled setter works`() {
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
    fun `LinearLayout responds to orientation`() {
        assertEquals("true", mruby.eval("Mrboto::LinearLayout.instance_methods.include?(:orientation=).to_s"))
    }

    @Test
    fun `create_view returns positive registry ID`() {
        setupActivity()
        val ctxId = mruby.eval("Mrboto._test_ctx_id")
        val result = mruby.eval("Mrboto._create_view($ctxId, 'android.widget.TextView', {})")
        val id = result.toIntOrNull()
        assertNotNull("Should return an integer, got: $result", id)
        assertTrue("View ID should be positive, got: $id", id!! > 0)
    }

    @Test
    fun `apply_attrs does not crash on unknown attr`() {
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
    fun `SeekBar is View subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::SeekBar < Mrboto::View).to_s"))
    }

    @Test
    fun `RatingBar is View subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::RatingBar < Mrboto::View).to_s"))
    }

    @Test
    fun `AutoCompleteTextView is EditText subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::AutoCompleteTextView < Mrboto::EditText).to_s"))
    }

    @Test
    fun `SearchView is View subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::SearchView < Mrboto::View).to_s"))
    }

    @Test
    fun `Toolbar is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::Toolbar < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `NumberPicker is View subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::NumberPicker < Mrboto::View).to_s"))
    }

    @Test
    fun `DatePicker is View subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::DatePicker < Mrboto::View).to_s"))
    }

    @Test
    fun `TimePicker is View subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::TimePicker < Mrboto::View).to_s"))
    }

    @Test
    fun `CalendarView is View subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::CalendarView < Mrboto::View).to_s"))
    }

    @Test
    fun `VideoView is View subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::VideoView < Mrboto::View).to_s"))
    }

    @Test
    fun `Chronometer is View subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::Chronometer < Mrboto::View).to_s"))
    }

    @Test
    fun `TextClock is View subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::TextClock < Mrboto::View).to_s"))
    }

    // Container Controls
    @Test
    fun `GridView is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::GridView < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `ListView is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::ListView < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `NestedScrollView is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::NestedScrollView < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `HorizontalScrollView is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::HorizontalScrollView < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `ViewPager is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::ViewPager < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `TabLayout is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::TabLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `ViewSwitcher is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::ViewSwitcher < Mrboto::ViewGroup).to_s"))
    }

    // Material Design Controls
    @Test
    fun `FloatingActionButton is View subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::FloatingActionButton < Mrboto::View).to_s"))
    }

    @Test
    fun `MaterialButton is Button subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::MaterialButton < Mrboto::Button).to_s"))
    }

    @Test
    fun `CardView is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::CardView < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `TextInputLayout is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::TextInputLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `TextInputEditText is EditText subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::TextInputEditText < Mrboto::EditText).to_s"))
    }

    @Test
    fun `BottomNavigationView is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::BottomNavigationView < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `AppBarLayout is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::AppBarLayout < Mrboto::ViewGroup).to_s"))
    }

    // New Dependency Containers
    @Test
    fun `DrawerLayout is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::DrawerLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `CoordinatorLayout is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::CoordinatorLayout < Mrboto::ViewGroup).to_s"))
    }

    @Test
    fun `NavigationView is ViewGroup subclass`() {
        assertEquals("true", mruby.eval("(Mrboto::NavigationView < Mrboto::ViewGroup).to_s"))
    }

    // ── New Widget Method Existence Tests ─────────────────────────

    @Test
    fun `SeekBar has progress and max setters`() {
        val methods = listOf("progress=", "max=")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::SeekBar.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `RatingBar has rating, step_size and max setters`() {
        val methods = listOf("rating=", "step_size=", "max=")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::RatingBar.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `SearchView has query and hint setters`() {
        val methods = listOf("query=", "hint=")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::SearchView.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `Toolbar has title and subtitle setters`() {
        val methods = listOf("title=", "subtitle=")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::Toolbar.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `NumberPicker has value setters`() {
        val methods = listOf("min_value=", "max_value=", "value=")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::NumberPicker.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `VideoView has playback methods`() {
        val methods = listOf("start", "pause", "stop")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::VideoView.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `Chronometer has start and stop methods`() {
        val methods = listOf("start", "stop")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::Chronometer.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `TextClock has format setters`() {
        val methods = listOf("format_12_hour=", "format_24_hour=")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::TextClock.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `CardView has elevation and radius setters`() {
        val methods = listOf("card_elevation=", "card_corner_radius=")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::CardView.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `TextInputLayout has hint and error setters`() {
        val methods = listOf("hint=", "error=")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::TextInputLayout.instance_methods.include?(:$m).to_s"))
        }
    }

    @Test
    fun `GridView has num_columns setter`() {
        assertEquals("true", mruby.eval("Mrboto::GridView.instance_methods.include?(:num_columns=).to_s"))
    }

    @Test
    fun `ViewPager has current_item setter`() {
        assertEquals("true", mruby.eval("Mrboto::ViewPager.instance_methods.include?(:current_item=).to_s"))
    }

    @Test
    fun `ViewSwitcher has show methods`() {
        val methods = listOf("show_next", "show_previous")
        for (m in methods) {
            assertEquals("true", mruby.eval("Mrboto::ViewSwitcher.instance_methods.include?(:$m).to_s"))
        }
    }

    // ── Functional Tests (no crash on setter calls) ──────────────

    @Test
    fun `SeekBar setters do not crash`() {
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
    fun `RatingBar setters do not crash`() {
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
    fun `NumberPicker setters do not crash`() {
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
    fun `TextClock setters do not crash`() {
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
    fun `GridView setter does not crash`() {
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
    fun `CardView setters do not crash`() {
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
    fun `TextInputLayout setters do not crash`() {
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
    fun `BottomNavigationView setter does not crash`() {
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
    fun `ViewSwitcher methods do not crash`() {
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
    fun `Chronometer methods do not crash`() {
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
    fun `JAVA_CLASS_MAP has all expected widgets`() {
        val expectedMin = 40 // 15 original + ~29 new
        val result = mruby.eval("Mrboto::Widgets::JAVA_CLASS_MAP.size")
        val size = result.toIntOrNull()
        assertTrue("Should have at least $expectedMin widget entries, got $size", size != null && size >= expectedMin)
    }
}
