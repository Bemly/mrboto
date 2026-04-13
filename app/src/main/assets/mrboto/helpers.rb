# mrboto/helpers.rb — Convenience helpers for common Android operations
#
# toast("Hello")
# start_activity(class_name: "moe.bemly.mrboto.demo.SettingsActivity")
# start_activity(class_name: "moe.bemly.mrboto.demo.Detail", extras: { key: "value" })
# get_extra("key")
# sp = shared_preferences("my_prefs")
# sp.put_string("name", "Alice")
# sp.get_string("name", "default")

module Mrboto
  module Helpers
    # ── SharedPreferences Wrapper ──────────────────────────────────────
    class SharedPreferences
      def initialize(name)
        @name = name
      end

      def get_string(key, default = nil)
        ctx = _context
        return default if ctx.nil?
        Mrboto._sp_get_string(ctx, @name, key.to_s, default)
      end

      def put_string(key, value)
        ctx = _context
        return "ok" if ctx.nil?
        Mrboto._sp_put_string(ctx, @name, key.to_s, value.to_s)
        "ok"
      end

      def get_int(key, default = 0)
        ctx = _context
        return default if ctx.nil?
        Mrboto._sp_get_int(ctx, @name, key.to_s, default.to_i)
      end

      def put_int(key, value)
        ctx = _context
        return "ok" if ctx.nil?
        Mrboto._sp_put_int(ctx, @name, key.to_s, value.to_i)
        "ok"
      end

      def _context
        ctx = Mrboto._app_context
        return ctx._registry_id if ctx
        act = Mrboto.current_activity
        act&._registry_id
      end
    end

    # Show an Android Toast
    def self.toast(message, duration = :short)
      activity = Mrboto.current_activity
      return "ok" unless activity

      dur = duration == :long ? 1 : 0
      Mrboto._toast(activity._registry_id, message.to_s, dur)
      "ok"
    end

    # Start another Activity
    def self.start_activity(class_name:, package: nil)
      activity = Mrboto.current_activity
      return unless activity
      cls = package ? "#{package}.#{class_name}" : "#{package_name}.#{class_name}"
      Mrboto._start_activity(activity._registry_id, cls)
    end

    # Start another Ruby Activity (reuse the same RubyActivity class)
    # The script path is passed via Intent extra and resolved in MrbotoActivityBase.
    def self.start_ruby_activity(script_path:)
      activity = Mrboto.current_activity
      return unless activity
      activity.call_java_method('startRubyActivity', script_path)
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

    # ── Resource helper ────────────────────────────────────────────────
    def self.string_resource(res_id)
      activity = Mrboto.current_activity
      return nil unless activity
      Mrboto._string_res(activity._registry_id, res_id)
    end

    # ── Package name ───────────────────────────────────────────────────
    def self.package_name
      Mrboto._package_name
    end

    # ── Dialog ─────────────────────────────────────────────────────────
    def self.dialog(title, message, buttons = nil)
      activity = Mrboto.current_activity
      return unless activity

      btn_str = if buttons.is_a?(Array)
                  "[" + buttons.map { |b| "\"#{b.to_s.gsub('"', '\\"')}\"" }.join(",") + "]"
                else
                  nil
                end

      Mrboto._show_dialog(activity._registry_id, title.to_s, message.to_s, btn_str)
      "ok"
    end

    # ── Snackbar ───────────────────────────────────────────────────────
    def self.snackbar(view_id, message, duration = :short)
      activity = Mrboto.current_activity
      return unless activity

      dur = duration == :long ? 1 : 0
      Mrboto._show_snackbar(activity._registry_id, view_id, message.to_s, dur)
      "ok"
    end

    # ── PopupMenu ──────────────────────────────────────────────────────
    def self.popup_menu(view_id, items)
      activity = Mrboto.current_activity
      return nil unless activity
      return nil unless items.is_a?(Array)

      items_str = "[" + items.map { |i| "\"#{i.to_s.gsub('"', '\\"')}\"" }.join(",") + "]"
      Mrboto._show_popup_menu(activity._registry_id, view_id, items_str)
      "ok"
    end

    # ── Animations ─────────────────────────────────────────────────────
    module Animations
      def self.fade(view_id, from = 1.0, to = 0.0, duration = 300)
        activity = Mrboto.current_activity
        return unless activity
        Mrboto._animate_fade(activity._registry_id, view_id, from.to_f, to.to_f, duration.to_i)
        "ok"
      end

      def self.fade_in(view_id, duration = 300)
        fade(view_id, 0.0, 1.0, duration)
      end

      def self.fade_out(view_id, duration = 300)
        fade(view_id, 1.0, 0.0, duration)
      end

      def self.translate(view_id, from_x = 0.0, from_y = 0.0, to_x = 0.0, to_y = 0.0, duration = 300)
        activity = Mrboto.current_activity
        return unless activity
        Mrboto._animate_translate(activity._registry_id, view_id,
          from_x.to_f, from_y.to_f, to_x.to_f, to_y.to_f, duration.to_i)
        "ok"
      end

      def self.slide_in_bottom(view_id, duration = 300)
        translate(view_id, 0.0, view_id.to_f, 0.0, 0.0, duration)
      end

      def self.scale(view_id, from_x = 1.0, from_y = 1.0, to_x = 1.0, to_y = 1.0, duration = 300)
        activity = Mrboto.current_activity
        return unless activity
        Mrboto._animate_scale(activity._registry_id, view_id,
          from_x.to_f, from_y.to_f, to_x.to_f, to_y.to_f, duration.to_i)
        "ok"
      end

      def self.pulse(view_id, factor = 1.2, duration = 200)
        scale(view_id, 1.0, 1.0, factor.to_f, factor.to_f, duration)
      end
    end
  end
end

# ── Top-level convenience methods ─────────────────────────────────────
def toast(message, duration = :short)
  Mrboto::Helpers.toast(message, duration)
end

def start_activity(class_name:, package: nil)
  Mrboto::Helpers.start_activity(class_name: class_name, package: package)
end

def start_ruby_activity(script_path:)
  Mrboto::Helpers.start_ruby_activity(script_path: script_path)
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

def dialog(title, message, buttons = nil)
  Mrboto::Helpers.dialog(title, message, buttons)
end

# Load and execute a Ruby script from assets.
def load_script(script_path)
  Mrboto.load_script(script_path)
end

# Return the source content of a script from assets.
def load_script_source(script_path)
  Mrboto.load_script_source(script_path)
end

# Evaluate a raw Ruby string via mruby eval.
def ruby_eval(code)
  Mrboto.ruby_eval(code)
end

def snackbar(view_id, message, duration = :short)
  Mrboto::Helpers.snackbar(view_id, message, duration)
end

def popup_menu(view_id, items)
  Mrboto::Helpers.popup_menu(view_id, items)
end

def fade(view_id, from = 1.0, to = 0.0, duration = 300)
  Mrboto::Helpers::Animations.fade(view_id, from, to, duration)
end

def translate(view_id, from_x, from_y, to_x, to_y, duration = 300)
  Mrboto::Helpers::Animations.translate(view_id, from_x, from_y, to_x, to_y, duration)
end

def scale(view_id, from_x, from_y, to_x, to_y, duration = 300)
  Mrboto::Helpers::Animations.scale(view_id, from_x, from_y, to_x, to_y, duration)
end
