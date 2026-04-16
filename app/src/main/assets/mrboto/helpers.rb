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
