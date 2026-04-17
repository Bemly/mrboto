# mrboto/activity.rb — Mrboto::Activity with lifecycle hooks and content_view setter
#
# Defines the Activity base class that Ruby developers inherit from.
# All Android lifecycle callbacks are available as snake_case Ruby methods.

module Mrboto
  class Activity < JavaObject
    attr_accessor :bundle

    def initialize(java_activity_registry_id)
      @_registry_id = java_activity_registry_id
      @_java_activity_id = java_activity_registry_id
      @bundle = nil
      @content_view = nil
    end

    # Override to also call native setContentView
    def content_view=(view)
      @content_view = view
      return unless view.is_a?(View) && view._registry_id
      Mrboto._set_content_view(@_registry_id, view._registry_id)
    end

    attr_reader :content_view

    # ── Lifecycle Hooks (override in subclasses) ───────────────────
    def on_create(bundle = nil)
      @bundle = bundle
      # Note: do NOT call super — JavaObject has no on_create method
    end

    def on_start; end
    def on_resume; end
    def on_pause; end
    def on_stop; end
    def on_destroy; end
    def on_restart; end
    def on_post_create(bundle = nil); end

    # ── Convenience Methods ────────────────────────────────────────

    # Set the content view of this Activity.
    # Accepts a View wrapper (e.g. from linear_layout { ... }).
    def set_content_view(view)
      @content_view = view
      return unless view.is_a?(View) && view._registry_id
      Mrboto._set_content_view(@_registry_id, view._registry_id)
    end

    # Find a view by its Android resource ID
    def find_view_by_id(id)
      view = call_java_method("findViewById", id)
      view ? Mrboto::View.from_java(view) : nil
    end

    # Get the Activity title
    def title=(text)
      call_java_method("setTitle", text.to_s)
    end

    def title
      call_java_method("getTitle")
    end

    # Get the application package name
    def package_name
      Mrboto._package_name
    end

    # Get the app's internal files directory path
    def files_dir
      call_java_method("getFilesDir").call_java_method("getAbsolutePath")
    end

    # Bridge to Kotlin side's MrbotoActivityBase.setViewClickListener
    def setViewClickListener(view_registry_id, callback_id)
      call_java_method("setViewClickListener", view_registry_id, callback_id)
    end

    # Bridge to Kotlin side's MrbotoActivityBase.setTextWatcher
    def setTextWatcher(view_registry_id, callback_id)
      call_java_method("setTextWatcher", view_registry_id, callback_id)
    end

    # ── Dialog / Snackbar / PopupMenu ──────────────────────────────

    def show_dialog(title, message, buttons = nil)
      btn_str = if buttons.is_a?(Array)
                  "[" + buttons.map { |b| "\"#{b.to_s.gsub('"', '\\"')}\"" }.join(",") + "]"
                else
                  nil
                end
      call_java_method("showDialog", title.to_s, message.to_s, btn_str)
    end

    def show_snackbar(view, message, duration = :short)
      dur = duration == :long ? 1 : 0
      vid = view.is_a?(View) ? view._registry_id : view
      call_java_method("showSnackbar", vid, message.to_s, dur)
    end

    def show_popup_menu(view, items, &block)
      return unless items.is_a?(Array)
      callback_id = block ? Mrboto.register_callback(&block) : 0
      vid = view.is_a?(View) ? view._registry_id : view
      items_json = "[" + items.map { |i| "\"#{i.to_s.gsub('"', '\\"')}\"" }.join(",") + "]"
      call_java_method("showPopupMenu", vid, items_json, callback_id)
    end
  end
end
