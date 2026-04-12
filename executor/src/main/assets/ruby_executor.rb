# ruby_executor.rb — mruby demo with terminal output and demo buttons
#
# UI matches the layout from activity_main.xml:
#   - Version label at top
#   - Scrollable terminal output (black bg, green monospace text)
#   - Demo buttons: arithmetic, strings, arrays, fibonacci, hashes,
#     syntax error, runtime error, multi-eval, gc, clear

class ExecutorActivity < Mrboto::Activity
  def on_create(bundle)
    super
    self.title = "mrboto Demo"

    # Get mruby version
    @version = Mrboto._eval("RUBY_VERSION") rescue "unknown"

    self.content_view = linear_layout(orientation: :vertical, padding: 16) {
      # Version label
      @version_label = text_view(
        text: "mruby version: #{@version}",
        text_size: 14,
        padding: 0
      )

      # Scrollable output area
      scroll_view {
        @output = text_view(
          text_size: 13,
          text_color: "00FF00",
          background_color: "1E1E1E",
          padding: 12
        )
      }

      # Button row 1: 算术  字符串  数组  斐波那契
      linear_layout(orientation: :horizontal, padding: 0) {
        button(text: "算术", padding: 4) { run_demo("arithmetic.rb") }
        button(text: "字符串", padding: 4) { run_demo("strings.rb") }
        button(text: "数组", padding: 4) { run_demo("arrays.rb") }
        button(text: "斐波那契", padding: 4) { run_demo("fibonacci.rb") }
      }

      # Button row 2: 哈希  语法错误  运行错误
      linear_layout(orientation: :horizontal, padding: 0) {
        button(text: "哈希", padding: 4) { run_demo("hashes.rb") }
        button(text: "语法错误", padding: 4) { run_demo("syntax_error.rb") }
        button(text: "运行错误", padding: 4) { run_demo("runtime_error.rb") }
      }

      # Button row 3: 多步骤  GC  清除
      linear_layout(orientation: :horizontal, padding: 0) {
        button(text: "多步骤", padding: 4) { run_demo("multi_eval.rb") }
        button(text: "GC", padding: 4) { run_gc }
        button(text: "清除", padding: 4) { @output.text = "" }
      }
    }

    @output.text = "点击按钮执行嵌入的 Ruby 脚本。\n\n"
  end

  # Load and run a demo script from assets, display result
  def run_demo(script_name)
    @output.append_text("─── #{script_name} ───\n")
    begin
      result = call_java_method("loadAssetScript", script_name)
      if result.nil? || result.to_s.empty?
        @output.append_text("  (no output)\n\n")
      else
        @output.append_text("  #{result}\n\n")
      end
    rescue => e
      @output.append_text("  Error: #{e.class}: #{e.message}\n\n")
    end
  end

  # Run garbage collection and display stats
  def run_gc
    @output.append_text("─── GC ───\n")
    begin
      Mrboto._eval("GC.start")
      @output.append_text("  GC started\n\n")
    rescue => e
      @output.append_text("  Error: #{e.class}: #{e.message}\n\n")
    end
  end
end

Mrboto._ruby_activity_class = ExecutorActivity
