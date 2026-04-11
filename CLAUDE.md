# CLAUDE.md

This file provides guidance to Claude Code when working with this project.

## Project Overview

mrboto embeds mruby 3.4.0 into an Android application. The architecture consists of:
- mruby C library cross-compiled via rake for Android (arm64-v8a, x86_64)
- A C JNI bridge (`native-lib.c`) exposing mrb_open/close, eval, version, gc
- A Kotlin wrapper (`MRuby.kt`) implementing AutoCloseable
- A demo app (`MainActivity.kt`) with 11 usage examples

## Key Directories

- `mruby/` — git submodule at tag 3.4.0
- `app/src/main/cpp/` — native C code, CMake, vendored mruby headers/libs
- `app/src/main/kotlin/com/mrboto/` — Kotlin source
- `build_config.rb` — mruby cross-compilation config
- `build-android.sh` — one-command build script for mruby → Android libs

## Common Tasks

### Rebuild mruby
```bash
# Clean and rebuild
cd mruby && rake deep_clean && cd ..
./build-android.sh
```

### Compile .rb to .mrb
```bash
mruby/build/host/bin/mrbc -o app/src/main/assets/output.mrb input.rb
```

### Build Android APK
```bash
./gradlew assembleDebug
```

## Important Constraints

- mruby uses rake, not CMake. Libraries are prebuilt and linked as `STATIC IMPORTED`.
- The host `mrbc` compiler is needed to precompile `.rb` to `.mrb` (Android cross-compiled `mrbc` won't run on host).
- `mrb_state` is NOT thread-safe. The Kotlin wrapper ensures serial access per instance.
- C code uses `mrb_gc_arena_save/restore` to prevent memory leaks from repeated eval calls.
- Minimum API 33 (Android 13), target API 35, NDK r29.
- `build-android.sh` sets `ANDROID_NDK_HOME` and runs `rake` with `MRUBY_CONFIG` pointing to `build_config.rb`.
- `build_config.rb` is at the project root; `rake` must be run from `mruby/` directory with `MRUBY_CONFIG` env var.

## Style Guidelines

- No emojis
- Concise, direct responses
- Chinese language for planning and conversation (中文)
- Keep C code simple — no abstractions for one-time operations
- Kotlin idiomatic style (use `AutoCloseable`, `check()`, extension functions)
