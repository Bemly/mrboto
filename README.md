# mrboto

Embed [mruby 3.4.0](https://mruby.org/) into Android applications as a reusable library, with a glue framework enabling 100% Ruby Android app development.

[中文版](README.zh.md)

## Screenshot
<span>
    <img src="screenshot/1.jpg" width="300"><img src="screenshot/2.png" width="300">
</span>

## Architecture

Three layers from native to user-facing:

| Layer | Language | Purpose |
|---|---|---|
| C JNI Bridge | C | Embeds mruby, bridges Ruby calls to Android Java APIs via reflection |
| Kotlin Wrapper | Kotlin | Manages mruby VM lifecycle, Java object registry, event listeners |
| Ruby DSL | Ruby | User-facing DSL for UI, lifecycle, helpers |

See [Architecture](https://github.com/Bemly/mrboto/wiki/Architecture) for details.

## Maven Coordinates

```
moe.bemly.mrboto:mrboto:26.4.13
```

## Quick Start

### 1. Add dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("moe.bemly.mrboto:mrboto:26.4.13")
}
```

### 2. Configure Application

```xml
<!-- AndroidManifest.xml -->
<application
    android:name="moe.bemly.mrboto.MrbotoApplication"
    ...>
```

### 3. Create an Activity (Zero Kotlin)

No Kotlin code needed — just declare `RubyActivity` in your manifest:

```xml
<!-- AndroidManifest.xml -->
<activity android:name="moe.bemly.mrboto.RubyActivity">
    <meta-data android:name="mrboto_script" android:value="main_activity.rb" />
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

For dynamic navigation between Ruby Activities:

```ruby
# In a Ruby Activity:
start_ruby_activity(script_path: "second_activity.rb")
```

### 4. Write Ruby UI

```ruby
# src/main/assets/main_activity.rb
class MainActivity < Mrboto::Activity
  def on_create(bundle)
    super
    self.title = "Hello mrboto"
    self.content_view = linear_layout(orientation: :vertical, padding: 16) {
      text_view(text: "Hello", text_size: 20)
      button(text: "Click me") {
        toast("Hello from mruby!")
      }
    }
  end
end
Mrboto._ruby_activity_class = MainActivity
```

## API Reference

| Document | Link |
|---|---|
| Kotlin API | [Wiki](https://github.com/Bemly/mrboto/wiki/Kotlin-API) |
| Ruby DSL | [Wiki](https://github.com/Bemly/mrboto/wiki/Ruby-DSL) |
| C Bridge | [Wiki](https://github.com/Bemly/mrboto/wiki/C-Bridge) / [中文版](https://github.com/Bemly/mrboto/wiki/C-Bridge-zh) |
| Architecture | [Wiki](https://github.com/Bemly/mrboto/wiki/Architecture) |
| Testing | [Wiki](https://github.com/Bemly/mrboto/wiki/Testing) |

### Ruby DSL Quick Reference

**Widgets (44):** `linear_layout`, `text_view`, `button`, `edit_text`, `image_view`, `scroll_view`, `relative_layout`, `check_box`, `switch_widget`, `progress_bar`, `spinner`, `radio_group`, `web_view`, `frame_layout`, `table_layout`, `seek_bar`, `rating_bar`, `auto_complete_text_view`, `search_view`, `toolbar`, `number_picker`, `date_picker`, `time_picker`, `calendar_view`, `video_view`, `chronometer`, `text_clock`, `grid_view`, `list_view`, `nested_scroll_view`, `horizontal_scroll_view`, `view_pager`, `tab_layout`, `view_switcher`, `floating_action_button`, `material_button`, `card_view`, `text_input_layout`, `text_input_edit_text`, `bottom_navigation_view`, `app_bar_layout`, `drawer_layout`, `coordinator_layout`, `navigation_view`

**View Methods:** `fade_in`, `fade_out`, `animate_translate`, `animate_scale`, `slide_in_bottom`, `pulse`, `clear_animation`, `width`, `height`, `visible?`, `show`, `hide`, `request_focus`, `perform_click`

**Activity Methods:** `show_dialog`, `show_snackbar`, `show_popup_menu`

**Helpers:** `toast`, `dialog`, `snackbar`, `popup_menu`, `start_activity`, `start_ruby_activity`, `get_extra`, `shared_preferences`, `run_on_ui_thread`, `package_name`

## Technical Stack

| Component | Version |
|---|---|
| mruby | 3.4.0 |
| Android NDK | r29 |
| minSdk | API 33 |
| targetSdk | API 36 |
| AGP | 9.1.0 |
| CMake | 4.3.1 |

## Development

### Prerequisites

- [Ruby](https://www.ruby-lang.org/) + `rake`
- [Android NDK r29](https://developer.android.com/ndk)
- [Android SDK](https://developer.android.com/studio) (API 33+)

### Build mruby static libraries

```bash
cd mruby && rake deep_clean && cd ..
./build-android.sh
```

### Publish to local Maven

```bash
./gradlew :mrboto:publishToMavenLocal
```

### Run tests

```bash
./gradlew :mrboto:connectedAndroidTest
```

## Project Structure

```
├── app/                             # Android Library module (:mrboto)
│   ├── build.gradle.kts
│   └── src/main/
│       ├── cpp/
│       │   ├── CMakeLists.txt
│       │   ├── native-lib.c         # mruby VM lifecycle (open/close/eval/gc)
│       │   ├── jni-registry.c/h     # 4096-slot JNI GlobalRef registry
│       │   ├── jni-helpers.c/h      # toast/sp/start_activity C helpers
│       │   ├── jni-ui.c/h           # dialog/snackbar/popup/animation C
│       │   └── jni-bindings.c       # JNI_OnLoad + mrboto native methods
│       ├── assets/mrboto/
│       │   ├── core.rb              # Mrboto module, JavaObject, callbacks
│       │   ├── layout.rb            # Layout constants, dp()
│       │   ├── activity.rb          # Activity lifecycle + instance methods
│       │   ├── widgets.rb           # 44 widgets + View instance methods
│       │   └── helpers.rb           # toast, dialog, snackbar, animations, etc.
│       └── kotlin/moe/bemly/mrboto/
│           ├── MRuby.kt             # AutoCloseable mruby wrapper
│           ├── MrbotoApplication.kt # Bootstraps global MRuby
│           ├── MrbotoActivityBase.kt# Activity lifecycle + UI helpers
│           ├── RubyActivity.kt      # Zero-Kotlin app support
│           ├── ViewListeners.kt     # Click/Text/Check listeners
│           └── JavaObjectWrapper.kt # Registry reference docs
├── demo/                            # Demo app (zero Kotlin)
├── showcase/                        # Showcase app (44 widgets demo)
├── build_config.rb                  # mruby build config
├── build-android.sh                 # One-shot build script
└── mruby/                           # git submodule (tag 3.4.0)
```

## How It Works

### Java Object Registry

C side maintains a 4096-slot array of JNI GlobalRefs. Java objects are stored and returned by integer ID. This avoids JNI LocalRef lifecycle issues.

### Lifecycle Dispatch

```
Java onCreate → nativeDispatchLifecycle → mruby eval →
  Mrboto.current_activity.on_create(bundle)
```

### Event Callbacks

```
Ruby: button { toast("Hi") }
  → block registered with callback ID
  → Activity.setViewClickListener(view_id, callback_id)
  → MrbotoClickListener attached to Android View
  → User taps → onClick → mruby.eval("Mrboto.dispatch_callback($id, $viewId)")
  → Block executes
```

### View Creation & Method Calls

```
Ruby: linear_layout { } → Widgets.create_view() → C mrboto_create_view() →
  JNI FindClass + NewObject(Context) → registry ID → View.from_registry(id)

Ruby: view.text = "Hello" → _call_java_method(registry_id, "setText", "Hello") →
  Java reflection: Class.getMethod("setText", CharSequence.class) + Method.invoke()
  → Uses Integer.TYPE, Float.TYPE, Boolean.TYPE for primitive param matching
  → Uses CharSequence.class for String params (setText, setHint, etc.)
  → Uses View.class for Data params (addView, setContentView, etc.)
```

## Acknowledgments

Inspired by [Ruboto/JRuby9K_POC](https://github.com/ruboto/JRuby9K_POC), which pioneered the idea of running Ruby on Android. mrboto takes a different approach — embedding mruby instead of the full JVM — but stands on the shoulders of that earlier work.

## License

MIT
