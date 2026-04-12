# CLAUDE.md

This file provides guidance to Claude Code when working with this project.

## Project Overview

mrboto embeds mruby 3.4.0 into Android applications as a reusable library, with a "glue" framework (mrboto-glue) enabling 100% Ruby Android app development via a three-layer architecture.

Architecture:
- `:mrboto` — Android Library module (`com.android.library`), published as AAR
- `:demo` — Demo app that depends on `:mrboto`, shows Ruby DSL usage

## Key Directories

- `app/src/main/cpp/` — C JNI bridge (`native-lib.c`, `android-jni-bridge.c`), CMake, vendored mruby headers/libs
- `app/src/main/kotlin/moe/bemly/mrboto/` — Kotlin API layer
  - `MRuby.kt` — core eval API + framework APIs (registerAndroidClasses, dispatchLifecycle, loadScript, registerJavaObject, lookupJavaObject)
  - `MrbotoApplication.kt` — bootstraps global MRuby, loads core Ruby scripts
  - `MrbotoActivityBase.kt` — Activity base class, lifecycle delegation, setViewClickListener
  - `ViewListeners.kt` — Click/Text/Check listeners delegating to mruby
  - `JavaObjectWrapper.kt` — registry reference docs
- `app/src/main/assets/mrboto/` — Ruby DSL core scripts
  - `core.rb` — Mrboto module, JavaObject base, callback registry, native method stubs
  - `layout.rb` — MATCH_PARENT, WRAP_CONTENT, Gravity, Orientation, dp()
  - `activity.rb` — Mrboto::Activity with lifecycle hooks, set_content_view
  - `widgets.rb` — Widget builders (linear_layout, button, text_view, etc.) + top-level DSL
  - `helpers.rb` — toast, start_activity, get_extra, shared_preferences, run_on_ui_thread, dialog, snackbar, popup_menu, animations
- `demo/` — Demo app showing Ruby-driven Activities
- `mruby/` — git submodule at tag 3.4.0
- `app/src/androidTest/kotlin/moe/bemly/mrboto/` — instrumented test suite (170+ tests)

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

### Run tests
```bash
./gradlew :mrboto:connectedAndroidTest
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
- `androidx.drawerlayout:drawerlayout:1.3.0`
- `androidx.coordinatorlayout:coordinatorlayout:1.3.0`
- `androidx.viewpager:viewpager:1.2.0`

## Important Constraints

- mruby uses rake, not CMake. Libraries are prebuilt and linked as `STATIC IMPORTED`.
- `build-android.sh` sets `ANDROID_NDK_HOME` and runs `rake` with `MRUBY_CONFIG` pointing to `build_config.rb`.
- `build_config.rb` is at the project root; `rake` must be run from `mruby/` directory with `MRUBY_CONFIG` env var.
- The library module is `app/` (named `:mrboto` in Gradle). Use `:mrboto` for Gradle tasks.
- `local.properties` only needs `sdk.dir` — AGP manages NDK via `ndkVersion` in `build.gradle.kts`.
- AGP 9.1.0 has built-in Kotlin support, so `org.jetbrains.kotlin.android` plugin is not needed.
- `compileSdk` is 36 (required by androidx.core 1.18.0+).
- `mrb_state` is NOT thread-safe. The Kotlin wrapper ensures serial access per instance.
- C code uses `mrb_gc_arena_save/restore` to prevent memory leaks from repeated eval calls.
- **Shared VM architecture**: `nativeEvalString` and `nativeEvalBytecode` use the shared `mrb_state` VM.
  They do NOT create temporary VMs (`mrb_open()`), because a new VM has no `Mrboto::Activity`,
  no registered JNI methods, and no loaded core scripts. All eval calls share the same VM instance
  registered via `nativeRegisterAndroidClasses` + 5 core rb scripts. The `isolated-vm` branch
  contains an abandoned experiment with per-eval temporary VMs (see branch for reference).
  `FindClass`, `NewStringUTF`, `NewObject`, `GetObjectClass`, `CallObjectMethod` should be deleted
  with `DeleteLocalRef()`. Mixing them causes `JNI DETECTED ERROR: expected reference of kind Local
  but found Global` crashes.
- **JNI exception handling pattern**: After any JNI call that may throw
  (`CallStaticObjectMethod`, `NewObject`, `CallObjectMethod`, `CallVoidMethod`, etc.),
  **check and clear the exception BEFORE checking the return value**:
  ```c
  jobject result = CallStaticObjectMethod(env, cls, mid, ...);
  if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); }
  else if (result != NULL) { ... }
  ```
  Do NOT use `if (result != NULL && !ExceptionCheck(env))` — if the call throws,
  `result` is NULL and the exception check is skipped, leaving a pending exception
  that causes `JNI DETECTED ERROR` crash on the next JNI call.
  Known Android APIs that throw: `Snackbar.make()` (no parent view),
  `AlertDialog.Builder.create()` (non-Looper thread), `PopupMenu` constructor (no parent view).
- **`mrb_get_args` format strings**: Use `"o"` (mrb_value) for parameters that can be nil, not `"z"`
  (const char* which can be NULL and crash `NewStringUTF`). Use `mrb_obj_as_string()` to safely
  convert to string.
- **mruby `private` keyword**: Using `private` inside a class can affect method visibility for
  subsequently defined methods in mruby's parser. Avoid `private` in module/class definitions
  that precede `def self.xxx` methods.
- **Error extraction**: `safe_extract_error()` in `native-lib.c` extracts exception as
  `"ClassName: message"` format (e.g., `"SyntaxError: syntax error"`, `"NoMethodError: undefined
  method 'foo'"`). If message extraction fails, falls back to `mrb_inspect` for debug info.
  `nativeLoadScript` in `android-jni-bridge.c` uses `mrb_protect` to safely catch compilation errors.
