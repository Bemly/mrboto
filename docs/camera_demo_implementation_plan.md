# Camera Demo App 实现方案

## 项目概述

创建一个 100% Ruby 的相机 demo app，全面展示 mrboto 框架的能力，包括：
- 图库选择二维码图片
- 二维码扫码比对
- 3s 瞬间视频录制
- OCR 文本识别（剪贴板）
- 通知发送
- SQLite 数据库存储
- 传感器读取
- 多线程处理
- 悬浮窗显示
- 权限请求
- Shell 命令执行

## 框架现状分析

### 已支持的 API

| 功能类别 | API | Ruby 方法 | Kotlin 方法 |
|---------|-----|----------|-----------|
| 相机 | 拍照/录像 | `camera_take_photo`, `camera_record_video` | `cameraTakePhoto`, `cameraRecordVideo` |
| OCR | 文本识别 | `ocr_init`, `ocr_recognize` | `ocrInit`, `ocrRecognize` |
| 通知 | 发送通知 | `notify`, `notify_big`, `notify_progress` | `notifyShow`, `notifyBig`, `notifyProgress` |
| SQLite | 数据库操作 | `sqlite_open`, `execute`, `query`, `insert` | `sqliteOpen`, `sqliteExecute`, `sqliteQuery`, `sqliteInsert` |
| 传感器 | 加速度/陀螺仪/距离 | `start_accelerometer`, `start_gyroscope`, `start_proximity` | `startAccelerometer`, `startGyroscope`, `startProximity` |
| 多线程 | 线程管理 | `thread_start`, `thread_join` | `threadStart`, `threadJoin` |
| 悬浮窗 | 显示/移除 | `overlay_show`, `overlay_remove` | `overlayShow`, `overlayRemove` |
| Shell | 命令执行 | `shell_exec` | `shellExec` |
| 权限 | 检查/请求 | `permission_granted?`, `request_permissions` | `checkPermissionGranted`, `requestPermissionsSync` |

### 需要新增的 API

| 功能 | 需求 | 状态 |
|-----|------|-----|
| 图库选择图片 | 选择二维码比对图片 | ❌ 待实现 |
| 二维码扫描 | 实时扫码比对 | ❌ 待实现 |
| onActivityResult 处理 | 拍照/录像/图库返回 | ❌ 待实现 |
| FileProvider 配置 | 相机拍照保存路径 | ❌ 待实现 |

---

## Phase 1: 扩展框架 API

### 1.1 Kotlin Extensions 新增

#### 1.1.1 创建 `GalleryExtensions.kt`

**文件路径:** `/Users/bemly/cchaha/mrboto/app/src/main/kotlin/moe/bemly/mrboto/GalleryExtensions.kt`

```kotlin
package moe.bemly.mrboto

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

private var galleryCallbackId: Int = -1
private var selectedImageUri: Uri? = null

interface GalleryMixin {
    val mruby: MRuby

    /**
     * 打开图库选择图片
     * @param callbackId 回调 ID，返回选中图片路径
     */
    fun pickImageFromGallery(callbackId: Int): Boolean {
        val activity = this as Activity
        return try {
            galleryCallbackId = callbackId
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            activity.startActivityForResult(intent, 9003)
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "pickImageFromGallery failed: ${e.message}")
            false
        }
    }

    /**
     * 复制选中的图片到缓存目录
     * @param outputPath 输出路径
     */
    fun copySelectedImageToCache(outputPath: CharSequence): String {
        val activity = this as Activity
        val uri = selectedImageUri ?: return ""
        return try {
            val inputStream = activity.contentResolver.openInputStream(uri) ?: return ""
            val outputFile = File(activity.cacheDir, outputPath.toString())
            inputStream.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.w("Mrboto", "copySelectedImageToCache failed: ${e.message}")
            ""
        }
    }
}
```

#### 1.1.2 创建 `QRCodeExtensions.kt`

**文件路径:** `/Users/bemly/cchaha/mrboto/app/src/main/kotlin/moe/bemly/mrboto/QRCodeExtensions.kt`

**依赖:** ZXing 库 (`com.google.zxing:core:3.5.3`)

