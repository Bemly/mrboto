# mrboto/activity.rb — Mrboto::Activity with lifecycle hooks and content_view setter
#
# Defines the Activity base class that Ruby developers inherit from.
# All Android lifecycle callbacks are available as snake_case Ruby methods.

module Mrboto
  class Activity < JavaObject
    attr_accessor :bundle, :content_view

    # ── Lifecycle Hooks (override in subclasses) ───────────────────
    def on_create(bundle = nil)
      @bundle = bundle
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
      if view.respond_to?(:java_object) && view.java_object
        # Call setContentView on the actual Activity
        activity_obj = Mrboto.current_activity
        if activity_obj && activity_obj.respond_to?(:call_java_method)
          activity_obj.call_java_method("setContentView", view.java_object)
        end
      end
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
  end
end
