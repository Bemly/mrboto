# editor_activity.rb — 100% Ruby 代码编辑器 + 运行器
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

class EditorActivity < Mrboto::Activity
  SCRIPT_PATH = "editor_script.rb"

  def on_create(bundle)
    super
    self.title = "Ruby Editor"
    @dark_mode = true

    build_ui
    load_saved_code
  end

  def build_ui
    # ── 代码编辑区 ──
    @editor_scroll = scroll_view
    @editor_widget = edit_text(
      hint: "在此输入 Ruby 代码...",
      text_size: 14,
      padding: 12,
      max_lines: 9999,
      single_line: false
    )
    @editor_scroll.add_child(@editor_widget)
    @editor_widget.call_java_method('setGravity', 51) # Gravity::TOP | Gravity::LEFT

    # ── 输出区域 ──
    @output_scroll = scroll_view
    @output_widget = text_view(
      text: "输出结果将显示在这里",
      text_size: 13,
      padding: 12,
      gravity: :top
    )
    @output_scroll.add_child(@output_widget)

    # ── 主题切换栏 ──
    @theme_btn = material_button(text: "☀ 浅色", padding: 8) { toggle_theme }
    @title_widget = text_view(
      text: "Mrboto Ruby Editor",
      text_size: 11,
      gravity: :center,
      padding: 8
    )
    theme_row = linear_layout(orientation: :horizontal, padding: 8)
    theme_row.add_child(@theme_btn)
    theme_row.add_child(@title_widget)
    theme_row.gravity = :center

    # ── 液态玻璃底部栏 ──
    @glass_bar = liquid_glass_view(
      shape_type: "rounded_rect",
      corner_radius: 24.0,
      blur_radius: 25.0,
      vibrancy: true
    )
    @btn_row = linear_layout(orientation: :horizontal, gravity: :center, padding: 12)
    @btn_run = material_button(text: "▶ Run", padding: 12) { run_code }
    @btn_save = material_button(text: "💾 Save", padding: 12) { save_code }
    @btn_load = material_button(text: "📂 Load", padding: 12) { load_code }
    @btn_clear = material_button(text: "✕ Clear", padding: 12) { clear_code }
    @btn_row.add_child(@btn_run)
    @btn_row.add_child(@btn_save)
    @btn_row.add_child(@btn_load)
    @btn_row.add_child(@btn_clear)
    @glass_bar.add_child(@btn_row)

    apply_theme_colors
  end

  def run_code
    code = @editor_widget.text.to_s
    return if code.strip.empty?

    # 清空上次输出
    @output_widget.text = "执行中..."

    begin
      result = Mrboto._eval(code)
      output = if result.nil?
                 "(nil)"
               elsif result.is_a?(String)
                 result
               else
                 result.to_s
               end
      @output_widget.text = output
    rescue => e
      msg = e.message.to_s
      bt = e.backtrace ? e.backtrace.first(3).join("\n") : ""
      @output_widget.text = "错误: #{e.class}\n#{msg}\n\n#{bt}"
    end
  end

  def save_code
    code = @editor_widget.text.to_s
    begin
      file_write(SCRIPT_PATH, code)
      toast("已保存")
    rescue => e
      toast("保存失败: #{e.message}")
    end
  end

  def load_code
    begin
      if file_exists?(SCRIPT_PATH)
        code = file_read(SCRIPT_PATH)
        @editor_widget.text = code
        toast("已加载")
      else
        toast("没有保存的脚本")
      end
    rescue => e
      toast("加载失败: #{e.message}")
    end
  end

  def clear_code
    @editor_widget.text = ""
    @output_widget.text = ""
  end

  def toggle_theme
    @dark_mode = !@dark_mode
    @theme_btn.text = @dark_mode ? "☀ 浅色" : "☾ 深色"
    apply_theme_colors
  end

  def apply_theme_colors
    if @dark_mode
      # 深色主题 — Catppuccin Mocha 风格
      @editor_widget.background_color = "1E1E2E"
      @editor_widget.text_color = "CDD6F4"
      @output_widget.background_color = "181825"
      @output_widget.text_color = "A6ADC8"
      @title_widget.text_color = "CDD6F4"
    else
      # 浅色主题
      @editor_widget.background_color = "FAFAFA"
      @editor_widget.text_color = "1A1A2E"
      @output_widget.background_color = "F0F0F5"
      @output_widget.text_color = "4A4A6A"
      @title_widget.text_color = "1A1A2E"
    end
  end
end

Mrboto.register_activity_class(EditorActivity)
