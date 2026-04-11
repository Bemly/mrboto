# CLAUDE.md

This file provides guidance to Claude Code when working with this project.

## Project Overview

mrboto embeds mruby 3.4.0 into Android applications as a reusable library. The architecture:
- `:mrboto` — Android Library module (`com.android.library`), published as AAR
- `:demo` — Demo app that depends on `:mrboto`, shows usage examples

## Key Directories

- `mrboto/` — Library module (JNI + Kotlin API)
- `mrboto/src/main/cpp/` — native C code, CMake, vendored mruby headers/libs
- `mrboto/src/main/kotlin/com/mrboto/MRuby.kt` — public API
- `demo/` — Demo app showing how to use the library
- `mruby/` — git submodule at tag 3.4.0

## Common Tasks

### Rebuild mruby
```bash
# Clean and rebuild
cd mruby && rake deep_clean && cd ..
./build-android.sh
```

### Compile .rb to .mrb
```bash
mruby/build/host/bin/mrbc -o demo/src/main/assets/hello.mrb input.rb
```

### Build Demo APK
```bash
./gradlew :demo:assembleDebug
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
- The host `mrbc` compiler is needed to precompile `.rb` to `.mrb` (Android cross-compiled `mrbc` won't run on host).
- `mrb_state` is NOT thread-safe. The Kotlin wrapper ensures serial access per instance.
- C code uses `mrb_gc_arena_save/restore` to prevent memory leaks from repeated eval calls.
- `build-android.sh` sets `ANDROID_NDK_HOME` and runs `rake` with `MRUBY_CONFIG` pointing to `build_config.rb`.
- `build_config.rb` is at the project root; `rake` must be run from `mruby/` directory with `MRUBY_CONFIG` env var.
- The library module is named `mrboto/` (not `app/`). Use `:mrboto` for Gradle tasks.
- `local.properties` only needs `sdk.dir` — AGP manages NDK via `ndkVersion` in `build.gradle.kts`.
- AGP 9.1.0 has built-in Kotlin support, so `org.jetbrains.kotlin.android` plugin is not needed.
- `compileSdk` is 36 (required by androidx.core 1.18.0+).

## Publishing

- Publish to local Maven: `./gradlew :mrboto:publishToMavenLocal`
- Maven coordinates: `com.mrboto:mrboto:1.0.0`

## Style Guidelines

- No emojis
- Concise, direct responses
- Chinese language for planning and conversation (中文)
- Keep C code simple — no abstractions for one-time operations
- Kotlin idiomatic style (use `AutoCloseable`, `check()`, extension functions)
