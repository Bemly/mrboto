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
        Mrboto._sp_get_int(ctx, @name, key.to_s, default)
      end

      def put_int(key, value)
        ctx = _context
        return "ok" if ctx.nil?
        Mrboto._sp_put_int(ctx, @name, key.to_s, value.to_i)
        "ok"
      end

      def _context
        ctx = Mrboto._app_context
        if ctx
          id = Mrboto._register_object(ctx)
          return id if id && id > 0
        end
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
    def self.start_activity(class_name:, package: nil, extras: nil)
      activity = Mrboto.current_activity
      return unless activity
      cls = package ? "#{package}.#{class_name}" : "#{package_name}.#{class_name}"
      if extras.is_a?(Hash) && extras.size > 0
        json = build_json_hash(extras)
        activity.call_java_method("startActivityWithExtras", cls, json)
      else
        Mrboto._start_activity(activity._registry_id, cls)
      end
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
    def self.popup_menu(view_id, items, &block)
      activity = Mrboto.current_activity
      return nil unless activity
      return nil unless items.is_a?(Array)

      callback_id = block ? Mrboto.register_callback(&block) : 0
      items_str = "[" + items.map { |i| "\"#{i.to_s.gsub('"', '\\"')}\"" }.join(",") + "]"
      activity.call_java_method("showPopupMenu", view_id, items_str, callback_id)
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

    # ── Clipboard ──────────────────────────────────────────────────────
    def self.clipboard_copy(text)
      activity = Mrboto.current_activity
      return unless activity
      activity.call_java_method("clipboardCopy", text.to_s)
      "ok"
    end

    def self.clipboard_paste
      activity = Mrboto.current_activity
      return nil unless activity
      activity.call_java_method("clipboardPaste")
    end

    def self.clipboard_has_text?
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("clipboardHasText")
      result == true || result.to_s == "true"
    end

    def self.clipboard_system_copy(text)
      activity = Mrboto.current_activity
      return false unless activity
      activity.call_java_method("clipboardSystemCopy", text.to_s)
      true
    end

    def self.clipboard_system_paste
      activity = Mrboto.current_activity
      return "" unless activity
      activity.call_java_method("clipboardSystemPaste").to_s
    end

    def self.clipboard_system_has_text?
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("clipboardSystemHasText")
      result == true || result.to_s == "true"
    end

    # ── Intent Extras ──────────────────────────────────────────────────
    def self.get_extra_int(key)
      activity = Mrboto.current_activity
      return 0 unless activity
      activity.call_java_method("getExtraInt", key.to_s).to_i
    end

    def self.get_extra_bool(key)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("getExtraBool", key.to_s)
      result == true || result.to_s == "true"
    end

    def self.get_extra_float(key)
      activity = Mrboto.current_activity
      return 0.0 unless activity
      activity.call_java_method("getExtraFloat", key.to_s).to_f
    end

    def self.get_all_extras
      activity = Mrboto.current_activity
      return {} unless activity
      json = activity.call_java_method("getAllExtras").to_s
      parse_json_object(json)
    end

    # ── File Operations ────────────────────────────────────────────────
    module FileOps
      def self.write(name, content)
        activity = Mrboto.current_activity
        return false unless activity
        result = activity.call_java_method("fileWrite", name.to_s, content.to_s)
        result == true || result.to_s == "true"
      end

      def self.read(name)
        activity = Mrboto.current_activity
        return "" unless activity
        activity.call_java_method("fileRead", name.to_s).to_s
      end

      def self.exists?(name)
        activity = Mrboto.current_activity
        return false unless activity
        result = activity.call_java_method("fileExists", name.to_s)
        result == true || result.to_s == "true"
      end

      def self.delete(name)
        activity = Mrboto.current_activity
        return false unless activity
        result = activity.call_java_method("fileDelete", name.to_s)
        result == true || result.to_s == "true"
      end

      def self.list(dir = "")
        activity = Mrboto.current_activity
        return [] unless activity
        json = activity.call_java_method("fileList", dir.to_s).to_s
        Mrboto::Helpers.parse_json_array(json)
      end

      def self.external_write(name, content)
        activity = Mrboto.current_activity
        return false unless activity
        result = activity.call_java_method("externalFileWrite", name.to_s, content.to_s)
        result == true || result.to_s == "true"
      end

      def self.external_read(name)
        activity = Mrboto.current_activity
        return "" unless activity
        activity.call_java_method("externalFileRead", name.to_s).to_s
      end

      def self.cache_write(name, content)
        activity = Mrboto.current_activity
        return false unless activity
        result = activity.call_java_method("cacheWrite", name.to_s, content.to_s)
        result == true || result.to_s == "true"
      end

      def self.cache_read(name)
        activity = Mrboto.current_activity
        return "" unless activity
        activity.call_java_method("cacheRead", name.to_s).to_s
      end

      def self.size(name)
        activity = Mrboto.current_activity
        return -1 unless activity
        activity.call_java_method("fileSize", name.to_s).to_i
      end
    end

    # ── Permission Requests ────────────────────────────────────────────
    def self.permission_granted?(permission)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("checkPermissionGranted", permission.to_s)
      result == true || result.to_s == "true"
    end

    def self.request_permission(permission)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("requestPermissionSync", permission.to_s)
      result == true || result.to_s == "true"
    end

    def self.request_permissions(perms)
      activity = Mrboto.current_activity
      return {} unless activity
      return {} unless perms.is_a?(Array)
      json_arr = "[" + perms.map { |p| "\"#{p.to_s}\"" }.join(",") + "]"
      json = activity.call_java_method("requestPermissionsSync", json_arr).to_s
      parse_json_object(json)
    end

    # Permission constants
    PERMISSION_CAMERA = "android.permission.CAMERA"
    PERMISSION_READ_EXTERNAL_STORAGE = "android.permission.READ_MEDIA_IMAGES"
    PERMISSION_WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE"
    PERMISSION_INTERNET = "android.permission.INTERNET"
    PERMISSION_RECORD_AUDIO = "android.permission.RECORD_AUDIO"
    PERMISSION_ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    PERMISSION_POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

    # ── Notification ───────────────────────────────────────────────────
    def self.notify(id, title, message, channel: "default")
      activity = Mrboto.current_activity
      return unless activity
      activity.call_java_method("notifyShow", id.to_i, title.to_s, message.to_s, channel.to_s)
      "ok"
    end

    def self.notify_cancel(id)
      activity = Mrboto.current_activity
      return unless activity
      activity.call_java_method("notifyCancel", id.to_i)
      "ok"
    end

    def self.notify_big(id, title, big_text, channel: "default")
      activity = Mrboto.current_activity
      return unless activity
      activity.call_java_method("notifyBig", id.to_i, title.to_s, big_text.to_s, channel.to_s)
      "ok"
    end

    def self.notify_progress(id, title, message, progress, max, channel: "default")
      activity = Mrboto.current_activity
      return unless activity
      activity.call_java_method("notifyProgress", id.to_i, title.to_s, message.to_s,
        progress.to_i, max.to_i, channel.to_s)
      "ok"
    end

    # ── SQLite Database ────────────────────────────────────────────────
    class SQLiteDB
      def initialize(name)
        @name = name
        @db_id = nil
      end

      def open
        act = Mrboto.current_activity
        return self unless act
        @db_id = act.call_java_method("sqliteOpen", @name).to_i
        self
      end

      def execute(sql)
        act = Mrboto.current_activity
        return false unless act && @db_id
        result = act.call_java_method("sqliteExecute", @db_id, sql.to_s)
        result == true || result.to_s == "true"
      end

      def insert(table, values)
        act = Mrboto.current_activity
        return -1 unless act && @db_id
        act.call_java_method("sqliteInsert", @db_id, table.to_s, Mrboto::Helpers.build_json_hash(values)).to_i
      end

      def query(sql)
        act = Mrboto.current_activity
        return [] unless act && @db_id
        json = act.call_java_method("sqliteQuery", @db_id, sql.to_s).to_s
        Mrboto::Helpers.parse_json_array_of_objects(json)
      end

      def raw_query(sql)
        query(sql)
      end

      def update(table, values, where_clause = "")
        act = Mrboto.current_activity
        return -1 unless act && @db_id
        act.call_java_method("sqliteUpdate", @db_id, table.to_s,
          Mrboto::Helpers.build_json_hash(values), where_clause.to_s).to_i
      end

      def delete(table, where_clause = "")
        act = Mrboto.current_activity
        return -1 unless act && @db_id
        act.call_java_method("sqliteDelete", @db_id, table.to_s, where_clause.to_s).to_i
      end

      def close
        act = Mrboto.current_activity
        act&.call_java_method("sqliteClose", @db_id) if @db_id
        @db_id = nil
      end
    end

    def self.sqlite_open(name)
      db = SQLiteDB.new(name)
      db.open
    end

    # ── Network Requests ───────────────────────────────────────────────
    def self.http_get(url, headers = {})
      activity = Mrboto.current_activity
      return '{"status":0,"body":"","headers":{}}' unless activity
      hdr = headers.is_a?(Hash) && headers.size > 0 ? build_json_hash(headers) : nil
      activity.call_java_method("httpGet", url.to_s, hdr).to_s
    end

    def self.http_post(url, body = "", headers = {})
      activity = Mrboto.current_activity
      return '{"status":0,"body":"","headers":{}}' unless activity
      hdr = headers.is_a?(Hash) && headers.size > 0 ? build_json_hash(headers) : nil
      activity.call_java_method("httpPost", url.to_s, body.to_s, hdr).to_s
    end

    def self.http_download(url, filepath)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("httpDownload", url.to_s, filepath.to_s)
      result == true || result.to_s == "true"
    end

    # ── Shell ──────────────────────────────────────────────────────────────
    def self.shell_exec(cmd, timeout: 10)
      activity = Mrboto.current_activity
      return nil unless activity
      activity.call_java_method("shellExec", cmd.to_s, timeout.to_i).to_s
    end

    # ── Intent / URL Scheme ────────────────────────────────────────────────
    def self.intent_view(url)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("intentView", url.to_s)
      result == true || result.to_s == "true"
    end

    def self.intent_send(text, subject: "")
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("intentSend", text.to_s, subject.to_s)
      result == true || result.to_s == "true"
    end

    def self.intent_action(action, data: "", type: "")
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("intentAction", action.to_s, data.to_s, type.to_s)
      result == true || result.to_s == "true"
    end

    # ── Coroutine / Timer ──────────────────────────────────────────────────
    def self.run_async(&block)
      callback_id = Mrboto.register_callback(&block)
      activity = Mrboto.current_activity
      return unless activity
      activity.call_java_method("runAsync", callback_id)
    end

    def self.run_delayed(ms, &block)
      callback_id = Mrboto.register_callback(&block)
      activity = Mrboto.current_activity
      return unless activity
      activity.call_java_method("runDelayed", callback_id, ms.to_i)
    end

    def self.timer_start(interval_ms, &block)
      callback_id = Mrboto.register_callback(&block)
      activity = Mrboto.current_activity
      return -1 unless activity
      activity.call_java_method("timerStart", callback_id, interval_ms.to_i).to_i
    end

    def self.timer_stop(timer_id)
      activity = Mrboto.current_activity
      return unless activity
      activity.call_java_method("timerStop", timer_id.to_i)
    end

    def self.timer_once(delay_ms, &block)
      callback_id = Mrboto.register_callback(&block)
      activity = Mrboto.current_activity
      return -1 unless activity
      activity.call_java_method("timerOnce", callback_id, delay_ms.to_i).to_i
    end

    # ── File Encoding ──────────────────────────────────────────────────────
    def self.file_read_encoding(name, encoding)
      activity = Mrboto.current_activity
      return "" unless activity
      activity.call_java_method("fileReadEncoding", name.to_s, encoding.to_s).to_s
    end

    def self.file_write_encoding(name, content, encoding)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("fileWriteEncoding", name.to_s, content.to_s, encoding.to_s)
      result == true || result.to_s == "true"
    end

    # ── Directory Operations ───────────────────────────────────────────────
    def self.file_list_dir(path = "")
      activity = Mrboto.current_activity
      return [] unless activity
      json = activity.call_java_method("fileListDir", path.to_s).to_s
      parse_json_array(json)
    end

    def self.file_mkdir(path)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("fileMkdir", path.to_s)
      result == true || result.to_s == "true"
    end

    def self.file_delete_dir(path)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("fileDeleteDir", path.to_s)
      result == true || result.to_s == "true"
    end

    def self.file_exists_path(path)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("fileExistsPath", path.to_s)
      result == true || result.to_s == "true"
    end

    def self.file_is_dir(path)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("fileIsDir", path.to_s)
      result == true || result.to_s == "true"
    end

    # ── Accessibility ──────────────────────────────────────────────────────
    def self.accessibility_enabled?
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("accessibilityEnabled")
      result == true || result.to_s == "true"
    end

    def self.accessibility_touch_exploration?
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("accessibilityTouchExploration")
      result == true || result.to_s == "true"
    end

    # ── Screen Capture ─────────────────────────────────────────────────────
    def self.request_screen_capture(&block)
      callback_id = Mrboto.register_callback(&block)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("requestScreenCapture", callback_id)
      result == true || result.to_s == "true"
    end

    def self.capture_screen(out_path)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("captureScreen", out_path.to_s)
      result == true || result.to_s == "true"
    end

    def self.start_record_screen(out_path)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("startRecordScreen", out_path.to_s)
      result == true || result.to_s == "true"
    end

    def self.stop_record_screen
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("stopRecordScreen")
      result == true || result.to_s == "true"
    end

    # ── Gesture ────────────────────────────────────────────────────────────
    def self.gesture_click(x, y)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("gestureClick", x.to_i, y.to_i)
      result == true || result.to_s == "true"
    end

    def self.gesture_long_click(x, y)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("gestureLongClick", x.to_i, y.to_i)
      result == true || result.to_s == "true"
    end

    def self.gesture_swipe(x1, y1, x2, y2, duration: 300)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("gestureSwipe", x1.to_i, y1.to_i, x2.to_i, y2.to_i, duration.to_i)
      result == true || result.to_s == "true"
    end

    def self.gesture_scroll(x1, y1, x2, y2, duration: 300)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("gestureScroll", x1.to_i, y1.to_i, x2.to_i, y2.to_i, duration.to_i)
      result == true || result.to_s == "true"
    end

    # ── Overlay ────────────────────────────────────────────────────────────
    def self.overlay_show(x, y, width: -2, height: -2, view_id: nil)
      activity = Mrboto.current_activity
      return -1 unless activity

      # If no view_id provided, create a default view
      if view_id.nil?
        # Create a simple text view for the overlay using Widgets.create_view
        view = Mrboto::Widgets.create_view('android.widget.TextView',
          text: "Overlay",
          text_size: 16,
          background_color: "80FF0000")
        unless view
          Mrboto._log("[mrboto] overlay_show: failed to create default TextView")
          return -1
        end
        view_id = view._registry_id
      end

      activity.call_java_method("overlayShow", view_id.to_i, x.to_i, y.to_i, width.to_i, height.to_i).to_i
    end

    def self.overlay_remove(overlay_id)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("overlayRemove", overlay_id.to_i)
      result == true || result.to_s == "true"
    end

    def self.overlay_update_position(overlay_id, x, y)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("overlayUpdatePosition", overlay_id.to_i, x.to_i, y.to_i)
      result == true || result.to_s == "true"
    end

    def self.check_overlay_permission
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("checkOverlayPermission")
      result == true || result.to_s == "true"
    end

    def self.open_overlay_settings
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("openOverlaySettings")
      result == true || result.to_s == "true"
    end

    # ── Predictive Back ────────────────────────────────────────────────────
    def self.predictive_back_enabled?
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("predictiveBackEnabled")
      result == true || result.to_s == "true"
    end

    # ── Color / Find Color ─────────────────────────────────────────────────
    def self.get_color_at(bitmap_path, x, y)
      activity = Mrboto.current_activity
      return "" unless activity
      activity.call_java_method("getColorAt", bitmap_path.to_s, x.to_i, y.to_i).to_s
    end

    def self.find_color(bitmap_path, color_hex, region: "")
      activity = Mrboto.current_activity
      return "[]" unless activity
      activity.call_java_method("findColor", bitmap_path.to_s, color_hex.to_s, region.to_s).to_s
    end

    def self.find_color_fuzzy(bitmap_path, color_hex, threshold: 32, region: "")
      activity = Mrboto.current_activity
      return "[]" unless activity
      activity.call_java_method("findColorFuzzy", bitmap_path.to_s, color_hex.to_s, threshold.to_i, region.to_s).to_s
    end

    # ── Image Processing ───────────────────────────────────────────────────
    def self.image_crop(path, x, y, w, h, out_path)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("imageCrop", path.to_s, x.to_i, y.to_i, w.to_i, h.to_i, out_path.to_s)
      result == true || result.to_s == "true"
    end

    def self.image_scale(path, new_w, new_h, out_path)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("imageScale", path.to_s, new_w.to_i, new_h.to_i, out_path.to_s)
      result == true || result.to_s == "true"
    end

    def self.image_rotate(path, degrees, out_path)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("imageRotate", path.to_s, degrees.to_f, out_path.to_s)
      result == true || result.to_s == "true"
    end

    def self.image_to_base64(path, format: "png")
      activity = Mrboto.current_activity
      return "" unless activity
      activity.call_java_method("imageToBase64", path.to_s, format.to_s).to_s
    end

    def self.image_from_base64(base64, out_path)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("imageFromBase64", base64.to_s, out_path.to_s)
      result == true || result.to_s == "true"
    end

    def self.image_pixel_color(path, x, y)
      activity = Mrboto.current_activity
      return "" unless activity
      activity.call_java_method("imagePixelColor", path.to_s, x.to_i, y.to_i).to_s
    end

    def self.image_width(path)
      activity = Mrboto.current_activity
      return -1 unless activity
      activity.call_java_method("imageWidth", path.to_s).to_i
    end

    def self.image_height(path)
      activity = Mrboto.current_activity
      return -1 unless activity
      activity.call_java_method("imageHeight", path.to_s).to_i
    end

    # ── Event Listener ─────────────────────────────────────────────────────
    def self.observe_battery(&block)
      callback_id = Mrboto.register_callback(&block)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("observeBattery", callback_id)
      result == true || result.to_s == "true"
    end

    def self.observe_network(&block)
      callback_id = Mrboto.register_callback(&block)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("observeNetwork", callback_id)
      result == true || result.to_s == "true"
    end

    def self.network_connected?
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("isNetworkConnected")
      result == true || result.to_s == "true"
    end

    # ── Device Control ─────────────────────────────────────────────────────
    def self.set_volume(stream_type, level, show_ui: false)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("setVolume", stream_type.to_i, level.to_i, show_ui)
      result == true || result.to_s == "true"
    end

    def self.get_volume(stream_type)
      activity = Mrboto.current_activity
      return -1 unless activity
      activity.call_java_method("getVolume", stream_type.to_i).to_i
    end

    def self.set_brightness(level)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("setBrightness", level.to_i)
      result == true || result.to_s == "true"
    end

    def self.get_brightness
      activity = Mrboto.current_activity
      return -1 unless activity
      activity.call_java_method("getBrightness").to_i
    end

    def self.vibrate(duration: 200)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("vibrate", duration.to_i)
      result == true || result.to_s == "true"
    end

    def self.vibrate_pattern(pattern, repeat: -1)
      activity = Mrboto.current_activity
      return false unless activity
      pattern_json = pattern.is_a?(String) ? pattern : build_json_array(pattern)
      result = activity.call_java_method("vibratePattern", pattern_json, repeat.to_i)
      result == true || result.to_s == "true"
    end

    # ── Sensor ─────────────────────────────────────────────────────────────
    def self.start_gyroscope(&block)
      callback_id = Mrboto.register_callback(&block)
      activity = Mrboto.current_activity
      return -1 unless activity
      activity.call_java_method("startGyroscope", callback_id).to_i
    end

    def self.stop_gyroscope(sensor_id)
      activity = Mrboto.current_activity
      return unless activity
      activity.call_java_method("stopGyroscope", sensor_id.to_i)
    end

    def self.start_accelerometer(&block)
      callback_id = Mrboto.register_callback(&block)
      activity = Mrboto.current_activity
      return -1 unless activity
      activity.call_java_method("startAccelerometer", callback_id).to_i
    end

    def self.stop_accelerometer(sensor_id)
      activity = Mrboto.current_activity
      return unless activity
      activity.call_java_method("stopAccelerometer", sensor_id.to_i)
    end

    def self.start_proximity(&block)
      callback_id = Mrboto.register_callback(&block)
      activity = Mrboto.current_activity
      return -1 unless activity
      activity.call_java_method("startProximity", callback_id).to_i
    end

    def self.stop_proximity(sensor_id)
      activity = Mrboto.current_activity
      return unless activity
      activity.call_java_method("stopProximity", sensor_id.to_i)
    end

    # ── Camera ─────────────────────────────────────────────────────────────
    def self.camera_available?
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("cameraAvailable")
      result == true || result.to_s == "true"
    end

    def self.camera_info
      activity = Mrboto.current_activity
      return "[]" unless activity
      activity.call_java_method("cameraInfo").to_s
    end

    def self.camera_take_photo(&block)
      callback_id = Mrboto.register_callback(&block)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("cameraTakePhoto", callback_id)
      result == true || result.to_s == "true"
    end

    def self.camera_record_video(&block)
      callback_id = Mrboto.register_callback(&block)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("cameraRecordVideo", callback_id)
      result == true || result.to_s == "true"
    end

    # ── Window Info ────────────────────────────────────────────────────────
    def self.current_activity_name
      activity = Mrboto.current_activity
      return "" unless activity
      activity.call_java_method("getCurrentActivityName").to_s
    end

    def self.current_layout_info
      activity = Mrboto.current_activity
      return "{}" unless activity
      activity.call_java_method("getCurrentLayoutInfo").to_s
    end

    def self.top_activity_package
      activity = Mrboto.current_activity
      return "" unless activity
      activity.call_java_method("getTopActivityPackage").to_s
    end

    # ── Threading / Atomic ─────────────────────────────────────────────────
    def self.thread_start(&block)
      callback_id = Mrboto.register_callback(&block)
      activity = Mrboto.current_activity
      return -1 unless activity
      activity.call_java_method("threadStart", callback_id).to_i
    end

    def self.thread_join(thread_id)
      activity = Mrboto.current_activity
      return unless activity
      activity.call_java_method("threadJoin", thread_id.to_i)
    end

    def self.atomic_get(counter_id)
      activity = Mrboto.current_activity
      return 0 unless activity
      activity.call_java_method("atomicGet", counter_id.to_i).to_i
    end

    def self.atomic_set(counter_id, value)
      activity = Mrboto.current_activity
      return unless activity
      activity.call_java_method("atomicSet", counter_id.to_i, value.to_i)
    end

    def self.atomic_increment(counter_id)
      activity = Mrboto.current_activity
      return 0 unless activity
      activity.call_java_method("atomicIncrement", counter_id.to_i).to_i
    end

    # ── Enhanced HTTP ──────────────────────────────────────────────────────
    def self.http_get_ex(url, headers: nil)
      activity = Mrboto.current_activity
      return "" unless activity
      hdr = headers ? build_json_hash(headers) : nil
      activity.call_java_method("httpGetEx", url.to_s, hdr).to_s
    end

    def self.http_post_ex(url, body, headers: nil)
      activity = Mrboto.current_activity
      return "" unless activity
      hdr = headers ? build_json_hash(headers) : nil
      activity.call_java_method("httpPostEx", url.to_s, body.to_s, hdr).to_s
    end

    def self.http_upload(url, file_path, field_name: "file")
      activity = Mrboto.current_activity
      return "" unless activity
      activity.call_java_method("httpUpload", url.to_s, file_path.to_s, field_name.to_s).to_s
    end

    # ── OCR (PaddleOCR v5 NCNN) ────────────────────────────────────────────
    def self.ocr_init
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("ocrInit")
      result == true || result.to_s == "true"
    end

    def self.ocr_recognize(image_path)
      activity = Mrboto.current_activity
      return "" unless activity
      activity.call_java_method("ocrRecognize", image_path.to_s).to_s
    end

    def self.ocr_recognize_from_path(image_path)
      ocr_recognize(image_path)
    end

    def self.ocr_detect(image_path)
      activity = Mrboto.current_activity
      return "" unless activity
      activity.call_java_method("ocrDetect", image_path.to_s).to_s
    end

    def self.ocr_release
      activity = Mrboto.current_activity
      return true unless activity
      result = activity.call_java_method("ocrRelease")
      result == true || result.to_s == "true"
    end

    # ── JSON helpers (no dependency on JSON gem) ───────────────────────
    def self.build_json_hash(hash)
      pairs = hash.map do |k, v|
        key = k.to_s.gsub('"', '\\"')
        val = case v
              when String then "\"#{v.to_s.gsub('"', '\\"')}\""
              when Integer, Float then v.to_s
              when true then "true"
              when false then "false"
              when nil then "null"
              else "\"#{v.to_s.gsub('"', '\\"')}\""
              end
        "\"#{key}\":#{val}"
      end
      "{#{pairs.join(',')}}"
    end

    def self.parse_json_array(json)
      return [] if json.nil? || json.empty?
      json = json.strip
      return [] unless json.start_with?("[") && json.end_with?("]")
      content = json[1..-2]
      return [] if content.nil? || content.empty?
      items = split_json_pairs(content)
      items.map { |item| parse_json_value(item.strip) }
    end

    def self.parse_json_object(json)
      return {} if json.nil? || json.empty? || json == "{}"
      result = {}
      content = json.strip
      return {} unless content.start_with?("{") && content.end_with?("}")
      content = content[1..-2]
      return {} if content.nil? || content.empty?
      pairs = split_json_pairs(content)
      pairs.each do |pair|
        m = /^\s*"([^"]*)"\s*:\s*(.*)$/.match(pair.strip)
        next unless m
        key = m[1]
        val_str = m[2].strip
        val = parse_json_value(val_str)
        result[key] = val
      end
      result
    end

    def self.parse_json_array(json)
      return [] if json.nil? || json.empty? || json == "[]"
      content = json.strip
      return [] unless content.start_with?("[") && content.end_with?("]")
      content = content[1..-2]
      return [] if content.nil? || content.empty?
      items = split_json_pairs(content)
      items.map { |item| parse_json_value(item.strip) }
    end

    def self.parse_json_value(val_str)
      m = /^"(.*)"$/.match(val_str)
      return m[1] if m
      return val_str.to_i if /^-?\d+$/.match(val_str)
      return val_str.to_f if /^-?\d+\.\d+$/.match(val_str)
      return true if val_str == "true"
      return false if val_str == "false"
      return nil if val_str == "null"
      val_str
    end

    def self.parse_json_array_of_objects(json)
      return [] if json.nil? || json.empty? || json == "[]"
      content = json.strip
      return [] unless content.start_with?("[") && content.end_with?("]")
      content = content[1..-2]
      return [] if content.nil? || content.empty?
      # Split top-level objects by finding matching braces
      objects = []
      depth = 0
      current = ""
      content.each_char do |c|
        if c == '{'
          depth += 1
          current += c
        elsif c == '}'
          depth -= 1
          current += c
          if depth == 0
            objects << parse_json_object(current)
            current = ""
          end
        elsif c == ',' && depth == 0
          # skip separator between objects
        else
          current += c if depth > 0
        end
      end
      objects
    end

    def self.split_json_pairs(str)
      parts = []
      depth = 0
      in_string = false
      current = ""
      str.each_char do |c|
        if c == '"' && !in_string
          in_string = true
          current += c
        elsif c == '"' && in_string
          in_string = false
          current += c
        elsif !in_string && (c == '{' || c == '[')
          depth += 1
          current += c
        elsif !in_string && (c == '}' || c == ']')
          depth -= 1
          current += c
        elsif !in_string && c == ',' && depth == 0
          parts << current
          current = ""
        else
          current += c
        end
      end
      parts << current unless current.empty?
      parts
    end
    # ── Gallery Picker ───────────────────────────────────────────────────────
    def self.pick_image_from_gallery(&block)
      callback_id = Mrboto.register_callback(&block)
      activity = Mrboto.current_activity
      return unless activity
      activity.call_java_method("pickImageFromGallery", callback_id)
    end

    def self.copy_selected_image(output_path)
      activity = Mrboto.current_activity
      return "" unless activity
      activity.call_java_method("copySelectedImageToCache", output_path).to_s
    end

    # ── QR Code Scanner ────────────────────────────────────────────────────
    def self.scan_qr_code(image_path)
      activity = Mrboto.current_activity
      return "[]" unless activity
      activity.call_java_method("scanQRCode", image_path).to_s
    end

    def self.generate_qr_code(text, output_path, size: 300)
      activity = Mrboto.current_activity
      return false unless activity
      result = activity.call_java_method("generateQRCode", text.to_s, output_path.to_s, size.to_i)
      result == true || result.to_s == "true"
    end
  end
