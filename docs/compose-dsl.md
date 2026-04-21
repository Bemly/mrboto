# Compose DSL 文档 / Compose DSL Documentation

## 概述 / Overview

mrboto 框架同时支持两种 UI 渲染模式：

- **View DSL** — 基于 Android 原生 View（`linear_layout`, `edit_text`, `button` 等）
- **Compose DSL** — 基于 Jetpack Compose（`column`, `text`, `button`, `outlined_text_field` 等）

两种模式共享同一个 mruby VM、回调注册机制和所有辅助 API（文件操作、网络、通知等）。

The mrboto framework supports two UI rendering modes simultaneously:

- **View DSL** — based on Android native Views (`linear_layout`, `edit_text`, `button`, etc.)
- **Compose DSL** — based on Jetpack Compose (`column`, `text`, `button`, `outlined_text_field`, etc.)

Both modes share the same mruby VM, callback registry, and all helper APIs (file ops, network, notifications, etc.).

---

## 快速开始 / Quick Start

### View 模式 / View Mode

```ruby
class MyActivity < Mrboto::Activity
  def on_create(bundle)
    super
    self.content_view = linear_layout(orientation: :vertical) do
      text_view(text: "Hello View!", text_size: 24)
      button(text: "Click") { toast("Clicked!") }
    end
  end
end
Mrboto.register_activity_class(MyActivity)
```

### Compose 模式 / Compose Mode

```ruby
class MyActivity < Mrboto::ComposeActivity
  def on_create(bundle)
    super
    set_title("Hello Compose")
    column {
      text("Hello Compose!", font_size: 24)
      button("Click") { toast("Clicked!") }
    }
    set_compose_content
  end
end
Mrboto.register_activity_class(MyActivity)
```

---

## Compose 布局 / Layouts

### Column（纵向排列）

```ruby
column(vertical_arrangement: :center, horizontal_alignment: :center) {
  text("First")
  text("Second")
}
```

| 参数 | 类型 | 说明 |
|------|------|------|
| vertical_arrangement | Symbol | `:top`, `:center`, `:bottom`, `:space_between`, `:space_evenly` |
| horizontal_alignment | Symbol | `:start`, `:center`, `:end` |

### Row（横向排列）

```ruby
row(horizontal_arrangement: :space_evenly, vertical_alignment: :center) {
  text("A")
  text("B")
  text("C")
}
```

| 参数 | 类型 | 说明 |
|------|------|------|
| horizontal_arrangement | Symbol | `:start`, `:center`, `:end`, `:space_between`, `:space_evenly` |
| vertical_alignment | Symbol | `:top`, `:center`, `:bottom` |

### Box（层叠布局）

```ruby
box(content_alignment: :center) {
  text("Background")
  text("Foreground", font_size: 24)
}
```

### Spacer（占位符）

```ruby
spacer(width: 16, height: 16)
```

### 滚动 / Scrolling

```ruby
vertical_scroll {
  (1..20).each { |i| text("Item #{i}") }
}

horizontal_scroll {
  row { (1..10).each { |i| text("Tab #{i}") } }
}
```

### Lazy 列表 / Lazy Lists

```ruby
lazy_column {
  items = ["Apple", "Banana", "Cherry"]
  items.each { |item| text(item) }
}
```

---

## Compose 基础组件 / Basic Components

### Text（文本）

```ruby
text("Hello World",
  color: "FF0000",
  font_size: 20,
  text_align: :center,
  font_family: :monospace
)
```

| 参数 | 类型 | 说明 |
|------|------|------|
| color | String | 十六进制颜色，如 `"FF0000"` |
| font_size | Number | 字号（SP 单位） |
| text_align | Symbol | `:start`, `:center`, `:end`, `:justify` |
| font_family | Symbol | `:default`, `:monospace`, `:serif`, `:sans_serif`, `:cursive` |

### Button（按钮）

```ruby
button("点击运行") {
  toast("按钮被点击了！")
}

text_button("文字按钮") { toast("点击！") }

icon_button(icon: "settings") { toast("设置") }

floating_action_button {
  icon(name: "add")
} { toast("添加") }
```

