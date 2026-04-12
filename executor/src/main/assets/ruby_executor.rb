# ruby_executor.rb — mruby demo with terminal output, demo buttons,
# custom script input, and external .rb file import
# All logic is 100% Ruby — Kotlin side only provides framework methods.

class ExecutorActivity < Mrboto::Activity
  def on_create(bundle)
    super
    self.title = "mrboto Demo"

    # Get mruby version
    @version = Mrboto._eval("RUBY_VERSION") rescue "unknown"

    self.content_view = linear_layout(orientation: :vertical, padding: 16) {
      # Version label
      text_view(
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

      # Separator
      text_view(text: "────────────────", text_size: 10, padding: 4)

      # Custom script input
      @script_input = edit_text(
        hint: "Ruby code or file path...",
        text_size: 13,
        padding: 4,
        single_line: true
      )

      # Button row 4: 执行  导入文件
      linear_layout(orientation: :horizontal, padding: 0) {
        button(text: "执行", padding: 4) { run_custom }
        button(text: "导入文件", padding: 4) { import_file }
      }
    }

    @output.text = "点击按钮执行嵌入的 Ruby 脚本，或在输入框中输入代码。\n\n"
  end

  # Get input text as a proper Ruby string (not Java CharSequence)
  def input_text
    @script_input.java_object.getText.toString
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

  # Run custom Ruby code from input field
  def run_custom
    code = input_text
    return if code.nil? || code.strip.empty?

    @output.append_text("─── custom ───\n")
    begin
      # Use evalRuby framework method which calls mruby.eval directly
      # and returns the string result
      result = call_java_method("evalRuby", code)
      if result.nil? || result.to_s.empty?
        @output.append_text("  (no output)\n\n")
      else
        @output.append_text("  #{result}\n\n")
      end
    rescue => e
      @output.append_text("  Error: #{e.class}: #{e.message}\n\n")
    end
    @script_input.text = ""
  end

  # Import and run an external .rb file from file system
  def import_file
    path = input_text
    return if path.nil? || path.strip.empty?

    @output.append_text("─── import: #{path} ───\n")
    begin
      # Read file content as Ruby string, then eval it
      content = File.read(path)
      result = Mrboto._eval(content)
      if result.nil?
        @output.append_text("  (no output)\n\n")
      else
        @output.append_text("  #{result}\n\n")
      end
    rescue => e
      @output.append_text("  Error: #{e.class}: #{e.message}\n\n")
    end
    @script_input.text = ""
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