end

# ── Top-level convenience methods ─────────────────────────────────────
def toast(message, duration = :short)
  Mrboto::Helpers.toast(message, duration)
end

def start_activity(class_name:, package: nil, extras: nil)
  Mrboto::Helpers.start_activity(class_name: class_name, package: package, extras: extras)
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

def popup_menu(view_id, items, &block)
  Mrboto::Helpers.popup_menu(view_id, items, &block)
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

# ── Top-level convenience: Intent Extras ──────────────────────────────
def get_extra_int(key)
  Mrboto::Helpers.get_extra_int(key)
end

def get_extra_bool(key)
  Mrboto::Helpers.get_extra_bool(key)
end

def get_extra_float(key)
  Mrboto::Helpers.get_extra_float(key)
end

def get_all_extras
  Mrboto::Helpers.get_all_extras
end

# ── Top-level convenience: Clipboard ──────────────────────────────────
def clipboard_copy(text)
  Mrboto::Helpers.clipboard_copy(text)
end

def clipboard_paste
  Mrboto::Helpers.clipboard_paste
end

def clipboard_has_text?
  Mrboto::Helpers.clipboard_has_text?
end

def clipboard_system_copy(text)
  Mrboto::Helpers.clipboard_system_copy(text)
end

def clipboard_system_paste
  Mrboto::Helpers.clipboard_system_paste