```kotlin
package moe.bemly.mrboto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.Reader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.qrcode.QRCodeMultiReader
import java.io.File

interface QRCodeMixin {
    val mruby: MRuby

    /**
     * 扫描图片中的二维码
     * @param imagePath 图片路径
     * @return JSON 数组，包含扫描到的所有二维码内容
     */
    fun scanQRCode(imagePath: CharSequence): String {
        val activity = this as Activity
        return try {
            val file = File(imagePath.toString())
            if (!file.exists()) return "[]"

            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return "[]"
            val results = scanQRCodeFromBitmap(bitmap)
            
            val arr = org.json.JSONArray()
            for (result in results) {
                arr.put(result)
            }
            arr.toString()
        } catch (e: Exception) {
            Log.w("Mrboto", "scanQRCode failed: ${e.message}")
            "[]"
        }
    }

    /**
     * 从 Bitmap 扫描二维码
     * @param bitmap 图片
     * @return 二维码内容列表
     */
    private fun scanQRCodeFromBitmap(bitmap: Bitmap): List<String> {
        val results = mutableListOf<String>()
        
        try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            val source: LuminanceSource = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            
            val reader: Reader = QRCodeMultiReader()
            val hints = mapOf<DecodeHintType, Any>(
                DecodeHintType.POSSIBLE_FORMATS to listOf(com.google.zxing.BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )
            
            val decodeResults = reader.decodeMultiple(binaryBitmap, hints)
            for (result in decodeResults) {
                results.add(result.text)
            }
        } catch (e: Exception) {
            Log.w("Mrboto", "scanQRCodeFromBitmap failed: ${e.message}")
        }
        
        return results
    }

    /**
     * 生成二维码图片
     * @param text 二维码内容
     * @param outputPath 输出路径
     * @param size 图片大小（默认 300）
     * @return 是否成功
     */
    fun generateQRCode(text: CharSequence, outputPath: CharSequence, size: Int = 300): Boolean {
        val activity = this as Activity
        return try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(text.toString(), com.google.zxing.BarcodeFormat.QR_CODE, size, size)
            
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) 
                        android.graphics.Color.BLACK 
                    else 
                        android.graphics.Color.WHITE)
                }
            }
            
            val outputFile = File(activity.cacheDir, outputPath.toString())
            outputFile.outputStream().use { 
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            true
        } catch (e: Exception) {
            Log.w("Mrboto", "generateQRCode failed: ${e.message}")
            false
        }
    }
}
```

#### 1.1.3 在 `MrbotoActivityBase.kt` 添加 onActivityResult

**文件路径:** `/Users/bemly/cchaha/mrboto/app/src/main/kotlin/moe/bemly/mrboto/MrbotoActivityBase.kt`

在类继承列表中添加 `GalleryMixin, QRCodeMixin`，并添加：

```kotlin
abstract class MrbotoActivityBase : Activity(),
    AccessibilityMixin, CameraMixin, ColorFindMixin, CoroutineMixin,
    DeviceControlMixin, EventListenerMixin, FileEncodingMixin, GestureMixin,
    ImageMixin, IntentMixin, NetworkMixin, OverlayMixin, PredictiveBackMixin,
    QRCodeMixin, ScreenCaptureMixin, SensorMixin, ShellMixin, ThreadingMixin, WindowInfoMixin {
    
    // ... 其他代码 ...
    
    /**
     * 处理 Activity 返回结果
     * - 9001: 拍照结果
     * - 9002: 录像结果
     * - 9003: 图库选择结果
     * - 9100: 屏幕捕获授权
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (resultCode != Activity.RESULT_OK) {
            // 失败回调
            when (requestCode) {
                9001, 9002, 9003 -> {
                    val callbackId = when (requestCode) {
                        9001 -> CameraExtensions.Companion.photoCallbackId
                        9002 -> CameraExtensions.Companion.videoCallbackId
                        9003 -> GalleryExtensions.Companion.galleryCallbackId
                        else -> -1
                    }
                    if (callbackId > 0) {
                        mruby.eval("Mrboto.dispatch_callback($callbackId, false, '')")
                    }
                }
            }
            return
        }
        
        when (requestCode) {
            9001 -> {
                // 拍照成功
                val path = CameraExtensions.Companion.photoUri?.path ?: ""
                val callbackId = CameraExtensions.Companion.photoCallbackId
                if (callbackId > 0) {
                    mruby.eval("Mrboto.dispatch_callback($callbackId, true, '$path')")
                }
            }
            9002 -> {
                // 录像成功
                val uri = data?.data
                val path = uri?.path ?: ""
                val callbackId = CameraExtensions.Companion.videoCallbackId
                if (callbackId > 0) {
                    mruby.eval("Mrboto.dispatch_callback($callbackId, true, '$path')")
                }
            }
            9003 -> {
                // 图库选择成功
                val uri = data?.data
                GalleryExtensions.Companion.selectedImageUri = uri
                val path = uri?.toString() ?: ""
                val callbackId = GalleryExtensions.Companion.galleryCallbackId
                if (callbackId > 0) {
                    mruby.eval("Mrboto.dispatch_callback($callbackId, true, '$path')")
                }
            }
            9100 -> {
                // 屏幕捕获授权
                val resultCodeData = data?.getIntExtra("resultCode", -1) ?: -1
                val permissionData = data?.getParcelableExtra<android.media.projection.MediaProjection>("projection")
                ScreenCaptureExtensions.Companion.mediaProjection = permissionData
            }
        }
    }
}
```

