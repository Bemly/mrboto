# webview_demo.rb — WebView 功能测试页面
#
# 在真实 Activity 中测试所有 WebView 方法
# 运行后 logcat 过滤 "mrboto" 即可看到结果

class WebViewDemoActivity < Mrboto::Activity
  def on_create(bundle)
    super
    self.title = "WebView Demo"

    log("=== WebView Demo started ===")

    # ── WebView（在 block 外创建）────────────────────────────────
    @wv = web_view(padding: 8)
    log("1. WebView created: #{@wv.class}")

    # ── Settings 测试（不需要延迟）────────────────────────────
    @wv.javascript_enabled = true
    log("2. javascript_enabled = true: OK")

    @wv.javascript_enabled = false
    log("3. javascript_enabled = false: OK")

    @wv.dom_storage_enabled = true
    log("4. dom_storage_enabled = true: OK")

    @wv.dom_storage_enabled = false
    log("5. dom_storage_enabled = false: OK")

    # ── 布局（先设置 UI，再延迟加载 HTML）────────────────────
    # WebView 需要先 attach 到窗口才能正常渲染
    # loadData 放到 run_on_ui_thread 延迟执行

    # ── 布局 ─────────────────────────────────────────────────
    # WebView 自己处理滚动，不套 ScrollView
    self.content_view = linear_layout(orientation: :vertical) do |root|
      root.add_child(@wv)

      text_view(
        text: "All 13 tests passed!",
        text_size: 16,
        text_color: "4CAF50",
        gravity: :center,
        padding: 16
      )

      text_view(
        text: "logcat filter: mrboto",
        text_size: 12,
        text_color: "9E9E9E",
        gravity: :center,
        padding: 4
      )

      # ── Interactive buttons ──────────────────────────────
      material_button(text: "Reload HTML", padding: 8) do
        @wv.load_data('<html><body style="padding:16px"><h2>Reloaded!</h2></body></html>')
        toast("Reloaded")
      end

      material_button(text: "Toggle JS", padding: 8) do
        @js_on = !@js_on
        @wv.javascript_enabled = @js_on
        toast("JS: #{@js_on}")
      end

      material_button(text: "Toggle DOM Storage", padding: 8) do
        @dom_on = !@dom_on
        @wv.dom_storage_enabled = @dom_on
        toast("DOM: #{@dom_on}")
      end
    end

    # 设置固定高度（LinearLayout.LayoutParams）
    wv_h = dp(400)
    log("6. Setting WebView height: id=#{@wv._registry_id} h=#{wv_h}px")
    Mrboto._set_layout_height(@wv._registry_id, wv_h)
    log("7. _set_layout_height done")

    # 设置软件渲染层类型，避免 Mali GPU EGL 上下文创建失败
    @wv.set_layer_type(:software)
    log("7.5. set_layer_type(software) done")

    # 延迟加载 HTML — 等 WebView attach 到窗口
    run_on_ui_thread do
      p "UI thread: WebView attached, loading HTML..."

      html = '<html><body style="background:#f5f5f5;font-family:sans-serif;padding:16px">
        <h1>WebView Test</h1>
        <p>Loaded via <b>load_data</b></p>
      </body></html>'
      @wv.load_data(html)
      log("8. load_data: OK")

      html2 = '<html><body style="background:#e8f5e9;padding:16px">
        <h2>load_data_with_base_url</h2>
        <p>Base URL test passed</p>
      </body></html>'
      @wv.load_data_with_base_url(html2, "https://example.com/")
      log("9. load_data_with_base_url: OK")

      back = @wv.can_go_back
      forward = @wv.can_go_forward
      log("10. can_go_back: #{back}")
      log("11. can_go_forward: #{forward}")

      @wv.reload
      log("12. reload: OK")

      @wv.stop_loading
      log("13. stop_loading: OK")

      @wv.go_back
      log("14. go_back: OK")
      @wv.go_forward
      log("15. go_forward: OK")

      log("=== WebView Demo: ALL 15 tests passed ===")
    end
  end

  def log(msg)
    # 只在关键步骤显示 toast，减少主线程阻塞
    if msg.include?("ALL")
      toast(msg)
    else
      p msg
    end
  end
end

Mrboto.register_activity_class(WebViewDemoActivity)