end

def clipboard_system_has_text?
  Mrboto::Helpers.clipboard_system_has_text?
end

# ── Top-level convenience: File Operations ────────────────────────────
def file_write(name, content)
  Mrboto::Helpers::FileOps.write(name, content)
end

def file_read(name)
  Mrboto::Helpers::FileOps.read(name)
end

def file_exists?(name)
  Mrboto::Helpers::FileOps.exists?(name)
end

def file_delete(name)
  Mrboto::Helpers::FileOps.delete(name)
end

def file_list(dir = "")
  Mrboto::Helpers::FileOps.list(dir)
end

def external_file_write(name, content)
  Mrboto::Helpers::FileOps.external_write(name, content)
end

def external_file_read(name)
  Mrboto::Helpers::FileOps.external_read(name)
end

def cache_write(name, content)
  Mrboto::Helpers::FileOps.cache_write(name, content)
end

def cache_read(name)
  Mrboto::Helpers::FileOps.cache_read(name)
end

def file_size(name)
  Mrboto::Helpers::FileOps.size(name)
end

# ── Top-level convenience: Permission ─────────────────────────────────
def permission_granted?(perm)
  Mrboto::Helpers.permission_granted?(perm)
end

def request_permission(perm)
  Mrboto::Helpers.request_permission(perm)
