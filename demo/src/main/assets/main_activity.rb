# main_activity.rb — Demo Ruby Activity using mrboto DSL
#
# This file is loaded by DemoActivity.kt which extends MrbotoActivityBase.
# It defines a Ruby class inheriting from Mrboto::Activity.
# Kotlin side handles instantiation and lifecycle dispatch.

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
    end
  end
end

# Tell Kotlin which class to instantiate
Mrboto._ruby_activity_class = MainActivity
