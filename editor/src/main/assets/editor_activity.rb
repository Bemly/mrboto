# editor_activity.rb — 100% Ruby Compose 代码编辑器 + 运行器
# 使用液态玻璃底部栏，支持代码/文件/日志三个页面

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
    @log_entries = []
    @active_tab = :code
    begin
      build_ui
      _log("build_ui completed successfully")
    rescue => e
      _log("build_ui ERROR: #{e.class}: #{e.message}")
      if e.respond_to?(:backtrace) && e.backtrace
        e.backtrace.first(5).each { |l| _log("  #{l}") }
      end
    end
  end

  def _log(msg)
    call_java_method("logDebug", "EditorActivity", msg.to_s) rescue puts(msg)
  end

  def add_log(msg)
    @log_entries << "[#{Time.now.strftime('%H:%M:%S')}] #{msg}"
  end

  def build_ui
    _log("build_ui: starting")
    Mrboto::ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])
    Mrboto::ComposeBuilder.instance_variable_set(:@_compose_root, nil)
    _log("build_ui: stack cleared")

    glass_bar(
      shape_type: :rounded_rect,
      corner_radius: 24.0,
      blur_radius: 25.0,
      vibrancy: true
    ) {

      # ── 页面内容 ──────────────────────────────
      case @active_tab
      when :code
        column(fill_max_width: true) {
          text("代码", font_size: 13, font_family: :monospace)
          outlined_text_field(
            @code,
            hint: "在此输入 Ruby 代码...",
            single_line: false,
            max_lines: 30,
            modifier: background_color(@dark_mode ? "1E1E2E" : "FFFFFF")
          ) { |v| @code = v }
          divider
          text("输出", font_size: 13, font_family: :monospace)
          text(
            @output,
            font_size: 12,
            font_family: :monospace,
            modifier: background_color(@dark_mode ? "181825" : "F0F0F5").then(padding(8))
          )
        }
      when :file
        column(fill_max_width: true, padding: 16) {
          text("文件", font_size: 16)
          spacer(height: 12)
          text("脚本路径: #{SCRIPT_PATH}", font_size: 13)
          spacer(height: 16)
          button("保存") { save_code }
          spacer(height: 8)
          button("加载") { load_code }
        }
      when :log
        vertical_scroll(fill_max_width: true) {
          column(fill_max_width: true, padding: 16) {
            text("日志", font_size: 16)
            spacer(height: 8)
            @log_entries.each do |entry|
              text(entry, font_size: 12, font_family: :monospace)
              spacer(height: 4)
            end
          }
        }
      end

      # ── 底部导航栏 ──────────────────────────────
      # 左侧一个大 glass_cell，内含 3 个 nav_cell 平分宽度
      glass_cell {
        nav_cell(icon: :ic_menu_code, content: "代码") { switch_tab(:code) }
        nav_cell(icon: :ic_menu_file, content: "文件") { switch_tab(:file) }
        nav_cell(icon: :ic_menu_log, content: "日志") { switch_tab(:log) }
      }

      # 右侧一个独立的 glass_cell
      right_cell {
        glass_cell {
          nav_cell(icon: :ic_menu_search, content: "清除") { clear_code }
        }
      }
    }
    _log("build_ui: tree built, root=#{Mrboto::ComposeBuilder.root ? Mrboto::ComposeBuilder.root["type"] : "nil"}")

    set_compose_content
    _log("build_ui: set_compose_content called")
  end

  def switch_tab(tab)
    @active_tab = tab
    refresh_ui
  end

  def run_code
    code = @code.to_s
    return if code.strip.empty?

    add_log("执行代码")
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
      add_log("执行成功: #{@output}")
    rescue => e
      msg = e.message.to_s
      bt = e.backtrace ? e.backtrace.first(3).join("\n") : ""
      @output = "错误: #{e.class}\n#{msg}\n\n#{bt}"
      add_log("执行失败: #{e.class}: #{msg}")
    end

    refresh_ui
  end

  def save_code
    begin
      file_write(SCRIPT_PATH, @code.to_s)
      add_log("文件已保存: #{SCRIPT_PATH}")
      toast("已保存")
    rescue => e
      add_log("保存失败: #{e.message}")
      toast("保存失败: #{e.message}")
    end
    refresh_ui
  end

  def load_code
    begin
      if file_exists?(SCRIPT_PATH)
        @code = file_read(SCRIPT_PATH)
        add_log("已加载脚本: #{SCRIPT_PATH}")
        toast("已加载")
      else
        add_log("没有保存的脚本")
        toast("没有保存的脚本")
      end
    rescue => e
      add_log("加载失败: #{e.message}")
      toast("加载失败: #{e.message}")
    end
    refresh_ui
  end

  def clear_code
    @code = ""
    @output = ""
    add_log("代码已清除")
    refresh_ui
  end

  def refresh_ui
    Mrboto::ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])
    Mrboto::ComposeBuilder.instance_variable_set(:@_compose_root, nil)
    build_ui
  end
end

Mrboto.register_activity_class(EditorActivity)