end

def request_permissions(perms)
  Mrboto::Helpers.request_permissions(perms)
end

# ── Top-level convenience: Notification ───────────────────────────────
def notify(id, title, message, channel: "default")
  Mrboto::Helpers.notify(id, title, message, channel: channel)
end

def notify_cancel(id)
  Mrboto::Helpers.notify_cancel(id)
end

def notify_big(id, title, big_text, channel: "default")
  Mrboto::Helpers.notify_big(id, title, big_text, channel: channel)
end

def notify_progress(id, title, message, progress, max, channel: "default")
  Mrboto::Helpers.notify_progress(id, title, message, progress, max, channel: channel)
end

# ── Top-level convenience: SQLite ─────────────────────────────────────
def sqlite_open(name)
  Mrboto::Helpers.sqlite_open(name)
end

# ── Top-level convenience: Network ────────────────────────────────────
def http_get(url, headers = {})
  Mrboto::Helpers.http_get(url, headers)
end

def http_post(url, body = "", headers = {})
  Mrboto::Helpers.http_post(url, body, headers)
end

def http_download(url, filepath)
  Mrboto::Helpers.http_download(url, filepath)
end

# ── Top-level convenience: Shell ───────────────────────────────────────
def shell_exec(cmd, timeout: 10)
  Mrboto::Helpers.shell_exec(cmd, timeout: timeout)