支持的图标：`light_mode`, `dark_mode`, `settings`, `delete`, `add`, `close`, `menu`, `search`, `home`, `arrow_back`, `arrow_forward`, `refresh`, `play_arrow`, `save`, `content_copy`, `content_paste`, `info`

### TextField（输入框）

```ruby
# 标准 TextField
text_field(@value) { |v| @value = v }

# 带边框的 OutlinedTextField
outlined_text_field(@code,
  hint: "在此输入代码...",
  single_line: false,
  max_lines: 9999,
  modifier: fill_max_height(0.6)
) { |v| @code = v }
```

| 参数 | 类型 | 说明 |
|------|------|------|
| hint | String | 提示文字 |
| single_line | Boolean | 是否单行 |
| max_lines | Integer | 最大行数 |

### 开关和滑块 / Controls

```ruby
switch(checked: true) { |checked| toast("开关: #{checked}") }

checkbox(checked: false) { |checked| toast("勾选: #{checked}") }

slider(value: 0.5, value_range: [0.0, 1.0]) { |v| toast("值: #{v}") }
```

---

## Material3 组件 / Material3 Components

### Scaffold（脚手架）

```ruby
scaffold(
  top_bar: -> { top_app_bar("标题", actions: [
    { icon: "settings", on_click: -> { toast("设置") } }
  ]) },
  bottom_bar: -> {
    row(horizontal_arrangement: :space_evenly, padding: 8) {
      button("按钮1") { }
      button("按钮2") { }
    }
  },
  floating_action_button: -> {
    floating_action_button { icon(name: "add") }
  }
) {
  column { text("主内容") }
}
```

### TopAppBar（顶部导航栏）

```ruby
top_app_bar("页面标题", actions: [
  { icon: "search", on_click: -> { toast("搜索") } },
  { icon: "settings", on_click: -> { toast("设置") } }
])
```

### Card（卡片）

```ruby
card {
  column(padding: 16) {
    text("卡片标题", font_size: 18)
    text("卡片内容")
  }
}
```

### Divider（分割线）

```ruby
divider
```

---

## 修饰符 / Modifiers

修饰符用于控制组件的大小、位置和样式：

```ruby
text("Hello",
  modifier: fill_max_width.padding(16).background_color("1E1E2E")
)

column(modifier: fill_max_height(0.5).weight(1.0)) {
  text("内容")
}
```

### 链式调用 / Chainable Methods

| 方法 | 说明 | 示例 |
|------|------|------|
| `padding(value)` | 内边距 | `.padding(16)` |
| `fill_max_width(factor)` | 填充宽度 | `.fill_max_width` 或 `.fill_max_width(0.5)` |
| `fill_max_height(factor)` | 填充高度 | `.fill_max_height(0.6)` |
| `weight(value)` | 权重分配 | `.weight(1.0)` |
| `width(value)` | 宽度（dp） | `.width(200)` |
| `height(value)` | 高度（dp） | `.height(100)` |
| `background_color(hex)` | 背景色 | `.background_color("1E1E2E")` |
| `align(value)` | 对齐 | `.align(:center)` |
| `aspect_ratio(value)` | 宽高比 | `.aspect_ratio(1.5)` |
| `clip` | 圆角裁剪 | `.clip` |
| `then(other)` | 拼接修饰符 | `.padding(8).then(fill_max_width)` |

### 简写属性 / Shorthand Props

也可以直接通过属性传入（不用 modifier 链）：

```ruby
column(fill_max_width: true, padding: 16) {
  text("Hello")
}
```

---

## AndroidView 嵌入原生视图 / Embedding Native Views

可以在 Compose 中嵌入原生 Android View：

```ruby
android_view(view_type: "moe.bemly.mrboto.LiquidGlassView") {
  # 子节点也会被传递
  row { button("按钮") { } }
}
```

### 液态玻璃示例 / Liquid Glass Example

```ruby
liquid_glass_view(
  shape_type: "rounded_rect",
  corner_radius: 24.0,
  blur_radius: 25.0,
  vibrancy: true
) {
  row(horizontal_arrangement: :space_evenly, padding: 12) {
    button("▶ Run") { run_code }
    button("💾 Save") { save_code }
  }
}
```