#### 1.1.4 添加依赖到 `app/build.gradle.kts`

```kotlin
dependencies {
    implementation("com.google.zxing:core:3.5.3")
    // ... 其他依赖 ...
}
```

#### 1.1.5 创建 FileProvider 配置

**文件路径:** `/Users/bemly/cchaha/mrboto/app/src/main/res/xml/file_paths.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths xmlns:android="http://schemas.android.com/apk/res/android">
    <cache-path name="cache" path="." />
    <files-path name="files" path="." />
    <external-cache-path name="external_cache" path="." />
</paths>
```

**添加到 `app/src/main/AndroidManifest.xml`**：

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <!-- 其他内容 -->
        
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.mrboto.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
```

### 1.2 Ruby API 新增

#### 1.2.1 在 `helpers.rb` 添加图库选择方法

```ruby
# ── Gallery ───────────────────────────────────────────────────────
def self.pick_image_from_gallery(&block)
  callback_id = Mrboto.register_callback(&block)
  activity = Mrboto.current_activity
  return unless activity
  activity.call_java_method("pickImageFromGallery", callback_id)
end

def self.copy_selected_image(output_path)
  activity = Mrboto.current_activity
  return "" unless activity
  activity.call_java_method("copySelectedImageToCache", output_path).to_s
end

# ── QR Code ───────────────────────────────────────────────────────
def self.scan_qr_code(image_path)
  activity = Mrboto.current_activity
  return "[]" unless activity
  activity.call_java_method("scanQRCode", image_path).to_s
end

def self.generate_qr_code(text, output_path, size: 300)
  activity = Mrboto.current_activity
  return false unless activity
  result = activity.call_java_method("generateQRCode", text.to_s, output_path.to_s, size.to_i)
  result == true || result.to_s == "true"
end
```

#### 1.2.2 顶层方法

```ruby
# ── Top-level: Gallery ──────────────────────────────────────────
def pick_image_from_gallery(&block)
  Mrboto::Helpers.pick_image_from_gallery(&block)
end

def copy_selected_image(path)
  Mrboto::Helpers.copy_selected_image(path)
end

# ── Top-level: QR Code ──────────────────────────────────────────
def scan_qr_code(path)
  Mrboto::Helpers.scan_qr_code(path)
end

def generate_qr_code(text, path, size: 300)
  Mrboto::Helpers.generate_qr_code(text, path, size: size)
end
```

---

## Phase 2: 创建相机 Demo App

### 2.1 项目结构

```
qr_demo/
├── build.gradle.kts
├── proguard-rules.pro
└── src/main/
    ├── AndroidManifest.xml
    └── assets/
        │   └── main_activity.rb
        ├── res/
        │   ├── mipmap-*/ic_launcher.png
        │   └── values/
        │       ├── colors.xml
        │       ├── strings.xml
        │       └── themes.xml