end

# ── Top-level convenience: Intent ──────────────────────────────────────
def intent_view(url)
  Mrboto::Helpers.intent_view(url)
end

def intent_send(text, subject: "")
  Mrboto::Helpers.intent_send(text, subject: subject)
end

def intent_action(action, data: "", type: "")
  Mrboto::Helpers.intent_action(action, data: data, type: type)
end

# ── Top-level convenience: Coroutine / Timer ───────────────────────────
def run_async(&block)
  Mrboto::Helpers.run_async(&block)
end

def run_delayed(ms, &block)
  Mrboto::Helpers.run_delayed(ms, &block)
end

def timer_start(interval_ms, &block)
  Mrboto::Helpers.timer_start(interval_ms, &block)
end

def timer_stop(timer_id)
  Mrboto::Helpers.timer_stop(timer_id)
end

def timer_once(delay_ms, &block)
  Mrboto::Helpers.timer_once(delay_ms, &block)
end

# ── Top-level convenience: Overlay ────────────────────────────────────
def overlay_show(x, y, width: -2, height: -2, view_id: nil)
  Mrboto::Helpers.overlay_show(x, y, width: width, height: height, view_id: view_id)
end

def overlay_remove(overlay_id)
  Mrboto::Helpers.overlay_remove(overlay_id)
