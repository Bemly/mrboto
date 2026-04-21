# editor_activity.rb — 100% Ruby Compose 代码编辑器 + 运行器
# 使用液态玻璃底部栏，支持深色/浅色主题切换

DEFAULT_CODE = <<'RUBY'
# Ruby Editor — 编写并直接运行
# 点击 ▶ Run 执行代码

# 示例：
toast("Hello from Ruby Editor!")

(1..5).each do |i|
  puts "Count: #{i}"
end

result = "Ruby is awesome!"
result
RUBY

class EditorActivity < Mrboto::ComposeActivity
  SCRIPT_PATH = "editor_script.rb"

  def on_create(bundle)
    super
    self.title = "Ruby Editor"
    @dark_mode = true
    @code = DEFAULT_CODE
    @output = ""
    build_ui
  end

  def build_ui
    # ── 清除之前的树栈 ──
    Mrboto::ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])

    scaffold(
      top_bar: -> { top_app_bar("Ruby Editor", actions: [
        { icon: @dark_mode ? "light_mode" : "dark_mode", on_click: -> { toggle_theme } }
      ]) },
      bottom_bar: -> {
        liquid_glass_view(
          shape_type: "rounded_rect",
          corner_radius: 24.0,
          blur_radius: 25.0,
          vibrancy: true
        ) {
          row(horizontal_arrangement: :space_evenly, padding: 8) {
            button("▶ Run") { run_code }
            button("💾 Save") { save_code }
            button("📂 Load") { load_code }
            button("✕ Clear") { clear_code }
          }
        }
      }
    ) {
      column(fill_max_width: true) {
        # 代码编辑区
        text("代码", font_size: 13, font_family: :monospace)

        outlined_text_field(
          @code,
          hint: "在此输入 Ruby 代码...",
          single_line: false,
          max_lines: 9999,
          modifier: fill_max_height(0.55).then(background_color(@dark_mode ? "1E1E2E" : "FFFFFF"))
        ) { |v| @code = v }

        divider

        # 输出区
        text("输出", font_size: 13, font_family: :monospace)

        text(
          @output,
          font_size: 12,
          font_family: :monospace,
          modifier: fill_max_height(0.3).then(background_color(@dark_mode ? "181825" : "F0F0F5")).then(padding(8))
        )
      }
    }

    set_compose_content
    apply_theme_colors
  end

  def run_code
    code = @code.to_s
    return if code.strip.empty?

    @output = "执行中..."
    refresh_ui

    begin
      result = Mrboto._eval(code)
      @output = if result.nil?
                  "(nil)"
                elsif result.is_a?(String)
                  result
                else
                  result.to_s
                end
    rescue => e
      msg = e.message.to_s
      bt = e.backtrace ? e.backtrace.first(3).join("\n") : ""
      @output = "错误: #{e.class}\n#{msg}\n\n#{bt}"
    end

    refresh_ui
  end

  def save_code
    begin
      file_write(SCRIPT_PATH, @code.to_s)
      toast("已保存")
    rescue => e
      toast("保存失败: #{e.message}")
    end
  end

  def load_code
    begin
      if file_exists?(SCRIPT_PATH)
        @code = file_read(SCRIPT_PATH)
        toast("已加载")
      else
        toast("没有保存的脚本")
      end
    rescue => e
      toast("加载失败: #{e.message}")
    end
    refresh_ui
  end

  def clear_code
    @code = ""
    @output = ""
    refresh_ui
  end

  def toggle_theme
    @dark_mode = !@dark_mode
    refresh_ui
    apply_theme_colors
  end

  def refresh_ui
    # Rebuild the entire UI to reflect state changes
    Mrboto::ComposeBuilder.stack.clear
    build_ui
  end

  def apply_theme_colors
    # Colors are baked into the Compose tree via build_ui
    # No native View manipulation needed in Compose mode
  end
end

Mrboto.register_activity_class(EditorActivity)
