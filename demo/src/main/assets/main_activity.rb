# main_activity.rb — Demo Ruby Activity using mrboto DSL
#
# This file is loaded by DemoActivity.kt which extends MrbotoActivityBase.
# It defines a Ruby class inheriting from Mrboto::Activity.

class MainActivity < Mrboto::Activity
  def on_create(bundle)
    super
    self.title = "mrboto Demo"

    self.content_view = linear_layout(
      orientation: :vertical,
      gravity: :center,
      padding: 24
    ) do
      text_view(
        text: "mrboto Framework",
        text_size: 28,
        text_color: "2196F3",
        gravity: :center,
        padding: 16
      )

      text_view(
        text: "100% Ruby Android Development",
        text_size: 16,
        gravity: :center,
        padding: 8
      )

      button(text: "Toast", padding: 12) {
        toast("Hello from Ruby!")
      }

      button(text: "Counter", padding: 12) {
        @counter ||= 0
        @counter += 1
        toast("Clicked #{@counter} times")
      }

      button(text: "Save to SharedPreferences", padding: 12) {
        sp = shared_preferences("demo_prefs")
        sp.put_string("last_action", "button_clicked")
        toast("Saved!")
      }

      button(text: "Read from SharedPreferences", padding: 12) {
        sp = shared_preferences("demo_prefs")
        val = sp.get_string("last_action", "(not set)")
        toast("Last action: #{val}")
      }
    end
  end
end