```

### 2.2 AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- 相机权限 -->
    <uses-permission android:name="android.permission.CAMERA" />
    
    <!-- 相机特性声明 -->
    <uses-feature android:name="android.hardware.camera" android:required="false" />
    <uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />
    
    <!-- 录音权限（录像需要） -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    
    <!-- 存储权限 -->
    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    
    <!-- 悬浮窗权限 -->
    <!-- 注意：需要在系统设置中手动授权 -->
    
    <!-- 通知权限 -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name="moe.bemly.mrboto.MrbotoApplication"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.QrDemo"
        android:requestLegacyExternalStorage="true">

        <activity
            android:name="moe.bemly.mrboto.RubyActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <meta-data android:name="mrboto_script" android:value="main_activity.rb" />
        </activity>

        <!-- FileProvider -->
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.mrboto.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>

    </application>

</manifest>
```

### 2.3 build.gradle.kts

```kotlin
plugins {
    id("com.android.application")
}

android {
    namespace = "moe.bemly.qrdemo"
    compileSdk = 36
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "moe.bemly.qrdemo"
        minSdk = 33
        targetSdk = 36

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":app"))
}
```

### 2.4 main_activity.rb 完整实现

```ruby
# main_activity.rb — QR Code Camera Demo
#
# 功能演示：
# 1. 从图库选择二维码图片
# 2. 启动相机比对二维码
# 3. 比对成功后保存 3s 瞬间视频
# 4. OCR 识别文字到剪贴板
# 5. 发送通知
# 6. 写入 SQLite
# 7. 读取传感器
# 8. 多线程处理
# 9. 悬浮窗显示
# 10. 申请权限
# 11. 执行 Shell 命令

require 'mrboto/regexp/mrblib/regexp'

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
    
    # 申请权限
    request_all_permissions
    
    # 初始化 OCR
    ocr_init
    
    # 初始化 SQLite
    init_database
    
    setup_ui
  end

  def on_resume
    super
    # 启动加速度传感器
    @sensor_id = start_accelerometer do |x, y, z|
      # 传感器数据回调
    end
  end

  def on_pause
    super
    # 停止传感器
    stop_accelerometer(@sensor_id) if @sensor_id > 0
  end

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
      dialog("权限缺失", "缺少以下权限：\n#{denied.join("\n")}")
    end
    
    # 悬浮窗权限需要手动设置
    unless permission_granted?("android.permission.SYSTEM_ALERT_WINDOW")
      toast("请在设置中授予悬浮窗权限")
    end
  end

  def init_database
    @db = sqlite_open("qr_scan.db")
    @db.execute <<-SQL
      CREATE TABLE IF NOT EXISTS scans (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        qr_data TEXT NOT NULL,
        timestamp INTEGER NOT NULL,
        video_path TEXT
      )
    SQL
  end

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
        
        # 功能按钮组
        button(text: "从图库选择二维码", padding: 12, margin_bottom: 8) {
          pick_from_gallery
        }
        
        button(text: "启动相机比对", padding: 12, margin_bottom: 8) {
          start_camera_scan
        }
        
        button(text: "录制 3s 视频", padding: 12, margin_bottom: 8) {
          record_3s_video
        }
        
        @qr_preview = image_view(
          width: MATCH_PARENT,
          height: 300,
          scale_type: :center_crop,
          background_color: "F0F0F0"
        )
        
        @preview_text = text_view(
          text: "二维码预览区域",
          text_size: 12,
          gravity: :center,
          padding: 8,
          text_color: "999999"
        )
        
        # 测试按钮组
        linear_layout(
          orientation: :horizontal,
          gravity: :center_horizontal,
          margin_top: 16,
          padding: 8
        ) do
          button(text: "OCR 测试", padding: 8, margin_right: 4) {
            test_ocr
          }
          
          button(text: "通知测试", padding: 8, margin_right: 4) {
            test_notification
          }
          
          button(text: "Shell 测试", padding: 8) {
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
          height: 200,
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
        button(text: "显示悬浮窗", padding: 12, margin_top: 8) {
          show_overlay_window
        end
      end
    end
  end

  # ── 1. 从图库选择二维码 ──────────────────────────────────────
  def pick_from_gallery
    pick_image_from_gallery do |success, uri|
      if success && uri && !uri.empty?
        # 复制图片到缓存
        cached_path = copy_selected_image("selected_qr.jpg")
        update_status("已选择图片：#{cached_path}")
        @state = STATE_GALLERY_SELECTED
        
        # 扫描二维码
        scan_qr_content(cached_path)
      else
        toast("未选择图片")
      end
    end
  end

  def scan_qr_content(path)
    results_json = scan_qr_code(path)
    results = Mrboto::Helpers.parse_json_array(results_json)
    
    if results.size > 0
      @target_qr_data = results[0]
      @preview_text.text = "目标二维码：#{@target_qr_data}"
      
      # 显示预览
      run_on_ui_thread do
        # 使用 WebView 显示图片（简化）
        toast("二维码内容：#{@target_qr_data}")
      end
    else
      @preview_text.text = "未检测到二维码"
      toast("图片中未找到二维码")
    end
  end

  # ── 2. 启动相机比对 ────────────────────────────────────────
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
    
    if results.include?(@target_qr_data)
      update_status("比对成功！")
      @state = STATE_MATCHED
      
      # 执行成功操作
      run_on_ui_thread do
        toast("二维码匹配成功！")
        trigger_success_actions
      end
    else
      # 继续扫描
      update_status("未匹配，继续扫描...")
      run_delayed(500) do
        start_camera_scan
      end
    end
  end

  # ── 3. 3s 瞬间视频录制 ─────────────────────────────────────
  def record_3s_video
    camera_record_video do |success, path|
      if success && path && !path.empty?
        toast("视频已保存：#{path}")
        
        # 存储到数据库
        @db.insert("scans", {
          qr_data: @target_qr_data || "test",
          timestamp: Time.now.to_i,
          video_path: path
        })
        
        refresh_records
      else
        toast("录像失败")
      end
    end
  end

  # ── 4. OCR 文字识别 ────────────────────────────────────────
  def test_ocr
    # 对刚才拍摄的图片进行 OCR
    camera_take_photo do |success, path|
      if success && path && !path.empty?
        ocr_result = ocr_recognize(path)
        
        if ocr_result && !ocr_result.empty?
          # 提取二维码下方的文字（假设 OCR 的前几行）
          lines = ocr_result.split("\n")
          text_below = lines.select { |l| l.strip.size > 0 }.first(2).join(" ")
          
          # 复制到剪贴板
          clipboard_copy(text_below)
          toast("已复制到剪贴板：#{text_below[0..20]}...")
          
          update_status("OCR 识别：#{text_below}")
        else
          toast("未识别到文字")
        end
      end
    end
  end

  # ── 5. 发送通知 ────────────────────────────────────────────
  def test_notification
    notify(1, "QR Demo", "二维码扫描成功")
    
    # 延迟发送大文本通知
    run_delayed(2000) do
      notify_big(
        2,
        "详细通知",
        "扫描记录：\n#{@records_view.text}"
      )
    end
  end

  # ── 6. SQLite 数据库 ────────────────────────────────────────
  def refresh_records
    records = @db.query("SELECT * FROM scans ORDER BY id DESC LIMIT 5")
    
    if records.size > 0
      text = records.map do |r|
        "##{r['id']} #{r['qr_data']} - #{Time.at(r['timestamp'])}"
      end.join("\n")
      @records_view.text = text
    else
      @records_view.text = "暂无记录"
    end
  end

  # ── 7. 传感器读取 ────────────────────────────────────────────
  # (在 on_resume 和 sensor 回调中处理)

  # ── 8. 多线程处理 ────────────────────────────────────────────
  def background_scan(video_path)
    @video_thread_id = thread_start do
      # 在后台线程处理视频
      sleep 3
      
      # 视频后处理（示例：生成缩略图）
      output = shell_exec("ffprobe -v error -select_streams v:0 -show_entries stream=width,height -of csv=p=0 #{video_path} 2>&1 || echo 'video_processed'")
      
      run_on_ui_thread do
        toast("视频处理完成")
      end
    end
  end

  # ── 9. 悬浮窗显示 ────────────────────────────────────────────
  def show_overlay_window
    if @overlay_id > 0
      overlay_remove(@overlay_id)
      @overlay_id = -1
    else
      @overlay_id = overlay_show(100, 100, width: 200, height: 100)
      toast("悬浮窗已显示 (ID: #{@overlay_id})")
    end
  end

  # ── 10. 权限申请 ────────────────────────────────────────────
  # (在 request_all_permissions 中处理)

  # ── 11. Shell 命令执行 ───────────────────────────────────────
  def test_shell
    result = shell_exec("echo 'Hello from Ruby' && date")
    dialog("Shell 输出", result)
  end

  # ── 内部方法 ─────────────────────────────────────────────────
  def update_status(text)
    run_on_ui_thread do
      @status_view.text = "状态：#{text}"
    end
  end

  def trigger_success_actions
    # 1. 发送通知
    notify(10, "匹配成功", "找到目标二维码：#{@target_qr_data}")
    
    # 2. 录制 3s 视频
    record_3s_video
    
    # 3. OCR 识别
    test_ocr
    
    # 4. 记录到数据库
    @db.insert("scans", {
      qr_data: @target_qr_data,
      timestamp: Time.now.to_i,
      video_path: "pending"
    })
    
    refresh_records
  end
end

# ── 注册主 Activity ────────────────────────────────────────────
Mrboto.register_activity_class(MainActivity)
```