end

def overlay_update_position(overlay_id, x, y)
  Mrboto::Helpers.overlay_update_position(overlay_id, x, y)
end

# ── Top-level convenience: File Encoding / Directory ───────────────────
def file_read_encoding(name, encoding)
  Mrboto::Helpers.file_read_encoding(name, encoding)
end

def file_write_encoding(name, content, encoding)
  Mrboto::Helpers.file_write_encoding(name, content, encoding)
end

def file_list_dir(path = "")
  Mrboto::Helpers.file_list_dir(path)
end

def file_mkdir(path)
  Mrboto::Helpers.file_mkdir(path)
end

def file_delete_dir(path)
  Mrboto::Helpers.file_delete_dir(path)
end

def file_exists_path(path)
  Mrboto::Helpers.file_exists_path(path)
end

def file_is_dir(path)
  Mrboto::Helpers.file_is_dir(path)
end

# ── Top-level convenience: Accessibility ───────────────────────────────
def accessibility_enabled?
  Mrboto::Helpers.accessibility_enabled?
end

# ── Top-level convenience: Screen Capture ──────────────────────────────
def capture_screen(out_path)
  Mrboto::Helpers.capture_screen(out_path)
end

def start_record_screen(out_path)
  Mrboto::Helpers.start_record_screen(out_path)
