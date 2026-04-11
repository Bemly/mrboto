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
    }

    # Create a View and apply attributes/block
    def self.create_view(class_name, attrs = {}, &block)
      activity = Mrboto.current_activity
      return nil unless activity

      ctx_id = activity._registry_id
      view_id = Mrboto._create_view(ctx_id, class_name, attrs)
      return nil if view_id.nil? || view_id == 0

      wrapper = View.from_registry(view_id)

      # Apply attributes
      apply_attrs(wrapper, attrs)

      # Execute block for nested children or event handlers
      yield wrapper if block_given?

      wrapper
    rescue => e
      puts "Widget create_view error: #{class_name} - #{e.class}: #{e.message}"
      nil
    end

    # Apply attributes to a View wrapper
    def self.apply_attrs(view, attrs)
      return unless attrs.is_a?(Hash)

      attrs.each do |key, val|
        case key
        when :text
          view.text = val
        when :text_size
          view.text_size = val
        when :text_color
          view.text_color = val
        when :hint
          view.hint = val
        when :orientation
          view.orientation = val
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
          view.image_resource = val
        when :layout_weight
          view.layout_weight = val
        when :on_click
          view.on_click(&val) if val.respond_to?(:call)
        when :on_text_changed
          view.on_text_changed(&val) if val.respond_to?(:call)
        when :on_checked_changed
          view.on_checked_changed(&val) if val.respond_to?(:call)
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
      Mrboto._call_java_method(@_registry_id, 'setId', val)
    end

    def enabled=(val)
      Mrboto._call_java_method(@_registry_id, 'setEnabled', !!val)
    end

    def visibility=(val)
      v = case val
          when :gone then 8
          when :invisible then 4
          else 0
          end
      Mrboto._call_java_method(@_registry_id, 'setVisibility', v)
    end

    def background_color=(val)
      color = case val
              when Integer then val
              when String then val.to_i(16)
              else 0xFF000000
              end
      Mrboto._call_java_method(@_registry_id, 'setBackgroundColor', color)
    end

    def padding=(val)
      px = dp(val)
      Mrboto._call_java_method(@_registry_id, 'setPadding', px, px, px, px)
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
      Mrboto._call_java_method(@_registry_id, 'setGravity', g)
    end

    def layout_weight=(val)
      # For LinearLayout children
      Mrboto._call_java_method(@_registry_id, 'setLayoutParams',
        # This requires creating LayoutParams - simplified for now
        val)
    end

    def on_click(&block)
      cid = Mrboto.register_callback(&block)
      activity = Mrboto.current_activity
      if activity.respond_to?(:setViewClickListener)
        activity.setViewClickListener(@_registry_id, cid)
      end
    end
  end

  # ── TextView ──────────────────────────────────────────────────────
  class TextView < View
    def text=(val)
      Mrboto._call_java_method(@_registry_id, 'setText', val.to_s)
    end

    def text_size=(val)
      # setTextSize takes unit (1=SP) and size
      Mrboto._call_java_method(@_registry_id, 'setTextSize', 1, val.to_f)
    end

    def text_color=(val)
      color = case val
              when Integer then val
              when String then val.to_i(16)
              else 0xFF000000
              end
      Mrboto._call_java_method(@_registry_id, 'setTextColor', color)
    end

    def hint=(val)
      Mrboto._call_java_method(@_registry_id, 'setHint', val.to_s)
    end
  end

  # ── Button (extends TextView) ────────────────────────────────────
  class Button < TextView; end

  # ── EditText ─────────────────────────────────────────────────────
  class EditText < TextView
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
      Mrboto._call_java_method(@_registry_id, 'setImageResource', res_id)
    end
  end

  # ── ViewGroup ────────────────────────────────────────────────────
  class ViewGroup < View; end

  # ── LinearLayout ─────────────────────────────────────────────────
  class LinearLayout < ViewGroup
    def orientation=(val)
      dir = case val
            when :vertical then Mrboto::Orientation::VERTICAL
            when :horizontal then Mrboto::Orientation::HORIZONTAL
            when Integer then val
            else Mrboto::Orientation::VERTICAL
            end
      Mrboto._call_java_method(@_registry_id, 'setOrientation', dir)
    end

    def weight_sum=(val)
      Mrboto._call_java_method(@_registry_id, 'setWeightSum', val.to_f)
    end
  end

  # ── Other Containers ─────────────────────────────────────────────
  class ScrollView < ViewGroup; end
  class RelativeLayout < ViewGroup; end
  class FrameLayout < ViewGroup; end

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
end