---

## 实施步骤

### Phase 1 实施步骤

1. **创建 GalleryExtensions.kt**
   - 实现 `pickImageFromGallery` 和 `copySelectedImageToCache` 方法
   - 处理图库选择回调

2. **创建 QRCodeExtensions.kt**
   - 添加 ZXing 依赖
   - 实现 `scanQRCode` 和 `generateQRCode` 方法
   - 处理二维码扫描和生成

3. **更新 MrbotoActivityBase.kt**
   - 添加 `GalleryMixin` 和 `QRCodeMixin` 到继承列表
   - 实现 `onActivityResult` 方法处理相机/图库返回

4. **配置 FileProvider**
   - 创建 `res/xml/file_paths.xml`
   - 在 AndroidManifest.xml 中添加 provider 声明

5. **更新 helpers.rb**
   - 添加 `pick_image_from_gallery`、`scan_qr_code` 等 Ruby API

6. **测试框架 API**
   - 编写单元测试验证新增 API

### Phase 2 实施步骤

1. **创建 qr_demo 模块**
   - 配置 `build.gradle.kts`
   - 配置 `AndroidManifest.xml`

2. **实现 main_activity.rb**
   - 实现完整的用户界面
   - 实现 11 个功能模块

3. **测试完整流程**
   - 端到端测试所有功能
   - 验证多线程、传感器、悬浮窗等高级功能

