# mrboto

在 Android 应用中嵌入 [mruby 3.4.0](https://github.com/mruby/mruby) 的轻量级框架。

## 特性

- 嵌入完整的 mruby Ruby 运行时到 Android 应用
- 支持执行 Ruby 源代码字符串和 `.mrb` 预编译字节码
- Kotlin API 封装 (`AutoCloseable`，线程安全 per-instance)
- C JNI 桥接层，含完整的异常处理和 GC 管理
- 支持 `arm64-v8a` 和 `x86_64` ABI
- 示例 Demo App，展示 11 种用法

## 技术栈

| 组件 | 版本 |
|------|------|
| mruby | 3.4.0 |
| Android NDK | r29 |
| minSdk | API 33 (Android 13) |
| targetSdk | API 35 |
| Kotlin | 2.1.0 |
| AGP | 8.7.3 |
| CMake | 3.31 |

## 快速开始

### 前置条件

- [Ruby](https://www.ruby-lang.org/) + `rake`
- [Android NDK r29](https://developer.android.com/ndk)
- [Android SDK](https://developer.android.com/studio) (API 33+)

### 1. 克隆项目

```bash
git clone --recursive https://github.com/YOUR_USERNAME/mrboto.git
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
- 复制头文件和 `.a` 到 `app/src/main/cpp/mruby/`

### 3. 编译 Ruby 脚本为字节码 (可选)

```bash
mruby/build/host/bin/mrbc -o app/src/main/assets/hello.mrb hello.rb
```

### 4. 构建 Android 应用

**Android Studio 方式：**
1. 打开 Android Studio → Open → 选择项目根目录
2. 等待 Gradle 同步和 CMake 构建
3. 点击 Run 按钮

**命令行方式：**
```bash
./gradlew assembleDebug
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
├── build_config.rb            # mruby 构建配置
├── build-android.sh           # 一键构建脚本
├── hello.rb                   # 示例 Ruby 脚本
├── app/
│   ├── build.gradle.kts       # Android 应用构建配置
│   └── src/main/
│       ├── cpp/
│       │   ├── CMakeLists.txt # CMake 构建配置
│       │   ├── native-lib.c   # JNI 桥接层
│       │   └── mruby/
│       │       ├── include/   # mruby 头文件
│       │       └── lib/       # 预编译静态库 (按 ABI 分类)
│       ├── kotlin/com/mrboto/
│       │   ├── MRuby.kt       # Kotlin 封装类
│       │   └── MainActivity.kt# 示例 Demo
│       └── assets/
│           └── hello.mrb      # 预编译 Ruby 字节码
└── mruby/                     # mruby 3.4.0 git submodule
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
# 清理旧构建
cd mruby && rake deep_clean && cd ..

# 重新构建
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
