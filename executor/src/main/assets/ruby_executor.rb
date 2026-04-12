# ruby_executor.rb — Terminal-like Ruby code executor
#
# UI: dark terminal with scrollable output TextView, input field, and Run button.
# Supports built-in commands: help, clear, version, widgets

class ExecutorActivity < Mrboto::Activity
  def on_create(bundle)
    super
    self.title = "mrboto Ruby Executor"

    @counter = 0

    self.content_view = linear_layout(orientation: :vertical, padding: 0) {
      # Output area — scrollable terminal
      scroll_view(padding: 8) {
        @output = text_view(
          text_size: 14,
          text_color: "00FF00",
          padding: 8
        )
      }

      # Input row
      linear_layout(
        orientation: :horizontal,
        padding: 8
      ) {
        @input = edit_text(
          hint: "Ruby code...",
          text_size: 14,
          padding: 8,
          single_line: true
        )

        button(text: "Run", padding: 8) { run_code }
      }
    }

    # Welcome message
    @output.text = <<~MSG
mruby 3.4.0 Terminal
Type Ruby expressions and press Run.

Built-in commands:
  help        — Show help
  version     — mruby version
  clear       — Clear output
  widgets     — List available widgets
  exit        — Reset output

MSG
  end

  # Run the input code and display result
  def run_code
    code = @input.java_object.getText.to_s.strip
    return if code.empty?

    @counter += 1
    @output.append_text("\n[#{@counter}]> #{code}\n")

    # Handle built-in commands
    case code
    when /^help$/i
      msg = <<~HELP
Commands:
  help      — Show this help
  version   — mruby version
  clear     — Clear output
  widgets   — List available widgets
  exit      — Reset output

Any other input is evaluated as Ruby code.
HELP
      @output.append_text("  => #{msg}")

    when /^version$/i
      @output.append_text("  => mruby 3.4.0\n")

    when /^clear$/i
      @output.text = ""

    when /^widgets$/i
      lines = Mrboto::Widgets::JAVA_CLASS_MAP.map { |k, v| "  #{k} → #{v}" }
      @output.append_text("  => #{lines.join("\n")}\n")

    when /^exit$/i
      @output.text = ""
      @output.append_text("Output cleared.\n")

    else
      # Evaluate as Ruby expression via C bridge
      begin
        val = Mrboto._eval(code)
        if val.nil?
          @output.append_text("  => nil\n")
        else
          @output.append_text("  => #{val.to_s}\n")
        end
      rescue => e
        @output.append_text("  Error: #{e.class}: #{e.message}\n")
      end
    end

    @input.text = ""
  end
end

Mrboto._ruby_activity_class = ExecutorActivity