end

def stop_record_screen
  Mrboto::Helpers.stop_record_screen
end

# ── Top-level convenience: Gesture ─────────────────────────────────────
def gesture_click(x, y)
  Mrboto::Helpers.gesture_click(x, y)
end

def gesture_swipe(x1, y1, x2, y2, duration: 300)
  Mrboto::Helpers.gesture_swipe(x1, y1, x2, y2, duration: duration)
end

# ── Top-level convenience: Device Control ──────────────────────────────
def set_volume(stream_type, level, show_ui: false)
  Mrboto::Helpers.set_volume(stream_type, level, show_ui: show_ui)
end

def get_volume(stream_type)
  Mrboto::Helpers.get_volume(stream_type)
end

def set_brightness(level)
  Mrboto::Helpers.set_brightness(level)
end

def get_brightness
  Mrboto::Helpers.get_brightness
end

def vibrate(duration: 200)
  Mrboto::Helpers.vibrate(duration: duration)
end

def vibrate_pattern(pattern, repeat: -1)
  Mrboto::Helpers.vibrate_pattern(pattern, repeat: repeat)
end

# ── Top-level convenience: Sensor ──────────────────────────────────────
def start_gyroscope(&block)
  Mrboto::Helpers.start_gyroscope(&block)
end

def stop_gyroscope(sensor_id)
  Mrboto::Helpers.stop_gyroscope(sensor_id)
