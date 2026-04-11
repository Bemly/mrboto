# CLAUDE.md

This file provides guidance to Claude Code when working with this project.

## Project Overview

mrboto embeds mruby 3.4.0 into Android applications as a reusable library, with a "glue" framework (mrboto-glue) enabling 100% Ruby Android app development via a three-layer architecture.

Architecture:
- `:mrboto` — Android Library module (`com.android.library`), published as AAR
- `:demo` — Demo app that depends on `:mrboto`, shows Ruby DSL usage

## Key Directories

- `mrboto/src/main/cpp/` — C JNI bridge (`native-lib.c`, `android-jni-bridge.c`), CMake, vendored mruby headers/libs
- `mrboto/src/main/kotlin/com/mrboto/` — Kotlin API layer
  - `MRuby.kt` — core eval API + framework APIs (registerAndroidClasses, dispatchLifecycle, loadScript, registerJavaObject, lookupJavaObject)
  - `MrbotoApplication.kt` — bootstraps global MRuby, loads core Ruby scripts
  - `MrbotoActivityBase.kt` — Activity base class, lifecycle delegation, setViewClickListener
  - `ViewListeners.kt` — Click/Text/Check listeners delegating to mruby
  - `JavaObjectWrapper.kt` — registry reference docs
- `mrboto/src/main/assets/mrboto/` — Ruby DSL core scripts
  - `core.rb` — Mrboto module, JavaObject base, callback registry, native method stubs
  - `layout.rb` — MATCH_PARENT, WRAP_CONTENT, Gravity, Orientation, dp()
  - `activity.rb` — Mrboto::Activity with lifecycle hooks, set_content_view
  - `widgets.rb` — Widget builders (linear_layout, button, text_view, etc.) + top-level DSL
  - `helpers.rb` — toast, start_activity, get_extra, shared_preferences, run_on_ui_thread
- `demo/` — Demo app showing Ruby-driven Activities
- `mruby/` — git submodule at tag 3.4.0

## Common Tasks

### Rebuild mruby
```bash
cd mruby && rake deep_clean && cd ..
./build-android.sh
```

### Build Demo APK
```bash
./gradlew :demo:assembleDebug
```

### Publish to local Maven
```bash
./gradlew :mrboto:publishToMavenLocal
```

## Technical Stack

| 组件 | 版本 |
|------|------|
| mruby | 3.4.0 |
| Android NDK | r29 (29.0.14206865) |
| minSdk | API 33 (Android 13) |
| targetSdk | API 36 |
| AGP | 9.1.0 |
| CMake | 4.1.2 |

## Dependency Versions

- `androidx.core:core-ktx:1.18.0`
- `androidx.appcompat:appcompat:1.7.1`
- `com.google.android.material:material:1.13.0`
- `androidx.constraintlayout:constraintlayout:2.2.1`

## Important Constraints

- mruby uses rake, not CMake. Libraries are prebuilt and linked as `STATIC IMPORTED`.
- `build-android.sh` sets `ANDROID_NDK_HOME` and runs `rake` with `MRUBY_CONFIG` pointing to `build_config.rb`.
- `build_config.rb` is at the project root; `rake` must be run from `mruby/` directory with `MRUBY_CONFIG` env var.
- The library module is named `mrboto/` (not `app/`). Use `:mrboto` for Gradle tasks.
- `local.properties` only needs `sdk.dir` — AGP manages NDK via `ndkVersion` in `build.gradle.kts`.
- AGP 9.1.0 has built-in Kotlin support, so `org.jetbrains.kotlin.android` plugin is not needed.
- `compileSdk` is 36 (required by androidx.core 1.18.0+).
- `mrb_state` is NOT thread-safe. The Kotlin wrapper ensures serial access per instance.
- C code uses `mrb_gc_arena_save/restore` to prevent memory leaks from repeated eval calls.

## Architecture Details

### Java Object Registry (C side)
`android-jni-bridge.c` maintains a 4096-slot registry of JNI GlobalRefs. Java objects are stored and returned by integer ID. This avoids JNI LocalRef lifecycle issues.

### Lifecycle Dispatch Flow
Java Activity onCreate → `nativeDispatchLifecycle(mrbPtr, activityId, "on_create", bundleId)` → mruby eval `Mrboto.current_activity.on_create(bundle)`

### Event Callback Flow
Ruby `button { toast("Hi") }` → block registered with callback ID → C stores ID on View tag → Kotlin `MrbotoClickListener` fires → `mruby.eval("Mrboto.dispatch_callback($callbackId, $viewId)")` → proc executes

### View Creation
Ruby `linear_layout { }` → `Mrboto::Widgets.create_view(class_name, attrs)` → C `mrboto_create_view()` → JNI `FindClass + NewObject(Context)` → returns registry ID → Ruby wraps as `View.from_registry(id)`

## Publishing

- Maven coordinates: `com.mrboto:mrboto:1.0.0`
- `maven-publish` plugin configured in `mrboto/build.gradle.kts`
- AGP 9 auto-associates release variant (no explicit component needed)

## Style Guidelines

- No emojis
- Concise, direct responses
- Chinese language for planning and conversation (中文)
- Keep C code simple — no abstractions for one-time operations
- Kotlin idiomatic style (use `AutoCloseable`, `check()`, extension functions)
- Auto git commit + push after each change
