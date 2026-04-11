# mrboto

在 Android 应用中嵌入 [mruby 3.4.0](https://github.com/mruby/mruby) 的轻量级框架，提供 100% Ruby 开发 DSL。

## 特性

- 嵌入完整的 mruby Ruby 运行时到 Android 应用
- 支持执行 Ruby 源代码字符串和 `.mrb` 预编译字节码
- **mrboto-glue 胶水框架** — 三层架构实现纯 Ruby Android 开发
  - JNI 桥接 (C) — 全局引用注册表、类型转换、生命周期分发
  - 静态封装 (Kotlin) — MrbotoApplication、MrbotoActivityBase、ViewListeners
  - Ruby DSL — widget 构建器、Activity 生命周期钩子、helpers
- Kotlin API 封装 (`AutoCloseable`，线程安全 per-instance)
- C JNI 桥接层，含完整的异常处理和 GC 管理
- 支持 `arm64-v8a` 和 `x86_64` ABI
- 示例 Demo App，展示 Ruby DSL 用法

## 使用方式

### 作为 Gradle 依赖

```kotlin
dependencies {
    implementation("com.mrboto:mrboto:1.0.0")
}
```

### 本地开发 / 发布到本地 Maven

```bash
./gradlew :mrboto:publishToMavenLocal
```

然后在你的项目中：

```kotlin
dependencies {
    implementation("com.mrboto:mrboto:1.0.0")
}
```

### 直接依赖子模块

```kotlin
// settings.gradle.kts
include(":mrboto")
project(":mrboto").projectDir = file("path/to/mrboto")

// app/build.gradle.kts
dependencies {
    implementation(project(":mrboto"))
}
```

## 技术栈

| 组件 | 版本 |
|------|------|
| mruby | 3.4.0 |
| Android NDK | r29 (29.0.14206865) |
| minSdk | API 33 (Android 13) |
| targetSdk | API 36 |
| AGP | 9.1.0 |
| CMake | 4.1.2 |

## 快速开始

### 前置条件

- [Ruby](https://www.ruby-lang.org/) + `rake`
- [Android NDK r29](https://developer.android.com/ndk)
- [Android SDK](https://developer.android.com/studio) (API 33+)

### 1. 克隆项目

```bash
git clone --recursive https://github.com/Bemly/mrboto.git
cd mrboto
```

### 2. 构建 mruby 静态库

```bash
export ANDROID_NDK_HOME=/path/to/android-ndk-r29
./build-android.sh
```

此脚本会自动：
- 编译 host 工具 (生成 `mrbc`)
- 交叉编译 arm64-v8a 和 x86_64 静态库
- 复制头文件和 `.a` 到 `mrboto/src/main/cpp/mruby/`

### 3. 运行 Demo

在 Android Studio 中打开项目，选择 `demo` 配置，点击 Run。

或编译 APK：

```bash
./gradlew :demo:assembleDebug
```

## 使用方法

### 基础 Kotlin API

```kotlin
val mruby = MRuby()
val result = mruby.eval("1 + 2 * 3")  // "7"
mruby.close()
```

### 纯 Ruby Activity（推荐）

继承 `MrbotoActivityBase`，用 Ruby DSL 定义 UI 和逻辑：

```kotlin
// MyActivity.kt
class MyActivity : MrbotoActivityBase() {
    override fun getScriptPath(): String = "main_activity.rb"
}
```

```ruby
# assets/main_activity.rb
class MainActivity < Mrboto::Activity
  def on_create(bundle)
    super
    self.content_view = linear_layout(orientation: :vertical) do
      text_view(text: "Hello!", text_size: 24)
      button(text: "Click") { toast("Clicked!") }
    end
  end
end
```

在 `AndroidManifest.xml` 中设置 Application：

```xml
<application android:name="com.mrboto.MrbotoApplication" ...>
```

### Ruby DSL 可用方法

| 方法 | 说明 |
|------|------|
| `toast("msg")` | 显示 Toast |
| `linear_layout { }` | 创建 LinearLayout |
| `text_view(text: "...")` | 创建 TextView |
| `button(text: "...") { }` | 创建 Button，支持点击回调 |
| `edit_text { }` | 创建 EditText |
| `image_view { }` | 创建 ImageView |
| `scroll_view { }` | 创建 ScrollView |
| `shared_preferences("name")` | 访问 SharedPreferences |
| `start_activity(class_name: "...")` | 启动新 Activity |
| `get_extra("key")` | 读取 Intent extra |
| `run_on_ui_thread { }` | 在 UI 线程执行块 |

完整 widget 列表：`linear_layout`, `text_view`, `button`, `edit_text`, `image_view`, `scroll_view`, `relative_layout`, `check_box`, `switch_widget`, `progress_bar`, `spinner`, `radio_group`, `web_view`, `frame_layout`, `table_layout`

## 项目结构

```
mrboto/
├── mrboto/                          # ← Android Library 模块
│   ├── build.gradle.kts             #   com.android.library + maven-publish
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── cpp/
│       │   ├── CMakeLists.txt
│       │   ├── native-lib.c         # 基础 JNI eval
│       │   ├── android-jni-bridge.c # mruby-glue: 注册表、View创建、helper
│       │   ├── android-jni-bridge.h
│       │   └── mruby/
│       │       ├── include/         # mruby 头文件
│       │       └── lib/             # 预编译静态库
│       ├── assets/mrboto/           # Ruby DSL 核心脚本
│       │   ├── core.rb              # Mrboto 模块、JavaObject 基类
│       │   ├── layout.rb            # 布局常量、dp 转换
│       │   ├── activity.rb          # Activity 生命周期钩子
│       │   ├── widgets.rb           # Widget DSL
│       │   └── helpers.rb           # toast、Intent、SharedPreferences
│       └── kotlin/com/mrboto/
│           ├── MRuby.kt             # 公共 API
│           ├── MrbotoApplication.kt # 启动时初始化 MRuby
│           ├── MrbotoActivityBase.kt# Activity 基类
│           ├── ViewListeners.kt     # View 事件监听器
│           └── JavaObjectWrapper.kt # JNI 注册表封装
├── demo/                            # ← Demo App
│   └── src/main/
│       ├── kotlin/com/mrboto/demo/
│       │   └── DemoActivity.kt      # 纯 Ruby 驱动的 Activity
│       ├── assets/
│       │   ├── main_activity.rb     # Ruby DSL 示例
│       │   └── *.rb                 # 基础 Ruby 脚本
│       └── res/...
├── build_config.rb                  # mruby 构建配置
├── build-android.sh                 # 一键构建脚本
└── mruby/                           # mruby 3.4.0 git submodule
```

## 架构

```
[ Ruby Activity DSL ]
        |
   widgets.rb / activity.rb / helpers.rb
        |
   MrbotoApplication / MrbotoActivityBase (Kotlin)
        |
   MRuby.kt (AutoCloseable wrapper)
        |
   JNI Interface (android-jni-bridge.c)
        |
   [ GlobalRef Registry (4096 slots) ]
        |
[ Android Java APIs: View, Intent, Toast, SharedPreferences ]
        |
[ libmruby.a ] (per ABI: arm64-v8a, x86_64)
```

### Java 对象传递

C 侧维护 4096 个 JNI GlobalRef 的注册表。Java 对象存入后返回整数 ID，mruby 通过 ID 引用，避免 JNI 局部引用生命周期问题。

### 生命周期分发

```
Java onCreate → nativeDispatchLifecycle → mruby eval →
  Mrboto.current_activity.on_create(bundle)
```

### 事件回调

```
Ruby: button { toast("Hi") }
  → block 注册到回调表获得 ID
  → C 存储 ID 到 View tag
  → Java onClick → 调用 mruby dispatch_callback(ID)
  → 找到 proc 执行
```

## 重新构建 mruby

```bash
cd mruby && rake deep_clean && cd ..
./build-android.sh
```

## 支持的 mruby gembox

- `stdlib` — 标准库核心
- `stdlib-ext` — 标准库扩展
- `math` — 数学模块
- `metaprog` — 元编程 (eval, send 等)
- `stdlib-io` — 字符串 I/O (非文件 I/O)

## License

MIT
