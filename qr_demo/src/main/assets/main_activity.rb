# main_activity.rb — QR Code Camera Demo
#
# 功能演示：
# 1. 图库选择二维码图片
# 2. 启动相机比对二维码
# 3. 保存 3s 瞬间视频
# 4. OCR 识别文字到剪贴板
# 5. 发送通知
# 6. 写入 SQLite
# 7. 读取传感器
# 8. 多线程处理
# 9. 悬浮窗显示
# 10. 申请权限
# 11. Shell 命令执行

class MainActivity < Mrboto::Activity
  STATE_IDLE = 0
  STATE_GALLERY_SELECTED = 1
  STATE_SCANNING = 2
  STATE_MATCHED = 3

  def on_create(bundle)
    super
    self.title = "QR Code Demo"

    @state = STATE_IDLE
    @target_qr_data = ""
    @video_thread_id = -1
    @sensor_id = -1
    @overlay_id = -1
    @db = nil

    # 初始化 OCR
    ocr_init

    # 初始化 SQLite
    init_database

    # 先创建 UI，再申请权限（权限按钮和状态显示需要 View 已创建）
    setup_ui

    # 申请权限
    request_all_permissions
  end

  def on_resume
    super
    # 刷新权限状态（用户可能从设置页面返回后授权了新权限）
    update_permission_display
    # 启动加速度传感器
    @sensor_id = start_accelerometer do |x, y, z|
      update_sensor_display(x, y, z)
    end
  end

  def on_pause
    super
    # 停止传感器
    stop_accelerometer(@sensor_id) if @sensor_id > 0
  end

  def on_destroy
    # 释放 OCR
    ocr_release
    super
  end

  # ── 权限申请 ─────────────────────────────────────────────────────
  def request_all_permissions
    perms = [
      Mrboto::Helpers::PERMISSION_CAMERA,
      Mrboto::Helpers::PERMISSION_READ_EXTERNAL_STORAGE,
      Mrboto::Helpers::PERMISSION_RECORD_AUDIO,
      Mrboto::Helpers::PERMISSION_POST_NOTIFICATIONS
    ]

    result = request_permissions(perms)
    denied = result.select { |_, v| !v }.keys
    if denied.size > 0
      toast("缺少权限：#{denied.join(', ')}")
    else
      toast("所有权限已授予")
    end
    update_permission_display
  end

  # ── SQLite 初始化 ─────────────────────────────────────────────────
  def init_database
    @db = sqlite_open("qr_scan.db")
    # 使用 execute 创建表
    @db.execute <<-SQL if @db
      CREATE TABLE IF NOT EXISTS scans (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        qr_data TEXT NOT NULL,
        timestamp INTEGER NOT NULL,
        video_path TEXT
      )
    SQL
  end

  # ── UI 布局 ───────────────────────────────────────────────────────
  def setup_ui
    self.content_view = scroll_view do
      linear_layout(
        orientation: :vertical,
        padding: 16
      ) do
        text_view(
          text: "QR Code 扫描演示",
          text_size: 24,
          gravity: :center,
          padding: 16
        )

        @status_view = text_view(
          text: "状态：等待选择二维码图片",
          text_size: 14,
          gravity: :center,
          padding: 8,
          text_color: "666666"
        )

        # 相机相关按钮
        button(text: "从图库选择二维码", padding: 12, margin_bottom: 8) {
          pick_from_gallery
        }

        button(text: "启动相机比对", padding: 12, margin_bottom: 8) {
          start_camera_scan
        }

        button(text: "录制视频 (约3s)", padding: 12, margin_bottom: 8) {
          record_video_hint
        }

        # 预览区域
        @preview_text = text_view(
          text: "二维码预览区域",
          text_size: 12,
          gravity: :center,
          padding: 8,
          text_color: "999999",
          margin_bottom: 16
        )

        # 功能测试按钮组
        text_view(
          text: "功能演示：",
          text_size: 16,
          margin_top: 8,
          padding: 8
        )

        linear_layout(
          orientation: :horizontal,
          gravity: :center_horizontal,
          padding: 8
        ) do
          button(text: "OCR测试", padding: 8, margin_right: 4) {
            test_ocr
          }

          button(text: "通知", padding: 8, margin_right: 4) {
            test_notification
          }

          button(text: "Shell", padding: 8) {
            test_shell
          }
        end

        # 数据库记录
        text_view(
          text: "扫描记录：",
          text_size: 16,
          margin_top: 16,
          padding: 8
        )

        @records_view = text_view(
          text: "暂无记录",
          text_size: 12,
          padding: 8,
          height: 150,
          background_color: "FAFAFA"
        )

        # 传感器数据
        @sensor_view = text_view(
          text: "传感器：--",
          text_size: 12,
          padding: 8,
          margin_top: 8
        )

        # 悬浮窗按钮
        button(text: "显示/隐藏 悬浮窗", padding: 12, margin_top: 8) {
          toggle_overlay_window
        }

        # 请求权限按钮
        button(text: "请求权限", padding: 12, margin_top: 8) {
          request_all_permissions
        }

        # 权限状态
        @permission_view = text_view(
          text: "权限：检查中...",
          text_size: 12,
          padding: 8,
          text_color: "999999"
        )
        update_permission_display
      end
    end
  end

  # ── 1. 图库选择二维码 ──────────────────────────────────────────────
  def pick_from_gallery
    pick_image_from_gallery do |success, uri|
      if success && uri && !uri.empty?
        # 复制图片到缓存
        cached_path = copy_selected_image("selected_qr_#{Time.now.to_i}.jpg")
        if !cached_path.empty?
          update_status("已选择图片：#{cached_path}")
          @state = STATE_GALLERY_SELECTED
          # 扫描二维码
          scan_qr_content(cached_path)
        else
          toast("图片复制失败")
        end
      else
        toast("未选择图片")
      end
    end
  end

  def scan_qr_content(path)
    results_json = scan_qr_code(path)
    results = Mrboto::Helpers.parse_json_array(results_json)

    if results.size > 0
      @target_qr_data = results[0].to_s
      @preview_text.text = "目标二维码：#{@target_qr_data}"
      toast("二维码内容：#{@target_qr_data}")
    else
      @preview_text.text = "未检测到二维码"
      toast("图片中未找到二维码")
    end
  end

  # ── 2. 启动相机比对 ────────────────────────────────────────────────
  def start_camera_scan
    if @target_qr_data.empty?
      toast("请先从图库选择目标二维码")
      return
    end

    update_status("正在扫描...")
    @state = STATE_SCANNING

    # 启动相机拍照
    camera_take_photo do |success, path|
      if success && path && !path.empty?
        toast("照片已保存：#{path}")
        # 分析照片中的二维码
        scan_photo_qr(path)
      else
        update_status("拍照失败")
      end
    end
  end

  def scan_photo_qr(path)
    results_json = scan_qr_code(path)
    results = Mrboto::Helpers.parse_json_array(results_json)

    # 检查是否有匹配的二维码
    matched = results.any? { |r| r.to_s == @target_qr_data }

    if matched
      update_status("比对成功！")
      @state = STATE_MATCHED
      toast("二维码匹配成功！")

      # 执行成功操作
      trigger_success_actions
    else
      # 未匹配，继续扫描提示
      update_status("未匹配，继续扫描...")
      toast("未找到目标二维码，请重新拍照")
    end
  end

  # ── 3. 录制视频 ────────────────────────────────────────────────────
  def record_video_hint
    toast("将调用系统相机\n请手动录制约3秒后停止")
    camera_record_video do |success, path|
      if success && path && !path.empty?
        toast("视频已保存：#{path}")
        # 存储到数据库
        record_to_db(@target_qr_data.empty? ? "test" : @target_qr_data, path)
        # 启动后台线程处理视频
        process_video_async(path)
      else
        toast("录像失败")
      end
    end
  end

  def process_video_async(video_path)
    @video_thread_id = thread_start do
      # 模拟视频后处理
      sleep 2
      run_on_ui_thread do
        toast("视频处理完成")
      end
    end
  end

  def record_to_db(qr_data, video_path)
    if @db
      @db.execute <<-SQL
        INSERT INTO scans (qr_data, timestamp, video_path)
        VALUES ('#{qr_data}', #{Time.now.to_i}, '#{video_path}')
      SQL
      refresh_records
    end
  end

  # ── 4. OCR 文字识别 ────────────────────────────────────────────────
  def test_ocr
    toast("请拍摄一张包含文字的照片")
    camera_take_photo do |success, path|
      if success && path && !path.empty?
        ocr_result = ocr_recognize(path)

        if ocr_result && !ocr_result.empty?
          # 提取前几行文字
          lines = ocr_result.split("\n").select { |l| l.strip.size > 0 }
          text_below = lines.first(3).join(" ")[0..50]

          # 复制到剪贴板
          clipboard_system_copy(text_below)
          toast("已复制到剪贴板：#{text_below}")

          update_status("OCR识别：#{text_below}")
        else
          toast("未识别到文字")
        end
      end
    end
  end

  # ── 5. 发送通知 ─────────────────────────────────────────────────────
  def test_notification
    notify(100, "QR Demo 通知", "这是一条测试通知")

    run_delayed(2000) do
      notify_big(
        101,
        "详细通知",
        "扫描记录：\n#{@records_view.text}"
      )
    end
  end

  # ── 6. SQLite 数据库 ─────────────────────────────────────────────────
  def refresh_records
    return unless @db

    records = @db.query("SELECT * FROM scans ORDER BY id DESC LIMIT 5")

    if records && records.size > 0
      text = records.map do |r|
        "##{r['id']} #{r['qr_data']} - #{r['video_path']}"
      end.join("\n")
      @records_view.text = text
    else
      @records_view.text = "暂无记录"
    end
  end

  # ── 7. 传感器读取 ─────────────────────────────────────────────────────
  def update_sensor_display(x, y, z)
    run_on_ui_thread do
      @sensor_view.text = "加速度: x=#{x.to_f.round(2)}, y=#{y.to_f.round(2)}, z=#{z.to_f.round(2)}"
    end
  end

  # ── 8. 多线程处理 ─────────────────────────────────────────────────────
  def trigger_success_actions
    # 1. 发送通知
    notify(200, "匹配成功", "找到目标二维码：#{@target_qr_data}")

    # 2. 在后台线程执行耗时操作
    background_task do
      toast("二维码匹配成功！")
      toast("已记录到数据库")
    end
  end

  def background_task
    thread_start do
      # 模拟后台处理
      result = shell_exec("echo 'Background task done'")
      run_on_ui_thread do
        toast(result)
      end
    end
  end

  # ── 9. 悬浮窗显示 ─────────────────────────────────────────────────────
  def toggle_overlay_window
    if @overlay_id > 0
      overlay_remove(@overlay_id)
      @overlay_id = -1
      toast("悬浮窗已隐藏")
    else
      # 检查悬浮窗权限
      unless Mrboto::Helpers.check_overlay_permission
        toast("需要悬浮窗权限，请在设置中启用")
        Mrboto::Helpers.open_overlay_settings
        return
      end

      # 创建自定义悬浮窗 UI
      overlay_view = linear_layout(
        orientation: :vertical,
        background_color: "80FF0000",  # 半透明红色
        padding: 8
      ) do
        text_view(
          text: "悬浮窗",
          text_size: 14,
          text_color: "FFFFFF",
          gravity: :center
        )
        text_view(
          text: "时间: #{Time.now.strftime('%H:%M:%S')}",
          text_size: 12,
          text_color: "FFFFFF",
          gravity: :center
        )
      end

      @overlay_id = overlay_show(100, 100, width: 150, height: 80, view_id: overlay_view._registry_id)
      if @overlay_id > 0
        @overlay_drag_start_x = 0
        @overlay_drag_start_y = 0
        @overlay_drag_touch_x = 0
        @overlay_drag_touch_y = 0

        overlay_view.on_touch do |view_id, action, raw_x, raw_y|
          case action
          when 0  # ACTION_DOWN
            @overlay_drag_touch_x = raw_x
            @overlay_drag_touch_y = raw_y
            @overlay_drag_start_x = 100
            @overlay_drag_start_y = 100
            false
          when 2  # ACTION_MOVE
            dx = raw_x - @overlay_drag_touch_x
            dy = raw_y - @overlay_drag_touch_y
            overlay_update_position(@overlay_id, @overlay_drag_start_x + dx, @overlay_drag_start_y + dy)
            true
          else
            false
          end
        end
        toast("悬浮窗已显示 (ID: #{@overlay_id})")
      else
        toast("无法显示悬浮窗")
      end
    end
  end

  # ── 10. 权限申请 ─────────────────────────────────────────────────────
  def update_permission_display
    return unless @permission_view

    perms = {
      "相机" => Mrboto::Helpers::PERMISSION_CAMERA,
      "存储" => Mrboto::Helpers::PERMISSION_READ_EXTERNAL_STORAGE,
      "录音" => Mrboto::Helpers::PERMISSION_RECORD_AUDIO,
      "通知" => Mrboto::Helpers::PERMISSION_POST_NOTIFICATIONS
    }

    status = perms.map do |name, perm|
      granted = permission_granted?(perm)
      "#{name}: #{granted ? '✓' : '✗'}"
    end.join(" | ")

    @permission_view.text = "权限：#{status}"
  end

  # ── 11. Shell 命令执行 ────────────────────────────────────────────────
  def test_shell
    result = shell_exec("echo 'Hello from Shell' && date")
    dialog("Shell 输出", result)
  end

  # ── 辅助方法 ─────────────────────────────────────────────────────────
  def update_status(text)
    run_on_ui_thread do
      @status_view.text = "状态：#{text}"
    end
  end
end

# ── 注册主 Activity ─────────────────────────────────────────────────────
Mrboto.register_activity_class(MainActivity)
