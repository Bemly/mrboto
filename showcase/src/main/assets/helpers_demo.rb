# helpers_demo.rb — 展示 mrboto 辅助 API：Toast/Dialog/Snackbar/PopupMenu/动画

class HelpersDemoActivity < Mrboto::Activity
  def on_create(bundle)
    super
    self.title = "Helpers Demo"

    self.content_view = scroll_view do
      linear_layout(orientation: :vertical, padding: 12) do

        # ── Section: Toast ────────────────────────────────────
        section_title("Toast")

        button(text: "Show Toast", padding: 12) {
          toast("Hello from mrboto!")
        }
        button(text: "Show Long Toast", padding: 12) {
          toast("This is a longer message", :long)
        }

        # ── Section: Dialog ───────────────────────────────────
        section_title("Dialog")

        button(text: "Show Dialog", padding: 12) {
          show_dialog("Alert", "This is a dialog message from mrboto.", ["OK", "Cancel"])
        }

        # ── Section: Snackbar ─────────────────────────────────
        section_title("Snackbar")

        @snack_target = text_view(
          text: "Snackbar target (click button below)",
          text_size: 14,
          text_color: "4CAF50",
          gravity: :center,
          padding: 12
        )

        button(text: "Show Snackbar", padding: 12) {
          show_snackbar(@snack_target, "Snackbar message!", :short)
        }
        button(text: "Show Long Snackbar", padding: 12) {
          show_snackbar(@snack_target, "This is a long snackbar message", :long)
        }

        # ── Section: PopupMenu ────────────────────────────────
        section_title("PopupMenu")

        @popup_target = button(text: "Show Popup Menu", padding: 12)

        # ── Section: View Animations ──────────────────────────
        section_title("View Animations")

        @anim_target = text_view(
          text: "Animation Target",
          text_size: 20,
          text_color: "FF5722",
          gravity: :center,
          padding: 24
        )

        button(text: "Fade In", padding: 8) {
          @anim_target.fade_in
        }
        button(text: "Fade Out", padding: 8) {
          @anim_target.fade_out
        }
        button(text: "Slide In Bottom", padding: 8) {
          @anim_target.slide_in_bottom(500)
        }
        button(text: "Pulse", padding: 8) {
          @anim_target.pulse(1.3, 300)
        }
        button(text: "Translate", padding: 8) {
          @anim_target.animate_translate(0.0, 0.0, 100.0, 0.0, 500)
        }
        button(text: "Scale", padding: 8) {
          @anim_target.animate_scale(1.0, 1.0, 1.5, 1.5, 400)
        }
        button(text: "Clear Animation", padding: 8) {
          @anim_target.clear_animation
        }

        # ── Section: View info ────────────────────────────────
        section_title("View Info")

        button(text: "Show Size", padding: 8) {
          toast("Target size: #{@anim_target.width} x #{@anim_target.height}")
        }
        button(text: "Request Focus", padding: 8) {
          @anim_target.request_focus
        }
        button(text: "Perform Click", padding: 8) {
          @anim_target.perform_click
        }

        # ── Section: Run on UI Thread ─────────────────────────
        section_title("Run on UI Thread")

        @ui_thread_result = text_view(
          text: "Click to run on UI thread",
          text_size: 14,
          gravity: :center,
          padding: 8
        )

        button(text: "Run on UI Thread", padding: 12) {
          run_on_ui_thread {
            @ui_thread_result.text = "Executed on UI thread!"
            @ui_thread_result.text_color = "4CAF50"
          }
        }

        # ── Section: Resource Helper ──────────────────────────
        section_title("Resource & Package")

        button(text: "Show Package Name", padding: 12) {
          toast("Package: #{package_name}")
        }

        # ── Footer ────────────────────────────────────────────
        text_view(
          text: "— End of Helpers Demo —",
          text_size: 14,
          gravity: :center,
          padding: 16,
          text_color: "9E9E9E"
        )
      end
    end
  end

  def section_title(text)
    text_view(
      text: text,
      text_size: 18,
      text_color: "6200EE",
      padding: [8, 12, 4, 4]
    )
  end
end

Mrboto._ruby_activity_class = HelpersDemoActivity
