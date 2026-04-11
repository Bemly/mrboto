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
      return nil if id.nil? || id == 0
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

  # ── Native Method Stubs ───────────────────────────────────────────
  # These are implemented in android-jni-bridge.c
  # They are called from Ruby and bridge to native code.

  def self._java_object_for(id); end
  def self._call_java_method(id, name, *args); end
  def self._register_object(obj); end
  def self._toast(context_id, msg, duration); end
  def self._start_activity(context_id, cls_name); end
  def self._get_extra(activity_id, key); end
  def self._app_context; end
  def self._create_view(context_id, class_name, attrs); end
  def self._set_content_view(activity_id, view_id); end
  def self._set_on_click(view_id, callback_id); end
  def self._set_text_watcher(view_id, callback_id); end
  def self._sp_get_string(context_id, name, key, default_val); end
  def self._sp_put_string(context_id, name, key, value); end
  def self._sp_get_int(context_id, name, key, default_val); end
  def self._sp_put_int(context_id, name, key, value); end
  def self._string_res(activity_id, res_id); end
  def self._run_on_ui_thread(activity_id, callback_id); end
end