end

def start_accelerometer(&block)
  Mrboto::Helpers.start_accelerometer(&block)
end

def stop_accelerometer(sensor_id)
  Mrboto::Helpers.stop_accelerometer(sensor_id)
end

# ── Top-level convenience: Camera ──────────────────────────────────────
def camera_available?
  Mrboto::Helpers.camera_available?
end

def camera_info
  Mrboto::Helpers.camera_info
end

def camera_take_photo(&block)
  Mrboto::Helpers.camera_take_photo(&block)
end

def camera_record_video(&block)
  Mrboto::Helpers.camera_record_video(&block)
end

# ── Top-level convenience: Window Info ─────────────────────────────────
def current_activity_name
  Mrboto::Helpers.current_activity_name
end

def current_layout_info
  Mrboto::Helpers.current_layout_info
end

def top_activity_package
  Mrboto::Helpers.top_activity_package
end

# ── Top-level convenience: Threading / Atomic ──────────────────────────
def thread_start(&block)
  Mrboto::Helpers.thread_start(&block)
end

def atomic_get(counter_id)
  Mrboto::Helpers.atomic_get(counter_id)
end

def atomic_set(counter_id, value)
  Mrboto::Helpers.atomic_set(counter_id, value)
end

def atomic_increment(counter_id)
  Mrboto::Helpers.atomic_increment(counter_id)
end

# ── Top-level convenience: Enhanced HTTP ───────────────────────────────
def http_get_ex(url, headers: nil)
  Mrboto::Helpers.http_get_ex(url, headers: headers)
end

def http_post_ex(url, body, headers: nil)
  Mrboto::Helpers.http_post_ex(url, body, headers: headers)
end

def http_upload(url, file_path, field_name: "file")
  Mrboto::Helpers.http_upload(url, file_path, field_name: field_name)
end

# ── Top-level convenience: Color / Image ───────────────────────────────
def get_color_at(path, x, y)
  Mrboto::Helpers.get_color_at(path, x, y)
end

def find_color(path, color, region: "")
  Mrboto::Helpers.find_color(path, color, region: region)
end

def find_color_fuzzy(path, color, threshold: 32, region: "")
  Mrboto::Helpers.find_color_fuzzy(path, color, threshold: threshold, region: region)
end

def image_crop(path, x, y, w, h, out_path)
  Mrboto::Helpers.image_crop(path, x, y, w, h, out_path)
end

def image_scale(path, w, h, out_path)
  Mrboto::Helpers.image_scale(path, w, h, out_path)
end

def image_rotate(path, degrees, out_path)
  Mrboto::Helpers.image_rotate(path, degrees, out_path)
end

def image_to_base64(path, format: "png")
  Mrboto::Helpers.image_to_base64(path, format: format)
end

def image_from_base64(base64, out_path)
  Mrboto::Helpers.image_from_base64(base64, out_path)
end

# ── Top-level convenience: Event Listener ──────────────────────────────
def observe_battery(&block)
  Mrboto::Helpers.observe_battery(&block)
end

def observe_network(&block)
  Mrboto::Helpers.observe_network(&block)
end

def network_connected?
  Mrboto::Helpers.network_connected?
end

# ── Top-level convenience: OCR ──────────────────────────────────────
def ocr_init
  Mrboto::Helpers.ocr_init
end

def ocr_recognize(image_path)
  Mrboto::Helpers.ocr_recognize(image_path)
end

def ocr_detect(image_path)
  Mrboto::Helpers.ocr_detect(image_path)
end

def ocr_release
  Mrboto::Helpers.ocr_release
end

# ── Top-level convenience: Gallery Picker ────────────────────────────
def pick_image_from_gallery(&block)
  Mrboto::Helpers.pick_image_from_gallery(&block)
end

def copy_selected_image(output_path)
  Mrboto::Helpers.copy_selected_image(output_path)
end

# ── Top-level convenience: QR Code ────────────────────────────────────
def scan_qr_code(image_path)
  Mrboto::Helpers.scan_qr_code(image_path)
end

def generate_qr_code(text, output_path, size: 300)
  Mrboto::Helpers.generate_qr_code(text, output_path, size: size)
end