| 参数 | 类型 | 说明 |
|------|------|------|
| shape_type | String | `"rounded_rect"`, `"circle"`, `"none"` |
| corner_radius | Float | 圆角半径 |
| blur_radius | Float | 模糊半径 |
| vibrancy | Boolean | 是否启用鲜艳度增强 |

---

## 主题切换 / Theme Toggle

Compose 模式下切换主题需要重建 UI 树：

```ruby
def toggle_theme
  @dark_mode = !@dark_mode
  rebuild_ui  # 重新调用 build_ui + set_compose_content
end

def rebuild_ui
  Mrboto::ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])
  build_ui
end
```

---

## 与 View DSL 的区别 / Differences from View DSL

| 特性 | View DSL | Compose DSL |
|------|----------|-------------|
| 基类 | `Mrboto::Activity` | `Mrboto::ComposeActivity` |
| 渲染方式 | `set_content_view` | `set_compose_content` |
| 控件名称 | `text_view`, `edit_text` | `text`, `outlined_text_field` |
| 事件处理 | `{ }` block = 点击 | `{ }` block = 点击（按钮）/ 值回调（输入框） |
| 布局容器 | `linear_layout` | `column`, `row` |
| 滚动 | `scroll_view` | `vertical_scroll` |
| 状态管理 | 直接修改 View 属性 | 重建 UI 树 |
| 修饰符 | `background_color=`, `padding=` | `modifier: .padding().background_color()` |

---

## 完整示例：代码编辑器 / Full Example: Code Editor

```ruby
class EditorActivity < Mrboto::ComposeActivity
  SCRIPT_PATH = "editor_script.rb"

  def on_create(bundle)
    super
    self.title = "Ruby Editor"
    @dark_mode = true
    @code = DEFAULT_CODE
    @output = ""
    build_ui
  end

  def build_ui
    Mrboto::ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])

    scaffold(
      top_bar: -> { top_app_bar("Ruby Editor", actions: [
        { icon: @dark_mode ? "light_mode" : "dark_mode",
          on_click: -> { toggle_theme } }
      ]) },
      bottom_bar: -> {
        liquid_glass_view(
          shape_type: "rounded_rect", corner_radius: 24.0,
          blur_radius: 25.0, vibrancy: true
        ) {
          row(horizontal_arrangement: :space_evenly, padding: 8) {
            button("▶ Run") { run_code }
            button("💾 Save") { save_code }
            button("📂 Load") { load_code }
            button("✕ Clear") { clear_code }
          }
        }
      }
    ) {
      column(fill_max_width: true) {
        text("代码", font_size: 13, font_family: :monospace)
        outlined_text_field(@code,
          hint: "输入 Ruby 代码...",
          single_line: false, max_lines: 9999,
          modifier: fill_max_height(0.55)
            .then(background_color(@dark_mode ? "1E1E2E" : "FFFFFF"))
        ) { |v| @code = v }

        divider

        text("输出", font_size: 13, font_family: :monospace)
        text(@output, font_size: 12, font_family: :monospace,
          modifier: fill_max_height(0.3)
            .then(background_color(@dark_mode ? "181825" : "F0F0F5"))
            .then(padding(8))
        )
      }
    }

    set_compose_content
  end

  def run_code
    code = @code.to_s
    return if code.strip.empty?
    @output = "执行中..."
    refresh_ui
    begin
      result = Mrboto._eval(code)
      @output = result.nil? ? "(nil)" : result.to_s
    rescue => e
      @output = "错误: #{e.class}\n#{e.message}"
    end
    refresh_ui
  end

  def save_code
    file_write(SCRIPT_PATH, @code.to_s)
    toast("已保存")
  end

  def load_code
    if file_exists?(SCRIPT_PATH)
      @code = file_read(SCRIPT_PATH)
      toast("已加载")
    else
      toast("没有保存的脚本")
    end
    refresh_ui
  end

  def clear_code
    @code = ""; @output = ""
    refresh_ui
  end

  def toggle_theme
    @dark_mode = !@dark_mode
    refresh_ui
  end

  def refresh_ui
    Mrboto::ComposeBuilder.instance_variable_set(:@_compose_parent_stack, [])
    build_ui
  end
end

Mrboto.register_activity_class(EditorActivity)
```
