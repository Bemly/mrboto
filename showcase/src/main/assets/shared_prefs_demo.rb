# shared_prefs_demo.rb — 展示 SharedPreferences API

class PrefsDemoActivity < Mrboto::Activity
  def on_create(bundle)
    super
    self.title = "Shared Preferences"

    self.content_view = scroll_view do
      linear_layout(orientation: :vertical, padding: 12) do

        section_title("String Preferences")

        @name_input = edit_text(
          hint: "Enter your name",
          text_size: 16,
          padding: 4
        )

        button(text: "Save Name", padding: 12) {
          ctx = Mrboto._app_context
          ctx_id = Mrboto._register_object(ctx)
          toast("ctx_id=#{ctx_id}")

          if ctx_id && ctx_id > 0
            Mrboto._sp_put_string(ctx_id, "prefs_demo", "debug_test", "hello_from_c")
            result = Mrboto._sp_get_string(ctx_id, "prefs_demo", "debug_test", "fallback")
            toast("C result=#{result.inspect}")
          end

          sp = shared_preferences("prefs_demo")
          name = @name_input.text
          toast("text=#{name.inspect} class=#{name.class}")

          sp.put_string("user_name", name || "")
          toast("Name saved!")
        }

        @name_display = text_view(
          text: "Saved name: (click Read to show)",
          text_size: 14,
          padding: 8
        )

        button(text: "Read Name", padding: 12) {
          sp = shared_preferences("prefs_demo")
          name = sp.get_string("user_name", "not set")
          @name_display.text = "Saved name: #{name}"
          toast("Name: #{name}")
        }

        # ── Section: Int Preferences ──────────────────────────
        section_title("Int Preferences")

        @count_input = edit_text(
          hint: "Enter a number",
          input_type: :number,
          text_size: 16,
          padding: 4
        )

        button(text: "Save Number", padding: 12) {
          sp = shared_preferences("prefs_demo")
          val = @count_input.text
          sp.put_int("counter", val.to_i)
          toast("Number saved!")
        }

        @count_display = text_view(
          text: "Saved number: (click Read to show)",
          text_size: 14,
          padding: 8
        )

        button(text: "Read Number", padding: 12) {
          sp = shared_preferences("prefs_demo")
          val = sp.get_int("counter", 0)
          @count_display.text = "Saved number: #{val}"
          toast("Number: #{val}")
        }

        # ── Footer ────────────────────────────────────────────
        text_view(
          text: "— End of Shared Preferences —",
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

Mrboto.register_activity_class(PrefsDemoActivity)