4. **文档和发布**
   - 更新使用文档
   - 发布 demo APK

---

## 技术细节

### 3s 瞬间视频实现方案

有两种实现方式：

**方案 1：系统录像 Intent（当前框架支持）**
```ruby
camera_record_video do |success, path|
  # 用户手动停止录像
end
```

**方案 2：MediaRecorder API（需要扩展）**
- 使用 Camera2 API 实现实时预览
- 在检测到匹配时自动录制 3 秒
- 自动停止并保存

### 悬浮窗实现

当前框架提供的 `overlay_show` 是简化实现。完整悬浮窗需要：
- SYSTEM_ALERT_WINDOW 权限
- WindowManager.LayoutParams 配置
- 自定义 View 布局

### 二维码比对算法

- 使用 ZXing 的 `QRCodeMultiReader` 支持多码扫描
- 字符串精确匹配
- 可扩展为模糊匹配（编辑距离）

---

## 依赖汇总

### Kotlin 依赖

```kotlin
dependencies {
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("com.github.equationl.paddleocr4android:ncnnandroidppocr:v1.3.0")
}
```

### Android 权限

```xml
<!-- 相机 -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" />

<!-- 录音 -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- 存储 -->
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

<!-- 通知 -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- 悬浮窗（需手动授权） -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

---

## 已知限制

1. **相机预览**: 当前框架通过系统 Intent 拍照，无法实现实时预览和自动录制
2. **悬浮窗权限**: 无法通过代码申请，需用户手动在设置中授权
3. **OCR 依赖**: PaddleOCR 模型较大（~50MB），首次初始化可能较慢
4. **多线程**: Ruby 线程通过 Kotlin Coroutine 实现，不是真正的 Ruby Fibers
5. **文件路径**: 不同设备的存储路径可能不同，需要动态处理

---

## 后续扩展方向

1. **Camera2 实时预览**: 实现自定义相机预览界面
2. **ML Kit 集成**: 使用 Google ML Kit 替代 PaddleOCR
3. **视频处理**: 使用 FFmpeg 处理视频（缩略图、剪辑）
4. **NFC 支持**: 增加二维码+NFC 双重验证
5. **云端同步**: 将扫描记录上传到服务器
6. **分享功能**: 分享扫码结果到社交媒体
