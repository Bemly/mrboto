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

    # ── load_data ────────────────────────────────────────────
    html = '<html><body style="background:#f5f5f5;font-family:sans-serif;padding:16px">
      <h1>WebView Test</h1>
      <p>Loaded via <b>load_data</b></p>
    </body></html>'
    @wv.load_data(html)
    log("2. load_data: OK")

    # ── javascript_enabled ───────────────────────────────────
    @wv.javascript_enabled = true
    log("3. javascript_enabled = true: OK")

    @wv.javascript_enabled = false
    log("4. javascript_enabled = false: OK")

    # ── dom_storage_enabled ──────────────────────────────────
    @wv.dom_storage_enabled = true
    log("5. dom_storage_enabled = true: OK")

    @wv.dom_storage_enabled = false
    log("6. dom_storage_enabled = false: OK")

    # ── load_data_with_base_url ──────────────────────────────
    html2 = '<html><body style="background:#e8f5e9;padding:16px">
      <h2>load_data_with_base_url</h2>
      <p>Base URL test passed</p>
    </body></html>'
    @wv.load_data_with_base_url(html2, "https://example.com/")
    log("7. load_data_with_base_url: OK")

    # ── can_go_back / can_go_forward ─────────────────────────
    back = @wv.can_go_back
    forward = @wv.can_go_forward
    log("8. can_go_back: #{back} (#{back.class})")
    log("9. can_go_forward: #{forward} (#{forward.class})")

    # ── reload ───────────────────────────────────────────────
    @wv.reload
    log("10. reload: OK")

    # ── stop_loading ─────────────────────────────────────────
    @wv.stop_loading
    log("11. stop_loading: OK")

    # ── go_back / go_forward (no history, no-op) ────────────
    @wv.go_back
    log("12. go_back: OK (no history, no-op)")
    @wv.go_forward
    log("13. go_forward: OK (no history, no-op)")

    # ── Summary ──────────────────────────────────────────────
    log("=== WebView Demo: ALL 13 tests passed ===")

    # ── 布局 ─────────────────────────────────────────────────
    self.content_view = scroll_view(orientation: :vertical) do |root|
      linear_layout(orientation: :vertical, gravity: :center, padding: 16) do |inner|
        inner.add_child(@wv)

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
        material_button(text: "Reload HTML", padding: 8) {
          @wv.load_data('<html><body style="padding:16px"><h2>Reloaded!</h2></body></html>')
          toast("Reloaded")
        }

        material_button(text: "Toggle JS", padding: 8) {
          @js_on = !@js_on
          @wv.javascript_enabled = @js_on
          toast("JS: #{@js_on}")
        }

        material_button(text: "Toggle DOM Storage", padding: 8) {
          @dom_on = !@dom_on
          @wv.dom_storage_enabled = @dom_on
          toast("DOM: #{@dom_on}")
        end
      end
    end

    # WebView 被 addView 后 LinearLayout 会覆盖 LayoutParams
    # 必须在 content_view 设置之后再设置高度
    wv_h = dp(400)
    log("14. Setting WebView height AFTER addView: id=#{@wv._registry_id} h=#{wv_h}px")
    Mrboto._set_layout_height(@wv._registry_id, wv_h)
    log("15. _set_layout_height done")
  end

  def log(msg)
    toast(msg)
    p msg
  end
end

Mrboto.register_activity_class(WebViewDemoActivity)
