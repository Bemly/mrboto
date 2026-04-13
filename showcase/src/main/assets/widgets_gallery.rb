# widgets_gallery.rb — 展示 mrboto 支持的全部 44 种控件

class WidgetsGalleryActivity < Mrboto::Activity
  def on_create(bundle)
    super
    self.title = "Widgets Gallery"

    self.content_view = scroll_view do
      linear_layout(orientation: :vertical, padding: 12) do

        # ── Section: Text ────────────────────────────────────
        section_title("Text")

        text_view(text: "Large Text", text_size: 28, text_color: "2196F3", padding: 4)
        text_view(text: "Medium Text", text_size: 20, text_color: "4CAF50", padding: 4)
        text_view(text: "Small Text with hint", text_size: 12, hint: "This is a hint", padding: 4)

        text_view(
          text: "Centered Bold Text",
          text_size: 18,
          text_color: "FF5722",
          gravity: :center,
          padding: 8
        )

        # ── Section: Input ────────────────────────────────────
        section_title("Input")

        edit_text(text: "Edit text here", hint: "Type something...", text_size: 16, padding: 4)
        edit_text(hint: "Number input", input_type: :number, text_size: 16, padding: 4)
        edit_text(hint: "Single line", single_line: true, text_size: 16, padding: 4)
        edit_text(hint: "Multi-line (max 3 lines)", max_lines: 3, text_size: 16, padding: 4)

        auto_complete_text_view(hint: "Auto-complete text view", padding: 4)
        search_view(hint: "Search...", padding: 4)

        number_picker(padding: 4)

        # ── Section: Buttons ──────────────────────────────────
        section_title("Buttons")

        button(text: "Standard Button", padding: 8) { toast("Standard Button clicked!") }
        material_button(text: "Material Button", padding: 8) { toast("Material Button clicked!") }
        floating_action_button(padding: 8)

        # ── Section: Switches & Checkboxes ────────────────────
        section_title("Switches & Checkboxes")

        check_box(text: "Checkbox (checked)", padding: 4)
        check_box(text: "Checkbox (unchecked)", padding: 4)
        switch_widget(text: "Switch On", padding: 4)
        switch_widget(text: "Switch Off", padding: 4)

        # ── Section: Progress Indicators ──────────────────────
        section_title("Progress Indicators")

        progress_bar(padding: 8)
        seek_bar(padding: 8)
        rating_bar(padding: 8)

        # ── Section: Date & Time ──────────────────────────────
        section_title("Date & Time")

        date_picker(padding: 4)
        time_picker(padding: 4)
        calendar_view(padding: 4)
        text_clock(padding: 4)
        chronometer(padding: 4)

        # ── Section: Image & Media ────────────────────────────
        section_title("Image & Media")

        text_view(text: "ImageView (no resource set)", text_size: 14, padding: 4)
        image_view(padding: 4)

        text_view(text: "VideoView (no path set)", text_size: 14, padding: 4)
        video_view(padding: 4)

        text_view(text: "WebView (empty)", text_size: 14, padding: 4)
        web_view(padding: 4)

        # ── Section: Containers ───────────────────────────────
        section_title("Containers")

        linear_layout(orientation: :horizontal, padding: 4) do
          text_view(text: "LinearLayout H", text_size: 14, padding: 4)
          text_view(text: "  |  ", text_size: 14)
          text_view(text: "horizontal", text_size: 14, text_color: "4CAF50", padding: 4)
        end

        frame_layout(padding: 4) do
          text_view(text: "FrameLayout (single child)", text_size: 14, gravity: :center, padding: 8)
        end

        relative_layout(padding: 4) do
          text_view(text: "RelativeLayout", text_size: 14, gravity: :center, padding: 8)
        end

        scroll_view(orientation: :vertical, padding: 4) do
          text_view(text: "ScrollView (empty)", text_size: 14, padding: 8)
        end

        nested_scroll_view(orientation: :vertical, padding: 4) do
          text_view(text: "NestedScrollView (empty)", text_size: 14, padding: 8)
        end

        horizontal_scroll_view(padding: 4) do
          linear_layout(orientation: :horizontal) do
            text_view(text: "H1  ", text_size: 14)
            text_view(text: "H2  ", text_size: 14)
            text_view(text: "H3  ", text_size: 14)
          end
        end

        grid_view(padding: 4)
        list_view(padding: 4)

        table_layout(padding: 4) do
          text_view(text: "TableLayout (empty)", text_size: 14, padding: 8)
        end

        view_pager(padding: 4)
        view_switcher(padding: 4)

        # ── Section: Material Design ──────────────────────────
        section_title("Material Design")

        card_view(padding: 4) do
          text_view(text: "CardView content", text_size: 16, text_color: "6200EE", padding: 8)
        end

        text_input_layout(hint: "Text Input Layout", padding: 4) do
          text_input_edit_text(text: "Inside TIL", text_size: 14, padding: 4)
        end

        bottom_navigation_view(padding: 4)
        app_bar_layout(padding: 4) do
          toolbar(title: "Toolbar", padding: 4)
        end

        # ── Section: Navigation Containers ────────────────────
        section_title("Navigation Containers")

        drawer_layout(padding: 4) do
          text_view(text: "DrawerLayout (content)", text_size: 14, padding: 8)
        end

        coordinator_layout(padding: 4) do
          text_view(text: "CoordinatorLayout (empty)", text_size: 14, padding: 8)
        end

        navigation_view(padding: 4)

        # ── Section: Selectors ────────────────────────────────
        section_title("Selectors")

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

  def section_title(text)
    text_view(
      text: text,
      text_size: 18,
      text_color: "6200EE",
      padding: [8, 12, 4, 4]
    )
  end
end

Mrboto._ruby_activity_class = WidgetsGalleryActivity