- **Test hang root cause**: `PopupMenu.show()` on an unattached `View(context)` hangs permanently
  waiting for a window token — it does NOT throw an exception. The test runs on a non-Looper thread
  (AndroidJUnitRunner), so the window attach process never completes. Fix: do NOT call `show()`
  in instrumented tests; constructor + `getMenu()` + `add()` items is sufficient.
  `Dialog.show()` throws `RuntimeException` on non-Looper threads (caught and cleared).
  `Snackbar.make()` throws `IllegalArgumentException` when View has no parent (caught and cleared).
  `View.startAnimation()` does NOT block but Animation objects accumulate on unattached Views.
- **Registry cleanup between tests**: `MrbotoTestRule.finally` calls `mruby.clearRegistry()`
  after `mruby.close()` to free all JNI GlobalRefs. The C registry (`g_registry`) is a process-level
  static that is NOT cleaned by `mrb_close()`. Without this, 200+ tests accumulate ~600+ GlobalRefs,
  potentially causing memory pressure and test instability.

## Architecture Details

### Java Object Registry (C side)
`android-jni-bridge.c` maintains a 4096-slot registry of JNI GlobalRefs. Java objects are stored and returned by integer ID. This avoids JNI LocalRef lifecycle issues.

### Lifecycle Dispatch Flow
Java Activity onCreate → `nativeDispatchLifecycle(mrbPtr, activityId, "on_create", bundleId)` → mruby eval `Mrboto.current_activity.on_create(bundle)`

### Event Callback Flow
Ruby `button { toast("Hi") }` → block registered with callback ID → C stores ID on View tag → Kotlin `MrbotoClickListener` fires → `mruby.eval("Mrboto.dispatch_callback($callbackId, $viewId)")` → proc executes

### View Creation
Ruby `linear_layout { }` → `Mrboto::Widgets.create_view(class_name, attrs)` → C `mrboto_create_view()` → JNI `FindClass + NewObject(Context)` → returns registry ID → Ruby wraps as `View.from_registry(id)`

### Helpers C Bridge
`helpers.rb` dialog/snackbar/popup/animation methods call C bridge functions directly (not Java reflection):
- `_show_dialog(context_id, title, message, buttons_json)` — AlertDialog.Builder via JNI (create + show, exception cleared on non-Looper thread)
- `_show_snackbar(context_id, view_id, message, duration)` — Snackbar.make + show (exception cleared when View has no parent)
- `_show_popup_menu(context_id, view_id, items_json)` — PopupMenu constructor + getMenu + add items (NO show() — hangs on unattached Views)
- `_animate_fade/_animate_translate/_animate_scale` — View animations via JNI (create Animation + startAnimation on View)

All take registry IDs, look up GlobalRefs via `mrboto_lookup_ref()`, use Android APIs directly.

## Publishing

- Maven coordinates: `moe.bemly.mrboto:mrboto:26.4.11`
- `maven-publish` plugin configured in `app/build.gradle.kts`
- AGP 9 auto-associates release variant (no explicit component needed)

## Test Coverage

| Test File | Tests | Covers |
|---|---|---|
| `MRubyTest.kt` | 14 | eval, version, gc, close, registerJavaObject, lookupJavaObject, loadScript |
| `ErrorHandlingTest.kt` | 15 | syntax errors, runtime errors, invalid IDs, closed VM |
| `BridgeMethodsTest.kt` | 17 | _toast, _start_activity, _get_extra, _sp_get/put_int, _app_context, _dp_to_px, _create_view, _set_on_click, _set_content_view, _run_on_ui_thread, stubs |
| `RegistryStressTest.kt` | 6 | sequential IDs, 4096 capacity limit, overflow rejection |
| `LayoutConstantsTest.kt` | 16 | MATCH_PARENT, WRAP_CONTENT, Gravity, Orientation, dp() |
| `ActivityClassTest.kt` | 11 | Activity instantiation, content_view, title, lifecycle hooks |
| `WidgetsTest.kt` | 74 | Widget creation, attributes, nesting, View.from_registry, 29 new widget hierarchy, 13 method existence, 10 functional tests |
| `CallbackDispatchTest.kt` | 8 | register_callback, dispatch_callback, dispatch_text_changed, dispatch_checked |
| `HelpersTest.kt` | 24 | toast, SharedPreferences, package_name, dialog, snackbar, popup_menu, animations |
| `LifecycleDispatchTest.kt` | 7 | dispatchLifecycle, accessors, hook ordering |

## Style Guidelines

- No emojis
- Concise, direct responses
- Chinese language for planning and conversation (中文)
- Keep C code simple — no abstractions for one-time operations
- Kotlin idiomatic style (use `AutoCloseable`, `check()`, extension functions)
- Auto git commit + push after each change

## Git Workflow

- After every code change (fix, refactor, doc update), commit and push to `origin/main`
- For wiki changes, commit and push to the wiki repo (`mrboto.wiki.git`) on `master` branch
