# mrboto/helpers.rb — Convenience helpers for common Android operations
#
# toast("Hello")
# start_activity(class_name: "com.mrboto.demo.SettingsActivity")
# start_activity(class_name: "com.mrboto.demo.Detail", extras: { key: "value" })
# get_extra("key")
# sp = shared_preferences("my_prefs")
# sp.put_string("name", "Alice")
# sp.get_string("name", "default")

module Mrboto
  module Helpers
    # Show an Android Toast
    def self.toast(message, duration = :short)
      activity = Mrboto.current_activity
      return unless activity

      dur = duration == :long ? 1 : 0
      Mrboto._toast(activity._registry_id, message.to_s, dur)
    end

    # Start another Activity
    def self.start_activity(class_name:, package: nil, extras: nil)
      activity = Mrboto.current_activity
      return unless activity

      pkg = package || Mrboto.package_name
      extras_map = extras.is_a?(Hash) ? extras : {}

      Mrboto._start_activity(activity._registry_id, pkg, class_name, extras_map)
    end

    # Get an Intent extra passed to the current Activity
    def self.get_extra(key)
      activity = Mrboto.current_activity
      return nil unless activity
      Mrboto._get_extra(activity._registry_id, key.to_s)
    end

    # Access a SharedPreferences file
    def self.shared_preferences(name)
      SharedPreferences.new(name)
    end

    # Run a block on the UI thread
    def self.run_on_ui_thread(&block)
      activity = Mrboto.current_activity
      return unless activity
      cid = Mrboto.register_callback(&block)
      Mrboto._run_on_ui_thread(activity._registry_id, cid)
    end
  end

  # ── SharedPreferences Wrapper ──────────────────────────────────────
  class SharedPreferences
    def initialize(name)
      @name = name
    end

    def get_string(key, default = nil)
      ctx = Mrboto._app_context
      Mrboto._sp_get_string(ctx, @name, key.to_s, default)
    end

    def put_string(key, value)
      ctx = Mrboto._app_context
      Mrboto._sp_put_string(ctx, @name, key.to_s, value.to_s)
    end

    def get_int(key, default = 0)
      ctx = Mrboto._app_context
      Mrboto._sp_get_int(ctx, @name, key.to_s, default.to_i)
    end

    def put_int(key, value)
      ctx = Mrboto._app_context
      Mrboto._sp_put_int(ctx, @name, key.to_s, value.to_i)
    end
  end

  # ── Resource helper ────────────────────────────────────────────────
  def self.string_resource(res_id)
    activity = Mrboto.current_activity
    return nil unless activity
    Mrboto._string_res(activity._registry_id, res_id)
  end

  # ── Package name ───────────────────────────────────────────────────
  def self.package_name
    ctx = Mrboto._app_context
    ctx ? ctx.call_java_method("getPackageName") : nil
  end
end

# ── Top-level convenience methods ─────────────────────────────────────
def toast(message, duration = :short)
  Mrboto::Helpers.toast(message, duration)
end

def start_activity(class_name:, package: nil, extras: nil)
  Mrboto::Helpers.start_activity(class_name: class_name, package: package, extras: extras)
end

def get_extra(key)
  Mrboto::Helpers.get_extra(key)
end

def shared_preferences(name)
  Mrboto::Helpers.shared_preferences(name)
end

def run_on_ui_thread(&block)
  Mrboto::Helpers.run_on_ui_thread(&block)
end
