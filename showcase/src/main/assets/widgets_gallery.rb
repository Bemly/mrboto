# widgets_gallery.rb — 展示 mrboto 支持的全部 44 种控件
# 每个 section 和关键控件都带有 Android 系统内置图标

class WidgetsGalleryActivity < Mrboto::Activity
  def on_create(bundle)
    super
    self.title = "Widgets Gallery"

    self.content_view = scroll_view do
      linear_layout(orientation: :vertical, padding: 12) do

        # ── Section: Text ────────────────────────────────────
        section_title("Text", "ic_menu_edit")

        text_view(text: "Large Text", text_size: 28, text_color: "2196F3", padding: 4,
                  compound_drawable_left: sys_drawable("ic_menu_edit"), drawable_padding: 8)
        text_view(text: "Medium Text", text_size: 20, text_color: "4CAF50", padding: 4,
                  compound_drawable_left: sys_drawable("ic_menu_sort_by_size"), drawable_padding: 8)
        text_view(text: "Small Text with hint", text_size: 12, hint: "This is a hint", padding: 4)

        text_view(
          text: "Centered Bold Text",
          text_size: 18,
          text_color: "FF5722",
          gravity: :center,
          padding: 8
        )

        # ── Section: Input ────────────────────────────────────
        section_title("Input", "ic_menu_edit")

        edit_text(text: "Edit text here", hint: "Type something...", text_size: 16, padding: 4,
                  compound_drawable_left: sys_drawable("ic_menu_edit"), drawable_padding: 8)
        edit_text(hint: "Number input", input_type: :number, text_size: 16, padding: 4,
                  compound_drawable_left: sys_drawable("ic_menu_zoom"), drawable_padding: 8)
        edit_text(hint: "Single line", single_line: true, text_size: 16, padding: 4)
        edit_text(hint: "Multi-line (max 3 lines)", max_lines: 3, text_size: 16, padding: 4)

        auto_complete_text_view(hint: "Auto-complete text view", padding: 4,
                  compound_drawable_left: sys_drawable("ic_menu_search"), drawable_padding: 8)
        search_view(hint: "Search...", padding: 4)
        number_picker(padding: 4)

        # ── Section: Buttons ──────────────────────────────────
        section_title("Buttons", "ic_menu_preferences")

        button(text: "Standard Button", padding: 8,
               compound_drawable_left: sys_drawable("ic_menu_save"), drawable_padding: 8) { toast("Standard Button clicked!") }
        material_button(text: "Material Button", padding: 8,
                        compound_drawable_left: sys_drawable("ic_menu_send"), drawable_padding: 8) { toast("Material Button clicked!") }
        floating_action_button(padding: 8)

        # ── Section: Switches & Checkboxes ────────────────────
        section_title("Switches & Checkboxes", "ic_menu_preferences")

        check_box(text: "Checkbox (checked)", padding: 4)
        check_box(text: "Checkbox (unchecked)", padding: 4)
        switch_widget(text: "Switch On", padding: 4)
        switch_widget(text: "Switch Off", padding: 4)

        # ── Section: Progress Indicators ──────────────────────
        section_title("Progress Indicators", "ic_menu_rotate")

        progress_bar(padding: 8)
        seek_bar(padding: 8)
        rating_bar(padding: 8)

        # ── Section: Date & Time ──────────────────────────────
        section_title("Date & Time", "ic_menu_today")

        date_picker(padding: 4)
        time_picker(padding: 4)
        calendar_view(padding: 4)
        text_view(text: "TextClock", text_size: 16, padding: 8,
                  compound_drawable_left: sys_drawable("ic_menu_recent_history"), drawable_padding: 8)
        chronometer(padding: 4)

        # ── Section: Image & Media ────────────────────────────
        section_title("Image & Media", "ic_menu_gallery")

        text_view(text: "ImageView", text_size: 14, padding: 4,
                  compound_drawable_left: sys_drawable("ic_menu_gallery"), drawable_padding: 8)
        image_view(padding: 4)

        text_view(text: "VideoView", text_size: 14, padding: 4,
                  compound_drawable_left: sys_drawable("ic_menu_play"), drawable_padding: 8)
        video_view(padding: 4)

        text_view(text: "WebView", text_size: 14, padding: 4,
                  compound_drawable_left: sys_drawable("ic_menu_help"), drawable_padding: 8)
        web_view(padding: 4)

        # ── Section: Containers ───────────────────────────────
        section_title("Containers", "ic_menu_agenda")

        linear_layout(orientation: :horizontal, padding: 4) do
          text_view(text: "  LinearLayout H", text_size: 14, padding: 4,
                    compound_drawable_left: sys_drawable("ic_menu_compose"), drawable_padding: 8)
          text_view(text: "  |  ", text_size: 14)
          text_view(text: "horizontal", text_size: 14, text_color: "4CAF50", padding: 4)
        end

        frame_layout(padding: 4) do
          text_view(text: "  FrameLayout (single child)", text_size: 14, gravity: :center, padding: 8,
                    compound_drawable_left: sys_drawable("ic_menu_view"), drawable_padding: 8)
        end

        relative_layout(padding: 4) do
          text_view(text: "  RelativeLayout", text_size: 14, gravity: :center, padding: 8,
                    compound_drawable_left: sys_drawable("ic_menu_crop"), drawable_padding: 8)
        end

        scroll_view(orientation: :vertical, padding: 4) do
          text_view(text: "  ScrollView", text_size: 14, padding: 8,
                    compound_drawable_left: sys_drawable("ic_menu_sort_by_size"), drawable_padding: 8)
        end

        nested_scroll_view(orientation: :vertical, padding: 4) do
          text_view(text: "  NestedScrollView", text_size: 14, padding: 8,
                    compound_drawable_left: sys_drawable("ic_menu_sort_by_size"), drawable_padding: 8)
        end

        horizontal_scroll_view(padding: 4) do
          linear_layout(orientation: :horizontal) do
            text_view(text: "  HorizontalScrollView", text_size: 14, padding: 8,
                      compound_drawable_left: sys_drawable("ic_menu_menu"), drawable_padding: 8)
          end
        end

        grid_view(padding: 4)
        list_view(padding: 4)

        table_layout(padding: 4) do
          text_view(text: "  TableLayout", text_size: 14, padding: 8,
                    compound_drawable_left: sys_drawable("ic_menu_agenda"), drawable_padding: 8)
        end

        view_pager(padding: 4)
        view_switcher(padding: 4)

        # ── Section: Material Design ──────────────────────────
        section_title("Material Design", "ic_menu_starred")

        card_view(padding: 4) do
          text_view(text: "  CardView content", text_size: 16, text_color: "6200EE", padding: 8,
                    compound_drawable_left: sys_drawable("ic_menu_starred"), drawable_padding: 8)
        end

        text_input_layout(hint: "Text Input Layout", padding: 4) do
          text_input_edit_text(text: "Inside TIL", text_size: 14, padding: 4)
        end

        bottom_navigation_view(padding: 4)
        app_bar_layout(padding: 4) do
          toolbar(title: "  Toolbar", padding: 4,
                  compound_drawable_left: sys_drawable("ic_menu_info_details"), drawable_padding: 8)
        end

        # ── Section: Navigation Containers ────────────────────
        section_title("Navigation Containers", "ic_menu_directions")

        # DrawerLayout requires EXACTLY measurement, can't render inside ScrollView
        text_view(text: "  DrawerLayout (requires exact sizing — use directly as content_view)",
                  text_size: 12, text_color: "9E9E9E", padding: 4)

        coordinator_layout(padding: 4) do
          text_view(text: "  CoordinatorLayout", text_size: 14, padding: 8,
                    compound_drawable_left: sys_drawable("ic_menu_manage"), drawable_padding: 8)
        end

        navigation_view(padding: 4)

        # ── Section: Selectors ────────────────────────────────
        section_title("Selectors", "ic_menu_search")

        spinner(padding: 4)
        radio_group(orientation: :vertical, padding: 4)

        # ── Footer ────────────────────────────────────────────
        text_view(
          text: "— End of 44 Widgets Gallery —",
          text_size: 14,
          gravity: :center,
          padding: 16,
          text_color: "9E9E9E"
        )
      end
    end
  end

  def section_title(text, icon_name = nil)
    attrs = {
      text: "  #{text}",
      text_size: 18,
      text_color: "6200EE",
      padding: [8, 12, 4, 4]
    }
    if icon_name
      res_id = sys_drawable(icon_name)
      attrs[:compound_drawable_left] = res_id
      attrs[:drawable_padding] = 8
    end
    text_view(attrs)
  end

  # Look up an android.R.drawable system resource ID
  def sys_drawable(name)
    Mrboto.android_sys_id(name, "drawable")
  end
end

Mrboto.register_activity_class(WidgetsGalleryActivity)
