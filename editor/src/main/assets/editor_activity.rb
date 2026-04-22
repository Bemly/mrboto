# editor_activity.rb — 最小化测试 glass_cell + nav_cell
class EditorActivity < Mrboto::ComposeActivity
  def on_create(bundle)
    super
    self.title = "Ruby Editor"
    begin
      build_ui
    rescue => e
      puts("build_ui ERROR: #{e.class}: #{e.message}")
      puts(e.backtrace.first(10).join("\n")) if e.respond_to?(:backtrace)
    end
  end

  def build_ui
    Mrboto::ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])
    Mrboto::ComposeBuilder.instance_variable_set(:@_compose_root, nil)

    glass_bar(
      shape_type: :rounded_rect,
      corner_radius: 24.0,
      blur_radius: 25.0,
      vibrancy: true
    ) {
      column(fill_max_width: true) {
        text("测试内容")
      }

      glass_cell {
        nav_cell(icon: :ic_menu_code, content: "代码") { toast("代码") }
      }
      glass_cell {
        nav_cell(icon: :ic_menu_file, content: "文件") { toast("文件") }
      }
      glass_cell {
        nav_cell(icon: :ic_menu_log, content: "日志") { toast("日志") }
      }

      right_cell {
        glass_cell {
          nav_cell(icon: :ic_menu_search, content: "搜索") { toast("搜索") }
        }
      }
    }

    puts("build_ui: root=#{Mrboto::ComposeBuilder.root ? Mrboto::ComposeBuilder.root["type"] : "nil"}")
    if Mrboto::ComposeBuilder.root
      puts(Mrboto._compose_to_json(Mrboto::ComposeBuilder.root))
    end

    set_compose_content
  end
end

Mrboto.register_activity_class(EditorActivity)
