# mrboto/core.rb — Mrboto module, JavaObject base class, callback registry
#
# This is the foundation of the mrboto framework. It defines the Mrboto
# module that all other DSL modules extend, and the JavaObject class
# that wraps Android Java objects visible from Ruby.

module Mrboto
  class << self
    attr_accessor :current_activity_id
    attr_accessor :current_activity
    attr_accessor :_test_ctx_id
    attr_accessor :_test_view_id

    # ── Activity Class Registration ────────────────────────────────
    # Scripts should call Mrboto.register_activity_class(ClassName)
    # at the end. This auto-sets the class so Kotlin can instantiate it.
    def _ruby_activity_class
      @_ruby_activity_class
    end
    def _ruby_activity_class=(klass)
      @_ruby_activity_class = klass
    end

    def register_activity_class(klass)
      @_ruby_activity_class = klass
    end

    # ── Callback Registry ──────────────────────────────────────────
    @@callbacks = {}
    @@next_callback_id = 1

    def register_callback(&block)
      id = @@next_callback_id
      @@next_callback_id += 1
      @@callbacks[id] = block
      id
    end

    def dispatch_callback(id, *args)
      cb = @@callbacks[id]
      cb.call(*args) if cb
    end

    def dispatch_text_changed(id, text)
      cb = @@callbacks[id]
      cb.call(text) if cb
    end

    def dispatch_checked(id, is_checked)
      cb = @@callbacks[id]
      cb.call(is_checked) if cb
    end

    def dispatch_touch(id, view_id, action, x, y)
      cb = @@callbacks[id]
      if cb
        result = cb.call(view_id, action, x, y)
        result == true ? "true" : "false"
      else
        "false"
      end
    end
  end

  # ── Script Loading & Evaluation ────────────────────────────────
  # These are defined at module level (not in class << self) because
  # mruby makes methods defined with `def self.xxx` inside
  # `class << self` private when class variables (`@@callbacks`)
  # precede them.

  # Load and execute a Ruby script from assets.
  def self.load_script(script_path)
    activity = current_activity
    return nil unless activity
    activity.call_java_method("loadAssetScript", script_path.to_s)
  end

  # Return the source content of a script from assets (no execution).
  def self.load_script_source(script_path)
    activity = current_activity
    return nil unless activity
    activity.call_java_method("loadAssetScriptSource", script_path.to_s)
  end

  # Evaluate a raw Ruby string via mruby eval.
  # Falls back to _eval (C-side) when no Activity is set.
  def self.ruby_eval(code)
    activity = current_activity
    if activity
      activity.call_java_method("evalRuby", code.to_s)
    else
      Mrboto._eval(code.to_s)
    end
  end

  # ── JavaObject ─────────────────────────────────────────────────────
  #
  # Base class for all Java object wrappers in mruby.
  # Instances hold a registry_id that maps to a JNI GlobalRef in C.

  class JavaObject
    attr_reader :_registry_id

    def self.from_registry(id)
      return nil if id.nil? || id <= 0 || id > 4096
      obj = allocate
      obj.instance_variable_set(:@_registry_id, id)
      obj
    end

    def initialize(registry_id = nil)
      @_registry_id = registry_id
    end

    # Returns the underlying Java object (for native method calls)
    def java_object
      Mrboto._java_object_for(@_registry_id)
    end

    # Call a Java method on the wrapped object
    def call_java_method(name, *args)
      Mrboto._call_java_method(@_registry_id, name.to_s, *args)
    end
  end

  # ── Native Methods (implemented in android-jni-bridge.c) ──────────
  # _eval(code) — evaluate Ruby code string, return result
  # _java_object_for, _call_java_method, _register_object, etc.

  # ── Android System Resource Helper ────────────────────────────────
  # Look up an Android system resource ID by name.
  # Usage: android_sys_id("ic_menu_info_details", "drawable")
  @@_android_sys_id_cache = {}
  def self.android_sys_id(name, type = "drawable")
    key = "#{type}/#{name}"
    return @@_android_sys_id_cache[key] if @@_android_sys_id_cache[key]

    id = Mrboto._get_sys_res_id(0, name.to_s, type.to_s)
    if id && id > 0
      @@_android_sys_id_cache[key] = id
      id
    else
      nil
    end
  end
end

# Regexp support: =~ returns MatchData, $~ is set (mruby supports $~ assignment)
# but $1-$9 are NOT assignable in mruby 4.0.0
class Regexp
  def _set_match_globals(m)
    $~ = m
  end
end

class String
  def =~(other)
    if other.is_a?(Regexp)
      m = other.match(self)
      other._set_match_globals(m)
      m ? m.begin(0) : nil
    else
      nil
    end
  end
end