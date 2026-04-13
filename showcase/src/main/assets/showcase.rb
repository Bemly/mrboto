# showcase.rb — mrboto UI API Showcase 主菜单
#
# mrboto 框架的完整演示入口，展示所有 44 种控件和辅助 API

class ShowcaseMenu < Mrboto::Activity
  def on_create(bundle)
    super
    self.title = "mrboto Showcase"

    self.content_view = scroll_view(orientation: :vertical) do
      linear_layout(orientation: :vertical, gravity: :center, padding: 16) do

        text_view(
          text: "mrboto UI Showcase",
          text_size: 24,
          text_color: "6200EE",
          gravity: :center,
          padding: 16
        )

        text_view(
          text: "44 widgets + Dialog + Snackbar + PopupMenu + Animations",
          text_size: 14,
          gravity: :center,
          padding: 8
        )

        # ── 主菜单按钮 ──────────────────────────────────────
        material_button(text: "Widgets Gallery", padding: 16, gravity: :center) {
          open_ruby("widgets_gallery.rb")
        }

        material_button(text: "Helpers Demo", padding: 16, gravity: :center) {
          open_ruby("helpers_demo.rb")
        }

        material_button(text: "Shared Preferences", padding: 16, gravity: :center) {
          open_ruby("shared_prefs_demo.rb")
        }
      end
    end
  end

  # Open a Ruby Activity via Intent extra
  def open_ruby(script_path)
    act = Mrboto.current_activity
    return if act.nil?
    # Use call_java_method to start activity with script path
    Mrboto._call_java_method(act._registry_id, 'startRubyActivity', script_path)
  end
end

Mrboto._ruby_activity_class = ShowcaseMenu
