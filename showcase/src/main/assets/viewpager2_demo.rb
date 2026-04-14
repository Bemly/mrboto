# viewpager2_demo.rb — ViewPager2 功能测试页面
#
# 在真实 Activity 中测试 ViewPager2 的所有方法
# logcat 过滤 "mrboto" 即可看到结果

class ViewPager2DemoActivity < Mrboto::Activity
  def on_create(bundle)
    super
    self.title = "ViewPager2 Demo"

    @page_index = 0

    # 创建 3 个页面
    @pages = []
    3.times do |i|
      page = linear_layout(orientation: :vertical, gravity: :center, padding: 24) do
        text_view(
          text: "Page #{i + 1}",
          text_size: 32,
          text_color: ["2196F3", "4CAF50", "FF5722"][i],
          gravity: :center
        )
        text_view(
          text: "This is page #{i + 1} of 3",
          text_size: 16,
          gravity: :center,
          padding: 8
        )
      end
      @pages << page
    end

    log("1. Created 3 pages")

    # 创建 ViewPager2
    @vp = view_pager_2(padding: [0, 16, 0, 0])

    log("2. ViewPager2 created: #{@vp.class}")

    # 设置 adapter
    @vp.set_adapter(@pages)
    log("3. set_adapter with 3 pages: OK")

    # offscreen_page_limit
    @vp.offscreen_page_limit = 1
    log("4. offscreen_page_limit = 1: OK")

    # user_input_enabled
    @vp.user_input_enabled = true
    log("5. user_input_enabled = true: OK")

    # current_item
    @vp.current_item = 0
    log("6. current_item = 0: OK")

    # orientation
    @vp.orientation = :horizontal
    log("7. orientation = :horizontal: OK")

    log("=== ViewPager2 Demo: ALL 7 tests passed ===")

    self.content_view = linear_layout(orientation: :vertical) do
      # ViewPager2 占主要区域
      @vp

      # 控制按钮
      linear_layout(orientation: :horizontal, gravity: :center, padding: 16) do
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

      linear_layout(orientation: :horizontal, gravity: :center, padding: [16, 0, 16, 16]) do
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
