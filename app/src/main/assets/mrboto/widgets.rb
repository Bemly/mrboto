# mrboto/widgets.rb — Widget builders and DSL
#
# Provides top-level methods for creating Android Views programmatically:
#   linear_layout { ... }
#   text_view(text: "Hello", text_size: 24)
#   button(text: "Click") { toast("Clicked!") }
#
# Each widget supports common attributes and event handlers.

module Mrboto
  module Widgets
    # Map Ruby-style attribute names to Java setter methods
    JAVA_CLASS_MAP = {
      linear_layout:  'android.widget.LinearLayout',
      text_view:      'android.widget.TextView',
      button:         'android.widget.Button',
      edit_text:      'android.widget.EditText',
      image_view:     'android.widget.ImageView',
      scroll_view:    'android.widget.ScrollView',
      relative_layout: 'android.widget.RelativeLayout',
      check_box:      'android.widget.CheckBox',
      switch_widget:  'android.widget.Switch',
      progress_bar:   'android.widget.ProgressBar',
      spinner:        'android.widget.Spinner',
      radio_group:    'android.widget.RadioGroup',
      web_view:       'android.webkit.WebView',
      frame_layout:   'android.widget.FrameLayout',
      table_layout:   'android.widget.TableLayout',
      # Standard controls
      seek_bar:       'android.widget.SeekBar',
      rating_bar:     'android.widget.RatingBar',
      auto_complete_text_view: 'android.widget.AutoCompleteTextView',
      search_view:    'android.widget.SearchView',
      toolbar:        'androidx.appcompat.widget.Toolbar',
      number_picker:  'android.widget.NumberPicker',
      date_picker:    'android.widget.DatePicker',
      time_picker:    'android.widget.TimePicker',
      calendar_view:  'android.widget.CalendarView',
      video_view:     'android.widget.VideoView',
      chronometer:    'android.widget.Chronometer',
      text_clock:     'android.widget.TextClock',
      # Container controls
      grid_view:      'android.widget.GridView',
      list_view:      'android.widget.ListView',
      nested_scroll_view: 'androidx.core.widget.NestedScrollView',
      horizontal_scroll_view: 'android.widget.HorizontalScrollView',
      view_pager:     'androidx.viewpager.widget.ViewPager',
      tab_layout:     'com.google.android.material.tabs.TabLayout',
      view_switcher:  'android.widget.ViewSwitcher',
      # Material Design controls
      floating_action_button: 'com.google.android.material.floatingactionbutton.FloatingActionButton',
      material_button: 'com.google.android.material.button.MaterialButton',
      card_view:      'com.google.android.material.card.MaterialCardView',
      text_input_layout: 'com.google.android.material.textfield.TextInputLayout',
      text_input_edit_text: 'com.google.android.material.textfield.TextInputEditText',
      bottom_navigation_view: 'com.google.android.material.bottomnavigation.BottomNavigationView',
      app_bar_layout: 'com.google.android.material.appbar.AppBarLayout',
      # New dependency containers
      drawer_layout:  'androidx.drawerlayout.widget.DrawerLayout',
      coordinator_layout: 'androidx.coordinatorlayout.widget.CoordinatorLayout',
      navigation_view: 'com.google.android.material.navigation.NavigationView',
    }

    # Reverse map: java class name → Ruby widget name
    CLASS_TO_WIDGET = JAVA_CLASS_MAP.each_with_object({}) { |(k, v), h| h[v] = k }

    # Track the current parent ViewGroup and children during nested DSL creation
    @_view_parent_stack = []
    @_view_children = {}  # parent object_id => [child_views]

    # Create a View and apply attributes/block
    def self.create_view(class_name, attrs = {}, &block)
      activity = Mrboto.current_activity
      return nil unless activity

      ctx_id = activity._registry_id
      view_id = Mrboto._create_view(ctx_id, class_name, attrs)
      return nil if view_id.nil? || view_id == 0

      # Wrap with the correct Ruby class based on the Java class name
      widget_name = CLASS_TO_WIDGET[class_name]
      wrapper = if widget_name
                  # Convert snake_case to CamelCase without using Regexp
                  # e.g., :linear_layout -> "Linear_layout" -> "LinearLayout"
                  parts = widget_name.to_s.split('_')
                  camel = parts.map { |p| p.capitalize }.join
                  widget_class = Mrboto.const_get(camel)
                  widget_class.from_registry(view_id)
                else
                  View.from_registry(view_id)
                end

      # Apply attributes
      apply_attrs(wrapper, attrs)

      # If this is a ViewGroup and has a block, push as parent before yielding
      is_group = wrapper.is_a?(ViewGroup)
      if is_group && block_given?
        @_view_parent_stack.push(wrapper)
        @_view_children[wrapper.object_id] = []
        yield wrapper if block_given?
        children = @_view_children.delete(wrapper.object_id) || []
        children.each { |child| wrapper.add_child(child) }
        @_view_parent_stack.pop
      elsif !is_group && block_given?
        # Non-ViewGroup widgets (Button, etc.) treat block as on_click handler
        cid = Mrboto.register_callback(&block)
        Mrboto.current_activity.setViewClickListener(wrapper._registry_id, cid)
      end

      # Register this view as a child of the current parent (if any)
      if (parent = @_view_parent_stack.last)
        (@_view_children[parent.object_id] ||= []) << wrapper
      end

      wrapper
    rescue => e
      $mrboto_widget_errors ||= []
      $mrboto_widget_errors << "#{class_name}: #{e.class}: #{e.message}"
      nil
    end

    # Apply attributes to a View wrapper
    def self.apply_attrs(view, attrs)
      return unless attrs.is_a?(Hash)

      attrs.each do |key, val|
        case key
        when :text
          view.text = val if view.respond_to?(:text=)
        when :text_size
          view.text_size = val if view.respond_to?(:text_size=)
        when :text_color
          view.text_color = val if view.respond_to?(:text_color=)
        when :hint
          view.hint = val if view.respond_to?(:hint=)
        when :orientation
          view.orientation = val if view.respond_to?(:orientation=)
        when :padding
          view.padding = val
        when :background_color
          view.background_color = val
        when :gravity
          view.gravity = val
        when :id
          view.id = val
        when :enabled
          view.enabled = val
        when :visibility
          view.visibility = val
        when :image_resource
          view.image_resource = val if view.respond_to?(:image_resource=)
        when :layout_weight
          # Simplified — skip for now
        when :on_click
          view.on_click(&val) if val.respond_to?(:call)
        when :on_text_changed
          view.on_text_changed(&val) if val.respond_to?(:call)
        when :on_checked_changed
          view.on_checked_changed(&val) if val.respond_to?(:call)
        when :input_type
          view.input_type = val if view.respond_to?(:input_type=)
        when :single_line
          view.single_line = val if view.respond_to?(:single_line=)
        when :max_lines
          view.max_lines = val if view.respond_to?(:max_lines=)
        when :compound_drawable_left
          view.compound_drawable_left = val if view.respond_to?(:compound_drawable_left=)
        when :compound_drawable_right
          view.compound_drawable_right = val if view.respond_to?(:compound_drawable_right=)
        when :drawable_padding
          view.drawable_padding = val if view.respond_to?(:drawable_padding=)
        end
      end
    end
  end

  # ── View Base Class ──────────────────────────────────────────────
  class View < JavaObject
    def self.from_java(jobject)
      return nil unless jobject
      id = Mrboto._register_object(jobject)
      from_registry(id)
    end

    def id=(val)
      call_java_method('setId', val)
    end

    def enabled=(val)
      call_java_method('setEnabled', !!val)
    end

    def visibility=(val)
      v = case val
          when :gone then 8
          when :invisible then 4
          else 0
          end
      call_java_method('setVisibility', v)
    end

    def background_color=(val)
      color = case val
              when Integer then val
              when String
                v = val.to_i(16)
                # 6-digit hex needs alpha channel prepended (FF)
                v < 0x1000000 ? (0xFF000000 | v) : v
              else 0xFF000000
              end
      call_java_method('setBackgroundColor', color)
    end

    def padding=(val)
      px = dp(val)
      call_java_method('setPadding', px, px, px, px)
    end

    def gravity=(val)
      g = case val
          when Integer then val
          when :center then Mrboto::Gravity::CENTER
          when :center_vertical then Mrboto::Gravity::CENTER_VERTICAL
          when :center_horizontal then Mrboto::Gravity::CENTER_HORIZONTAL
          when :top then Mrboto::Gravity::TOP
          when :bottom then Mrboto::Gravity::BOTTOM
          when :left then Mrboto::Gravity::LEFT
          when :right then Mrboto::Gravity::RIGHT
          else 0
          end
      call_java_method('setGravity', g)
    end

    def layout_weight=(val)
      # For LinearLayout children
      call_java_method('setLayoutParams',
        # This requires creating LayoutParams - simplified for now
        val)
    end

    # No-op for non-ViewGroup views
    def add_child(child); end

    def on_click(&block)
      cid = Mrboto.register_callback(&block)
      Mrboto.current_activity.setViewClickListener(@_registry_id, cid)
    end

    # ── Animation helpers ──────────────────────────────────────────
    # These call the C bridge functions directly (not call_java_method),
    # avoiding Java reflection type mismatch issues with Float/Double.

    def fade_in(duration = 300)
      act = Mrboto.current_activity
      return if act.nil?
      Mrboto._animate_fade(act._registry_id, @_registry_id, 0.0, 1.0, duration.to_i)
      "ok"
    end

    def fade_out(duration = 300)
      act = Mrboto.current_activity
      return if act.nil?
      Mrboto._animate_fade(act._registry_id, @_registry_id, 1.0, 0.0, duration.to_i)
      "ok"
    end

    def animate_translate(from_x = 0.0, from_y = 0.0, to_x = 0.0, to_y = 0.0, duration = 300)
      act = Mrboto.current_activity
      return if act.nil?
      Mrboto._animate_translate(act._registry_id, @_registry_id,
        from_x.to_f, from_y.to_f, to_x.to_f, to_y.to_f, duration.to_i)
      "ok"
    end

    def animate_scale(from_x = 1.0, from_y = 1.0, to_x = 1.0, to_y = 1.0, duration = 300)
      act = Mrboto.current_activity
      return if act.nil?
      Mrboto._animate_scale(act._registry_id, @_registry_id,
        from_x.to_f, from_y.to_f, to_x.to_f, to_y.to_f, duration.to_i)
      "ok"
    end

    def slide_in_bottom(duration = 300)
      act = Mrboto.current_activity
      return if act.nil?
      h = call_java_method('getHeight').to_i
      Mrboto._animate_translate(act._registry_id, @_registry_id,
        0.0, h.to_f, 0.0, 0.0, duration.to_i)
      "ok"
    end

    def pulse(factor = 1.2, duration = 200)
      act = Mrboto.current_activity
      return if act.nil?
      Mrboto._animate_scale(act._registry_id, @_registry_id,
        1.0, 1.0, factor.to_f, factor.to_f, duration.to_i)
      "ok"
    end

    def clear_animation
      call_java_method('clearAnimation')
    end

    # ── Common View methods ────────────────────────────────────────
    def width
      call_java_method('getWidth').to_i
    end

    def height
      call_java_method('getHeight').to_i
    end

    def visible?
      v = call_java_method('getVisibility')
      v == 0
    end

    def show
      self.visibility = 0
    end

    def hide
      self.visibility = :gone
    end

    def request_focus
      call_java_method('requestFocus')
    end

    def perform_click
      call_java_method('performClick')
    end
  end

  # ── TextView ──────────────────────────────────────────────────────
  class TextView < View
    def text=(val)
      call_java_method('setText', val.to_s)
    end

    def text
      call_java_method('getText')
    end

    def text_size=(val)
      # setTextSize takes unit (1=SP) and size
      call_java_method('setTextSize', 1, val.to_f)
    end

    def text_color=(val)
      color = case val
              when Integer then val
              when String
                v = val.to_i(16)
                # 6-digit hex needs alpha channel prepended (FF)
                v < 0x1000000 ? (0xFF000000 | v) : v
              else 0xFF000000
              end
      call_java_method('setTextColor', color)
    end

    def hint=(val)
      call_java_method('setHint', val.to_s)
    end

    # Set compound drawable on the left side
    # val: Android resource ID (e.g., android.R.drawable.ic_menu_info_details)
    def compound_drawable_left=(res_id)
      call_java_method('setCompoundDrawablesWithIntrinsicBounds',
        res_id.to_i, 0, 0, 0)
    end

    # Set compound drawable on the right side
    def compound_drawable_right=(res_id)
      call_java_method('setCompoundDrawablesWithIntrinsicBounds',
        0, 0, res_id.to_i, 0)
    end

    # Set padding between compound drawable and text (in dp)
    def drawable_padding=(val)
      px = dp(val)
      call_java_method('setCompoundDrawablePadding', px)
    end

    # Append text without replacing existing content.
    # Tracks text in Ruby (@_text) to avoid the fragile JNI roundtrip
    # of _view_text, which can crash if the mruby VM is in an error state.
    def append_text(val)
      @_text = "" if @_text.nil?
      @_text = "#{@_text}#{val}"
      self.text = @_text
    end
  end

  # ── Button (extends TextView) ────────────────────────────────────
  class Button < TextView; end

  # ── EditText ─────────────────────────────────────────────────────
  class EditText < TextView
    def input_type=(val)
      t = case val
          when Integer then val
          when :text then 1  # TYPE_CLASS_TEXT
          when :number then 2  # TYPE_CLASS_NUMBER
          when :phone then 3   # TYPE_CLASS_PHONE
          else 1
          end
      call_java_method('setInputType', t)
    end

    def single_line=(val)
      call_java_method('setSingleLine', !!val)
    end

    def max_lines=(val)
      call_java_method('setMaxLines', val.to_i)
    end

    def on_text_changed(&block)
      cid = Mrboto.register_callback(&block)
      activity = Mrboto.current_activity
      if activity.respond_to?(:setTextWatcher)
        activity.setTextWatcher(@_registry_id, cid)
      end
    end
  end

  # ── ImageView ────────────────────────────────────────────────────
  class ImageView < View
    def image_resource=(res_id)
      call_java_method('setImageResource', res_id)
    end
  end

  # ── ViewGroup ────────────────────────────────────────────────────
  class ViewGroup < View
    def add_child(child)
      puts "add_child: adding #{child.class}(id=#{child._registry_id}) to #{self.class}(id=#{@_registry_id})"
      result = call_java_method('addView',
        Mrboto._java_object_for(child._registry_id))
      puts "add_child: addView returned #{result.inspect}"
      result
    end
  end

  # ── LinearLayout ─────────────────────────────────────────────────
  class LinearLayout < ViewGroup
    def orientation=(val)
      dir = case val
            when :vertical then Mrboto::Orientation::VERTICAL
            when :horizontal then Mrboto::Orientation::HORIZONTAL
            when Integer then val
            else Mrboto::Orientation::VERTICAL
            end
      call_java_method('setOrientation', dir)
    end

    def weight_sum=(val)
      call_java_method('setWeightSum', val.to_f)
    end
  end

  # ── Other Containers ─────────────────────────────────────────────
  class ScrollView < ViewGroup; end
  class RelativeLayout < ViewGroup; end
  class FrameLayout < ViewGroup; end
  class TableLayout < ViewGroup; end

  # ── CheckBox ─────────────────────────────────────────────────────
  class CheckBox < TextView
    def on_checked_changed(&block)
      cid = Mrboto.register_callback(&block)
      Mrboto.current_activity.setCheckListener(@_registry_id, cid)
    end
  end

  # ── SwitchWidget ─────────────────────────────────────────────────
  class SwitchWidget < TextView
    def on_checked_changed(&block)
      cid = Mrboto.register_callback(&block)
      Mrboto.current_activity.setCheckListener(@_registry_id, cid)
    end
  end

  # ── Progress ─────────────────────────────────────────────────────
  class ProgressBar < View; end
  class WebView < View; end
  class Spinner < View; end

  # ── RadioGroup ───────────────────────────────────────────────────
  class RadioGroup < ViewGroup; end

  # ── Standard Controls ────────────────────────────────────────────
  class SeekBar < View
    def progress=(val)
      call_java_method('setProgress', val.to_i)
    end

    def max=(val)
      call_java_method('setMax', val.to_i)
    end
  end

  class RatingBar < View
    def rating=(val)
      call_java_method('setRating', val.to_f)
    end

    def step_size=(val)
      call_java_method('setStepSize', val.to_f)
    end

    def max=(val)
      call_java_method('setMax', val.to_i)
    end
  end

  class AutoCompleteTextView < EditText; end

  class SearchView < View
    def query=(val)
      call_java_method('setQuery', val.to_s, false)
    end

    def hint=(val)
      call_java_method('setQueryHint', val.to_s)
    end
  end

  class Toolbar < ViewGroup
    def title=(val)
      call_java_method('setTitle', val.to_s)
    end

    def subtitle=(val)
      call_java_method('setSubtitle', val.to_s)
    end
  end

  class NumberPicker < View
    def min_value=(val)
      call_java_method('setMinValue', val.to_i)
    end

    def max_value=(val)
      call_java_method('setMaxValue', val.to_i)
    end

    def value=(val)
      call_java_method('setValue', val.to_i)
    end
  end

  class DatePicker < View
    def date=(val)
      # val: [year, month, day]
      return unless val.is_a?(Array) && val.size == 3
      call_java_method('updateDate', val[0], val[1] - 1, val[2])
    end
  end

  class TimePicker < View
    def time=(val)
      # val: [hour, minute]
      return unless val.is_a?(Array) && val.size == 2
      call_java_method('setHour', val[0])
      call_java_method('setMinute', val[1])
    end
  end

  class CalendarView < View
    def date=(val)
      # val: Time object or epoch milliseconds
      ms = val.respond_to?(:to_i) ? val.to_i * 1000 : val.to_i
      call_java_method('setDate', ms)
    end
  end

  class VideoView < View
    def video_path=(val)
      call_java_method('setVideoPath', val.to_s)
    end

    def start
      call_java_method('start')
    end

    def pause
      call_java_method('pause')
    end

    def stop
      call_java_method('stopPlayback')
    end
  end

  class Chronometer < View
    def start
      call_java_method('start')
    end

    def stop
      call_java_method('stop')
    end

    def format=(val)
      call_java_method('setFormat', val.to_s)
    end
  end

  class TextClock < View
    def format_12_hour=(val)
      call_java_method('setFormat12Hour', val.to_s)
    end

    def format_24_hour=(val)
      call_java_method('setFormat24Hour', val.to_s)
    end
  end

  # ── Additional Containers ────────────────────────────────────────
  class GridView < ViewGroup
    def num_columns=(val)
      call_java_method('setNumColumns', val.to_i)
    end
  end

  class ListView < ViewGroup; end

  class NestedScrollView < ViewGroup; end

  class HorizontalScrollView < ViewGroup; end

  class ViewPager < ViewGroup
    def current_item=(val)
      call_java_method('setCurrentItem', val.to_i)
    end
  end

  class TabLayout < ViewGroup
    def selected_tab=(val)
      call_java_method('setScrollPosition', val.to_i, 0, true)
    end

    def tab_mode=(val)
      mode = case val
             when :fixed then 1  # MODE_FIXED
             when :scrollable then 0  # MODE_SCROLLABLE
             else 0
             end
      call_java_method('setTabMode', mode)
    end

    def tab_gravity=(val)
      g = case val
          when :fill then 1  # GRAVITY_FILL
          when :center then 0  # GRAVITY_CENTER
          when :auto then -1  # GRAVITY_AUTO
          else -1
          end
      call_java_method('setTabGravity', g)
    end
  end

  class ViewSwitcher < ViewGroup
    def show_next
      call_java_method('showNext')
    end

    def show_previous
      call_java_method('showPrevious')
    end
  end

  # ── Material Design Controls ─────────────────────────────────────
  class FloatingActionButton < View
    def src=(res_id)
      call_java_method('setImageResource', res_id)
    end

    def ripple_color=(val)
      color = case val
              when Integer then val
              when String
                v = val.to_i(16)
                v < 0x1000000 ? (0xFF000000 | v) : v
              else 0xFF000000
              end
      call_java_method('setRippleColorResource', color)
    end
  end

  class MaterialButton < Button
    def icon=(res_id)
      call_java_method('setIconResource', res_id)
    end

    def ripple_color=(val)
      color = case val
              when Integer then val
              when String
                v = val.to_i(16)
                v < 0x1000000 ? (0xFF000000 | v) : v
              else 0xFF000000
              end
      call_java_method('setRippleColorResource', color)
    end
  end

  class CardView < ViewGroup
    def card_elevation=(val)
      px = dp(val)
      call_java_method('setCardElevation', px)
    end

    def card_corner_radius=(val)
      px = dp(val)
      call_java_method('setRadius', px)
    end
  end

  class TextInputLayout < ViewGroup
    def hint=(val)
      call_java_method('setHint', val.to_s)
    end

    def error=(val)
      call_java_method('setError', val.to_s)
    end
  end

  class TextInputEditText < EditText; end

  class BottomNavigationView < ViewGroup
    def selected_item=(val)
      call_java_method('setSelectedItemId', val.to_i)
    end
  end

  class AppBarLayout < ViewGroup; end

  # ── New Dependency Containers ────────────────────────────────────
  class DrawerLayout < ViewGroup; end

  class CoordinatorLayout < ViewGroup; end

  class NavigationView < ViewGroup
    def menu_item=(res_id)
      call_java_method('inflateMenu', res_id)
    end
  end

  # ── Top-level DSL Methods ────────────────────────────────────────
  # These make widget creation natural: linear_layout { ... }

  module DSL
    Widgets::JAVA_CLASS_MAP.each do |dsl_name, java_class|
      define_method(dsl_name) do |attrs = {}, &block|
        Mrboto::Widgets.create_view(java_class, attrs, &block)
      end
    end
  end

  include DSL

  # Also make DSL methods available on Activity instances
  Mrboto::Activity.include(DSL)
end
