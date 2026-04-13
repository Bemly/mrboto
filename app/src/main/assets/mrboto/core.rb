# mrboto/core.rb — Mrboto module, JavaObject base class, callback registry
#
# This is the foundation of the mrboto framework. It defines the Mrboto
# module that all other DSL modules extend, and the JavaObject class
# that wraps Android Java objects visible from Ruby.

module Mrboto
  class << self
    attr_accessor :current_activity_id
    attr_accessor :current_activity
    attr_accessor :_ruby_activity_class
    attr_accessor :_test_ctx_id
    attr_accessor :_test_view_id

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
