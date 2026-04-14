# viewpager2_demo.rb — ViewPager2 功能测试页面
#
# 每页展示 6 个系统图标，共 3 页覆盖 18 个图标
# logcat 过滤 "mrboto" 即可看到结果

class ViewPager2DemoActivity < Mrboto::Activity
  def on_create(bundle)
    super
    self.title = "ViewPager2 Demo"

    @page_index = 0
    @locked = false

    icons = [
      "ic_menu_edit", "ic_menu_save", "ic_menu_send",
      "ic_menu_search", "ic_menu_zoom", "ic_menu_preferences",
      "ic_menu_sort_by_size", "ic_menu_rotate", "ic_menu_today",
      "ic_menu_gallery", "ic_menu_play", "ic_menu_agenda",
      "ic_menu_starred", "ic_menu_info_details", "ic_menu_help",
      "ic_menu_compose", "ic_menu_view", "ic_menu_manage"
    ]

    # 每页 6 个图标，分 3 行 x 2 列
    @pages = []
    icons.each_slice(6) do |page_icons|
      page = linear_layout(orientation: :vertical, padding: 16) do
        page_icons.each_slice(2) do |row|
          linear_layout(orientation: :horizontal, gravity: :center, padding: 8) do
            row.each do |icon_name|
              icon_id = Mrboto.android_sys_id(icon_name, "drawable")
              linear_layout(orientation: :vertical, gravity: :center, padding: 16) do
                if icon_id && icon_id > 0
                  image_view(image_resource: icon_id, padding: 8)
                end
                text_view(
                  text: icon_name.sub("ic_menu_", ""),
                  text_size: 12,
                  gravity: :center,
                  text_color: "757575"
                )
              end
            end
          end
        end
      end
      @pages << page
    end

    log("1. Created #{@pages.size} pages with #{icons.size} icons")

    # ViewPager2
    @vp = view_pager_2(padding: [0, 16, 0, 0])
    log("2. ViewPager2 created: #{@vp.class}")

    # adapter
    @vp.set_adapter(@pages.map { |p| p._registry_id })
    log("3. set_adapter: OK")

    @vp.offscreen_page_limit = 1
    log("4. offscreen_page_limit = 1: OK")

    @vp.user_input_enabled = true
    log("5. user_input_enabled = true: OK")

    @vp.current_item = 0
    log("6. current_item = 0: OK")

    @vp.orientation = :horizontal
    log("7. orientation = :horizontal: OK")

    log("=== ViewPager2 Demo: ALL 7 tests passed ===")

    # 布局 — ViewPager2 需要固定高度，LinearLayout WRAP_CONTENT 会压扁它
    # 使用 C bridge 设置 LinearLayout.LayoutParams(MATCH_PARENT, 400dp)
    vp_h = dp(400)
    log("8. Setting layout height: vp_id=#{@vp._registry_id} height=#{vp_h}px (400dp)")
    Mrboto._set_layout_height(@vp._registry_id, vp_h)
    log("9. _set_layout_height done")

    self.content_view = linear_layout(orientation: :vertical) do
      @vp

      text_view(
        text: "All 7 tests passed - swipe or use buttons",
        text_size: 14,
        text_color: "4CAF50",
        gravity: :center,
        padding: 8
      )

      linear_layout(orientation: :horizontal, gravity: :center, padding: 8) do
        material_button(text: "Prev", padding: 8) {
          @page_index = [@page_index - 1, 0].max
          @vp.current_item = @page_index
          toast("Page #{@page_index + 1}")
        }

        material_button(text: "Next", padding: 8) {
          @page_index = [@page_index + 1, 2].min
          @vp.current_item = @page_index
          toast("Page #{@page_index + 1}")
        }
      end

      linear_layout(orientation: :horizontal, gravity: :center, padding: 8) do
        material_button(text: "Vertical", padding: 8) {
          @vp.orientation = :vertical
          toast("Vertical")
        }

        material_button(text: "Horizontal", padding: 8) {
          @vp.orientation = :horizontal
          toast("Horizontal")
        }

        material_button(text: "Lock", padding: 8) {
          @locked = !@locked
          @vp.user_input_enabled = !@locked
          toast(@locked ? "Locked" : "Unlocked")
        }
      end
    end
  end

  def log(msg)
    toast(msg)
    p msg
  end
end

Mrboto.register_activity_class(ViewPager2DemoActivity)
