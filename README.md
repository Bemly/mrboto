# mrboto

在 Android 应用中嵌入 [mruby 3.4.0](https://github.com/mruby/mruby) 的轻量级框架。

## 特性

- 嵌入完整的 mruby Ruby 运行时到 Android 应用
- 支持执行 Ruby 源代码字符串和 `.mrb` 预编译字节码
- Kotlin API 封装 (`AutoCloseable`，线程安全 per-instance)
- C JNI 桥接层，含完整的异常处理和 GC 管理
- 支持 `arm64-v8a` 和 `x86_64` ABI
- 示例 Demo App，展示 11 种用法

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

### 3. 编译 Ruby 脚本为字节码 (可选)

```bash
mruby/build/host/bin/mrbc -o demo/src/main/assets/hello.mrb hello.rb
```

### 4. 运行 Demo

在 Android Studio 中打开项目，选择 `demo` 配置，点击 Run。

或在命令行：

```bash
./gradlew :demo:assembleDebug
```

## 使用方法

### 在 Kotlin 中使用

```kotlin
val mruby = MRuby()

// 执行 Ruby 源代码
val result = mruby.eval("1 + 2 * 3")
println(result)  // "7"

// 加载预编译字节码
val bytecode = assets.open("script.mrb").use { it.readBytes() }
val result2 = mruby.evalBytecode(bytecode)

// 查询版本
println(mruby.version())  // "3.4.0"

// 手动 GC (通常不需要)
mruby.gc()

// 释放资源
mruby.close()
```

### 支持 try-with-resources

```kotlin
MRuby().use { mruby ->
    val x = mruby.eval("x = 10")
    val y = mruby.eval("x * 2")  // "20"，状态跨调用保持
}
```

## 项目结构

```
mrboto/
├── mrboto/                  # ← Android Library 模块 (可发布为 AAR)
│   ├── build.gradle.kts     #   com.android.library + maven-publish
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── cpp/
│       │   ├── CMakeLists.txt
│       │   ├── native-lib.c      # JNI 桥接层
│       │   └── mruby/
│       │       ├── include/      # mruby 头文件
│       │       └── lib/          # 预编译静态库
│       └── kotlin/com/mrboto/
│           └── MRuby.kt          # 公共 API
├── demo/                    # ← Demo App (展示如何使用 mrboto)
│   └── src/main/
│       ├── kotlin/com/mrboto/demo/MainActivity.kt
│       ├── assets/hello.mrb
│       └── res/...
├── build_config.rb          # mruby 构建配置
├── build-android.sh         # 一键构建脚本
├── hello.rb                 # 示例 Ruby 脚本
└── mruby/                   # mruby 3.4.0 git submodule
```

## 架构

```
[ Android App (Kotlin) ]
         |
    MRuby.kt (AutoCloseable wrapper)
         |
    JNI Interface (System.loadLibrary "mrboto-native")
         |
[ native-lib.c ]
    - mrb_open() / mrb_close()
    - mrb_load_string() / mrb_load_irep_buf()
    - GC arena management
    - Exception handling with backtrace
         |
[ libmruby.a ] (per ABI: arm64-v8a, x86_64)
         |
[ .mrb bytecode ] (in assets/)
```

## 重新构建 mruby

如果需要修改 `build_config.rb` (添加/删除 gembox) 或升级 mruby 版本：

```bash
cd mruby && rake deep_clean && cd ..
./build-android.sh
```

## 支持的 mruby gembox

当前启用的 gembox：
- `stdlib` — 标准库核心
- `stdlib-ext` — 标准库扩展
- `math` — 数学模块
- `metaprog` — 元编程 (eval, send 等)
- `stdlib-io` — 字符串 I/O (非文件 I/O)

## License

MIT
