# mrboto/compose.rb — Jetpack Compose DSL for mrboto
#
# Provides a Ruby DSL for building Compose UI trees:
#   column {
#     text("Hello Compose!", font_size: 24)
#     button("Click") { toast("Clicked!") }
#   }
#   set_compose_content

module Mrboto
  # ── ComposeActivity — base class for Compose-backed Ruby Activities ──
  class ComposeActivity < JavaObject
    def on_create(bundle = nil); end
    def on_start; end
    def on_resume; end
    def on_pause; end
    def on_stop; end
    def on_destroy; end
    def on_restart; end
    def on_post_create(bundle = nil); end

    def set_title(text)
      call_java_method("setTitle", text.to_s)
    end

    def title=(text)
      set_title(text)
    end

    def find_view_by_id(id)
      view = call_java_method("findViewById", id)
      view ? View.from_java(view) : nil
    end
  end

  # ── Compose Node Builder — internal tree construction ──
  module ComposeBuilder
    class << self
      # Stack of parent hashes for nested children building.
      # The root node stays on the stack permanently until cleared.
      def stack
        @_compose_parent_stack ||= []
      end

      # Store the root node reference for set_compose_content
      def root
        stack.first
      end

      # Push a node onto the current parent, or make it root
      def add_node(node)
        if stack.empty?
          stack.push(node)
        else
          parent = stack.last
          kids = parent["children"]
          if kids.nil?
            kids = []
            parent["children"] = kids
          end
          kids << node
        end
        node
      end

      # Enter a parent context (for nested children)
      # Pushes node only if it's not already the current stack top
      def with_parent(node)
        is_top = stack.last == node
        stack.push(node) unless is_top
        yield if block_given?
        stack.pop unless is_top
        node
      end

      # Internal: collect child nodes from a block
      # Returns the single child directly (not a wrapper) when there's exactly one.
      def collect_nodes
        wrapper = { "children" => [] }
        stack.push(wrapper)
        yield
        stack.pop
        kids = wrapper["children"]
        kids.size == 1 ? kids[0] : nil
      end
    end
  end

  # ── Helper: build modifier array ──
  def self._compose_modifiers(**kwargs)
    mods = []
    kwargs.each do |key, val|
      case key
      when :modifier
        # Already an array from modifier() calls
        mods.concat(val) if val.is_a?(Array)
      end
    end
    mods
  end

  # Modifier chain builder
  class ComposeModifier
    def initialize
      @mods = []
    end

    def padding(val)
      @mods << { "type" => "padding", "value" => val }
      self
    end

    def fill_max_width(val = 1.0)
      @mods << { "type" => "fill_max_width", "value" => val }
      self
    end

    def fill_max_height(val = 1.0)
      @mods << { "type" => "fill_max_height", "value" => val }
      self
    end

    def weight(val = 1.0)
      @mods << { "type" => "weight", "value" => val }
      self
    end

    def width(val)
      @mods << { "type" => "width", "value" => val }
      self
    end

    def height(val)
      @mods << { "type" => "height", "value" => val }
      self
    end

    def background(val)
      @mods << { "type" => "background", "value" => val }
      self
    end

    def align(val)
      @mods << { "type" => "align", "value" => val.to_s }
      self
    end

    def aspect_ratio(val)
      @mods << { "type" => "aspect_ratio", "value" => val }
      self
    end

    def clip
      @mods << { "type" => "clip" }
      self
    end

    def to_a
      @mods
    end

    def then(other)
      @mods.concat(other.to_a) if other.is_a?(ComposeModifier)
      self
    end
  end

  def modifier
    ComposeModifier.new
  end

  def padding(val)
    modifier.padding(val)
  end

  def fill_max_width(val = 1.0)
    ComposeModifier.new.fill_max_width(val)
  end

  def fill_max_height(val = 1.0)
    ComposeModifier.new.fill_max_height(val)
  end

  def weight(val = 1.0)
    ComposeModifier.new.weight(val)
  end

  def width(val)
    ComposeModifier.new.width(val)
  end

  def height(val)
    ComposeModifier.new.height(val)
  end

  def background_color(val)
    ComposeModifier.new.background(val)
  end

  def align(val)
    ComposeModifier.new.align(val)
  end

  # ── Color helper ──
  def color(hex)
    hex.to_s
  end

  # ── Build a ComposableNode and add to tree ──
  def self._build_compose_node(type, content = nil, props = {}, callback_id = 0, &block)
    node = {
      "type" => type.to_s,
      "props" => props,
      "children" => [],
    }
    node["content"] = content.to_s if content
    node["callback_id"] = callback_id if callback_id > 0

    # Register click callback if block given for interactive nodes
    if block_given? && %w[button text_button floating_action_button icon_button].include?(type.to_s)
      cid = Mrboto.register_callback(&block)
      node["callback_id"] = cid
    end

    # Yield block content for non-interactive nodes
    if block_given? && !%w[button text_button floating_action_button icon_button].include?(type.to_s)
      ComposeBuilder.with_parent(node) { yield nil }
    end

    ComposeBuilder.add_node(node)
    node
  end

  # ── Layouts ──
  def column(vertical_arrangement: nil, horizontal_alignment: nil, **kwargs, &block)
    props = _extract_props(kwargs)
    props["vertical_arrangement"] = vertical_arrangement.to_s if vertical_arrangement
    props["horizontal_alignment"] = horizontal_alignment.to_s if horizontal_alignment
    Mrboto._build_compose_node("column", nil, props, &block)
  end

  def row(horizontal_arrangement: nil, vertical_alignment: nil, **kwargs, &block)
    props = _extract_props(kwargs)
    props["horizontal_arrangement"] = horizontal_arrangement.to_s if horizontal_arrangement
    props["vertical_alignment"] = vertical_alignment.to_s if vertical_alignment
    Mrboto._build_compose_node("row", nil, props, &block)
  end

  def box(content_alignment: nil, **kwargs, &block)
    props = _extract_props(kwargs)
    props["content_alignment"] = content_alignment.to_s if content_alignment
    Mrboto._build_compose_node("box", nil, props, &block)
  end

  def spacer(**kwargs)
    props = _extract_props(kwargs)
    Mrboto._build_compose_node("spacer", nil, props)
  end

  # ── Scrolling ──
  def vertical_scroll(**kwargs, &block)
    props = _extract_props(kwargs)
    Mrboto._build_compose_node("vertical_scroll", nil, props, &block)
  end

  def horizontal_scroll(**kwargs, &block)
    props = _extract_props(kwargs)
    Mrboto._build_compose_node("horizontal_scroll", nil, props, &block)
  end

  def lazy_column(**kwargs, &block)
    props = _extract_props(kwargs)
    Mrboto._build_compose_node("lazy_column", nil, props, &block)
  end

  def lazy_row(**kwargs, &block)
    props = _extract_props(kwargs)
    Mrboto._build_compose_node("lazy_row", nil, props, &block)
  end

  # ── Text ──
  def text(content, color: nil, font_size: nil, text_align: nil, font_family: nil, **kwargs)
    props = _extract_props(kwargs)
    props["color"] = color if color
    props["font_size"] = font_size if font_size
    props["text_align"] = text_align.to_s if text_align
    props["font_family"] = font_family.to_s if font_family
    Mrboto._build_compose_node("text", content, props)
  end

  # ── Buttons ──
  def button(content, **kwargs, &block)
    props = _extract_props(kwargs)
    Mrboto._build_compose_node("button", content, props, &block)
  end

  def text_button(content, **kwargs, &block)
    props = _extract_props(kwargs)
    Mrboto._build_compose_node("text_button", content, props, &block)
  end

  def floating_action_button(**kwargs, &block)
    props = _extract_props(kwargs)
    Mrboto._build_compose_node("floating_action_button", nil, props, &block)
  end

  def icon_button(icon: "info", **kwargs, &block)
    props = _extract_props(kwargs)
    props["icon"] = icon.to_s
    Mrboto._build_compose_node("icon_button", nil, props, &block)
  end

  # ── Input ──
  def text_field(value = "", hint: nil, single_line: nil, **kwargs, &on_value_change)
    props = _extract_props(kwargs)
    props["hint"] = hint if hint
    props["single_line"] = !!single_line if single_line
    cid = on_value_change ? Mrboto.register_callback(&on_value_change) : 0
    Mrboto._build_compose_node("text_field", value.to_s, props, cid)
  end

  def outlined_text_field(value = "", hint: nil, single_line: nil, max_lines: nil, **kwargs, &on_value_change)
    props = _extract_props(kwargs)
    props["hint"] = hint if hint
    props["single_line"] = !!single_line if single_line
    props["max_lines"] = max_lines.to_i if max_lines
    cid = on_value_change ? Mrboto.register_callback(&on_value_change) : 0
    Mrboto._build_compose_node("outlined_text_field", value.to_s, props, cid)
  end

  # ── Controls ──
  def switch(checked: false, **kwargs, &on_change)
    props = _extract_props(kwargs)
    props["checked"] = !!checked
    cid = on_change ? Mrboto.register_callback(&on_change) : 0
    Mrboto._build_compose_node("switch", nil, props, cid)
  end

  def checkbox(checked: false, **kwargs, &on_change)
    props = _extract_props(kwargs)
    props["checked"] = !!checked
    cid = on_change ? Mrboto.register_callback(&on_change) : 0
    Mrboto._build_compose_node("checkbox", nil, props, cid)
  end

  def slider(value: 0.0, value_range: nil, **kwargs, &on_change)
    props = _extract_props(kwargs)
    props["value"] = value.to_f
    if value_range.is_a?(Array) && value_range.size == 2
      props["value_range"] = value_range.map(&:to_f)
    end
    cid = on_change ? Mrboto.register_callback(&on_change) : 0
    Mrboto._build_compose_node("slider", nil, props, cid)
  end

  # ── Material3 ──
  def card(**kwargs, &block)
    props = _extract_props(kwargs)
    Mrboto._build_compose_node("card", nil, props, &block)
  end

  def divider(**kwargs)
    props = _extract_props(kwargs)
    Mrboto._build_compose_node("divider", nil, props)
  end

  def scaffold(top_bar: nil, bottom_bar: nil, floating_action_button: nil, **kwargs, &content_block)
    props = _extract_props(kwargs)

    node = {
      "type" => "scaffold",
      "props" => props,
      "children" => [],
    }

    if top_bar.respond_to?(:call)
      saved_stack = ComposeBuilder.instance_variable_get(:@_compose_parent_stack)
      ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])
      top_bar_node = _collect_nodes(&top_bar)
      ComposeBuilder.instance_variable_set(:@_compose_parent_stack, saved_stack)
      node["props"]["top_bar"] = top_bar_node if top_bar_node
    elsif top_bar.is_a?(Hash)
      node["props"]["top_bar"] = top_bar
    end

    if bottom_bar.respond_to?(:call)
      saved_stack = ComposeBuilder.instance_variable_get(:@_compose_parent_stack)
      ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])
      bottom_bar_node = _collect_nodes(&bottom_bar)
      ComposeBuilder.instance_variable_set(:@_compose_parent_stack, saved_stack)
      node["props"]["bottom_bar"] = bottom_bar_node if bottom_bar_node
    elsif bottom_bar.is_a?(Hash)
      node["props"]["bottom_bar"] = bottom_bar
    end

    if floating_action_button.respond_to?(:call)
      saved_stack = ComposeBuilder.instance_variable_get(:@_compose_parent_stack)
      ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])
      fab_node = _collect_nodes(&floating_action_button)
      ComposeBuilder.instance_variable_set(:@_compose_parent_stack, saved_stack)
      node["props"]["floating_action_button"] = fab_node if fab_node
    elsif floating_action_button.is_a?(Hash)
      node["props"]["floating_action_button"] = floating_action_button
    end

    # Content block — add children to scaffold via with_parent
    if content_block.respond_to?(:call)
      ComposeBuilder.stack.push(node)
      content_block.call
      ComposeBuilder.stack.pop
    end

    ComposeBuilder.add_node(node)
    node
  end

  def top_app_bar(title, actions: nil, **kwargs)
    props = _extract_props(kwargs)
    node = {
      "type" => "top_app_bar",
      "props" => props,
      "children" => [],
      "content" => title.to_s,
    }
    if actions.is_a?(Array)
      actions_arr = actions.map do |a|
        action_node = {
          "type" => "action",
          "props" => { "icon" => a["icon"] || a[:icon] || "info" },
          "children" => [],
        }
        if a["on_click"] || a[:on_click]
          cid = Mrboto.register_callback { (a["on_click"] || a[:on_click]).call }
          action_node["callback_id"] = cid
        end
        action_node
      end
      node["props"]["actions"] = actions_arr
    end
    ComposeBuilder.add_node(node)
    node
  end

  def bottom_app_bar(**kwargs, &block)
    props = _extract_props(kwargs)
    Mrboto._build_compose_node("bottom_app_bar", nil, props, &block)
  end

  # ── AndroidView (embed native View in Compose) ──
  def android_view(view_type: nil, **kwargs, &block)
    props = _extract_props(kwargs)
    props["view_type"] = view_type.to_s if view_type
    Mrboto._build_compose_node("android_view", nil, props, &block)
  end

  # ── Icon ──
  def icon(name: "info", **kwargs)
    props = _extract_props(kwargs)
    props["name"] = name.to_s
    Mrboto._build_compose_node("icon", nil, props)
  end

  # ── Image ──
  def image(path_or_res, **kwargs)
    props = _extract_props(kwargs)
    props["path"] = path_or_res.to_s
    Mrboto._build_compose_node("image", nil, props)
  end

  # ── Liquid Glass View (native) ──
  def liquid_glass_view(shape_type: nil, corner_radius: nil, blur_radius: nil, vibrancy: nil, **kwargs, &block)
    props = _extract_props(kwargs)
    props["view_type"] = "moe.bemly.mrboto.LiquidGlassView"
    # These will be applied via AndroidView factory
    props["shape_type"] = shape_type.to_s if shape_type
    props["corner_radius"] = corner_radius.to_f if corner_radius
    props["blur_radius"] = blur_radius.to_f if blur_radius
    props["vibrancy"] = !!vibrancy unless vibrancy.nil?
    Mrboto._build_compose_node("android_view", nil, props, &block)
  end

  # ── Internal: collect child nodes from a block ──
  def _collect_nodes(&block)
    ComposeBuilder.collect_nodes(&block)
  end

  # ── Extract DSL props from kwargs ──
  def _extract_props(kwargs)
    props = {}
    # Modifier from ComposeModifier chain
    if kwargs[:modifier].is_a?(ComposeModifier)
      props["modifier"] = kwargs[:modifier].to_a
    end
    # Direct shorthand props
    [:padding, :fill_max_width, :fill_max_height, :weight, :width, :height,
     :background_color, :align, :aspect_ratio].each do |key|
      if kwargs.key?(key)
        case key
        when :background_color
          props["background_color"] = kwargs[key].to_s
        when :fill_max_width
          props["fill_max_width"] = kwargs[key]
        when :fill_max_height
          props["fill_max_height"] = kwargs[key]
        else
          props[key.to_s] = kwargs[key]
        end
      end
    end
    props
  end
  module_function :_extract_props

  # ── set_compose_content — trigger rendering ──
  def set_compose_content
    root = ComposeBuilder.root
    return unless root && !root["children"].empty?

    # If there's a single root node, use it; otherwise wrap in a Column
    tree = if root["children"].size == 1
             root["children"][0]
           else
             {
               "type" => "column",
               "props" => {},
               "children" => root["children"],
             }
           end

    json = Mrboto._compose_to_json(tree)
    activity = Mrboto.current_activity
    return unless activity

    activity.call_java_method("setComposeContent", json)
  end

  # ── Minimal JSON serializer (no JSON gem needed) ──
  def self._compose_to_json(obj)
    case obj
    when Hash
      pairs = obj.map do |k, v|
        "\"#{k.to_s.gsub('"', '\\"')}\":#{Mrboto._compose_to_json(v)}"
      end
      "{#{pairs.join(',')}}"
    when Array
      items = obj.map { |item| Mrboto._compose_to_json(item) }
      "[#{items.join(',')}]"
    when String
      safe = obj.gsub('\\', '\\\\').gsub('"', '\\"').gsub("\n", '\\n').gsub("\r", '\\r').gsub("\t", '\\t')
      "\"#{safe}\""
    when true then "true"
    when false then "false"
    when nil then "null"
    when Float
      obj.to_s
    when Integer
      obj.to_s
    when Numeric
      obj.to_s
    else
      "\"#{obj.to_s.gsub('"', '\\"')}\""
    end
  end
end

# Make DSL methods available at top level in scripts
include Mrboto
