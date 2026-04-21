package moe.bemly.mrboto

import android.annotation.SuppressLint
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a single node in the Compose UI tree built from Ruby DSL.
 *
 * Structure:
 *   { "type": "column", "props": { "modifier": [...] }, "children": [...], "callback_id": 123 }
 */
data class ComposableNode(
    val type: String,
    val props: Map<String, Any?> = emptyMap(),
    val children: List<ComposableNode> = emptyList(),
    val callbackId: Int = 0,
    val content: String? = null,
)

/**
 * Parse a JSONObject into a ComposableNode tree.
 */
fun parseComposableTree(json: JSONObject): ComposableNode {
    val type = json.optString("type", "box")
    val props = mutableMapOf<String, Any?>()
    val propsObj = json.optJSONObject("props")
    if (propsObj != null) {
        propsObj.keys().forEach { key ->
            props[key] = propsObj.get(key)
        }
    }
    val children = mutableListOf<ComposableNode>()
    val childrenArr = json.optJSONArray("children")
    if (childrenArr != null) {
        for (i in 0 until childrenArr.length()) {
            children.add(parseComposableTree(childrenArr.getJSONObject(i)))
        }
    }
    val cbId = json.optInt("callback_id", 0)
    val content = if (json.has("content")) json.getString("content") else null
    return ComposableNode(type, props, children, cbId, content)
}

/**
 * Parse a JSON array of nodes (for lazy list items).
 */
fun parseComposableTreeArray(jsonArray: JSONArray): List<ComposableNode> {
    return (0 until jsonArray.length()).map { i ->
        parseComposableTree(jsonArray.getJSONObject(i))
    }
}

/**
 * Render a ComposableNode tree into actual Compose UI.
 * This is a @Composable function called from setContent.
 */
@Composable
fun RenderComposableNode(
    node: ComposableNode,
    mruby: MRuby,
    activity: MrbotoActivityBase,
) {
    val modifier = buildModifier(node.props, activity)

    when (node.type) {
        // ── Layouts ──
        "column" -> {
            val verticalArrangement = parseArrangement(node.props["vertical_arrangement"])
            val horizontalAlignment = parseAlignment(node.props["horizontal_alignment"])
            Column(
                modifier = modifier,
                verticalArrangement = verticalArrangement,
                horizontalAlignment = horizontalAlignment,
            ) {
                node.children.forEach { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "row" -> {
            val horizontalArrangement = parseArrangement(node.props["horizontal_arrangement"])
            val verticalAlignment = parseAlignment(node.props["vertical_alignment"])
            Row(
                modifier = modifier,
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = verticalAlignment,
            ) {
                node.children.forEach { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "box" -> {
            val contentAlignment = parseAlignment(node.props["content_alignment"])
            Box(
                modifier = modifier,
                contentAlignment = contentAlignment,
            ) {
                node.children.forEach { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "spacer" -> Spacer(modifier = modifier)

        // ── Scrolling ──
        "vertical_scroll" -> {
            val scrollState = rememberScrollState()
            Column(modifier = modifier.verticalScroll(scrollState)) {
                node.children.forEach { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "horizontal_scroll" -> {
            val scrollState = rememberScrollState()
            Row(modifier = modifier.horizontalScroll(scrollState)) {
                node.children.forEach { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "lazy_column" -> {
            LazyColumn(modifier = modifier) {
                items(node.children) { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "lazy_row" -> {
            LazyRow(modifier = modifier) {
                items(node.children) { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        // ── Text ──
        "text" -> {
            val color = parseColor(node.props["color"])
            val fontSize = parseTextSize(node.props["font_size"])
            val textAlign = parseTextAlign(node.props["text_align"])
            val fontFamily = parseFontFamily(node.props["font_family"])
            Text(
                text = node.content ?: "",
                modifier = modifier,
                color = color,
                fontSize = fontSize,
                textAlign = textAlign,
                fontFamily = fontFamily,
            )
        }

        // ── Buttons ──
        "button" -> {
            val onClick = {
                if (node.callbackId > 0) {
                    mruby.eval("Mrboto.dispatch_callback(${node.callbackId})")
                }
            }
            Button(onClick = onClick, modifier = modifier) {
                Text(text = node.content ?: "")
            }
        }

        "text_button" -> {
            val onClick = {
                if (node.callbackId > 0) {
                    mruby.eval("Mrboto.dispatch_callback(${node.callbackId})")
                }
            }
            TextButton(onClick = onClick, modifier = modifier) {
                Text(text = node.content ?: "")
            }
        }

        "floating_action_button" -> {
            val onClick = {
                if (node.callbackId > 0) {
                    mruby.eval("Mrboto.dispatch_callback(${node.callbackId})")
                }
            }
            FloatingActionButton(onClick = onClick, modifier = modifier) {
                node.children.forEach { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "icon_button" -> {
            val onClick = {
                if (node.callbackId > 0) {
                    mruby.eval("Mrboto.dispatch_callback(${node.callbackId})")
                }
            }
            val iconName = parseIconName(node.props["icon"])
            IconButton(onClick = onClick, modifier = modifier) {
                Icon(
                    imageVector = materialIcon(iconName),
                    contentDescription = null,
                )
            }
        }

        // ── Input: TextField ──
        "text_field" -> {
            var value by remember { mutableStateOf(node.content ?: "") }
            val callbackId = node.callbackId
            TextField(
                value = value,
                onValueChange = { newValue ->
                    value = newValue
                    if (callbackId > 0) {
                        val safe = newValue.replace("\\", "\\\\").replace("'", "\\'")
                        mruby.eval("Mrboto.dispatch_callback($callbackId, '$safe')")
                    }
                },
                modifier = modifier,
                singleLine = node.props["single_line"] == true,
            )
        }

        "outlined_text_field" -> {
            var value by remember { mutableStateOf(node.content ?: "") }
            val callbackId = node.callbackId
            val hint = node.props["hint"]?.toString()
            val singleLine = node.props["single_line"] == true
            val maxLines = (node.props["max_lines"] as? Number)?.toInt() ?: if (singleLine) 1 else Int.MAX_VALUE
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    value = newValue
                    if (callbackId > 0) {
                        val safe = newValue.replace("\\", "\\\\").replace("'", "\\'")
                        mruby.eval("Mrboto.dispatch_callback($callbackId, '$safe')")
                    }
                },
                modifier = modifier,
                placeholder = if (hint != null) { { Text(hint) } } else null,
                singleLine = singleLine,
                maxLines = maxLines,
            )
        }

        // ── Controls ──
        "switch" -> {
            var checked by remember { mutableStateOf(node.props["checked"] == true) }
            val callbackId = node.callbackId
            Switch(
                checked = checked,
                onCheckedChange = { newValue ->
                    checked = newValue
                    if (callbackId > 0) {
                        mruby.eval("Mrboto.dispatch_callback($callbackId, $newValue)")
                    }
                },
                modifier = modifier,
            )
        }

        "checkbox" -> {
            var checked by remember { mutableStateOf(node.props["checked"] == true) }
            val callbackId = node.callbackId
            Checkbox(
                checked = checked,
                onCheckedChange = { newValue ->
                    checked = newValue
                    if (callbackId > 0) {
                        mruby.eval("Mrboto.dispatch_callback($callbackId, $newValue)")
                    }
                },
                modifier = modifier,
            )
        }

        "slider" -> {
            var value by remember { mutableStateOf((node.props["value"] as? Number)?.toFloat() ?: 0f) }
            val callbackId = node.callbackId
            val valueRange = parseRange(node.props["value_range"])
            Slider(
                value = value,
                onValueChange = { newValue ->
                    value = newValue
                    if (callbackId > 0) {
                        mruby.eval("Mrboto.dispatch_callback($callbackId, $newValue)")
                    }
                },
                modifier = modifier,
                valueRange = valueRange,
            )
        }

        // ── Material3 ──
        "card" -> {
            Card(modifier = modifier) {
                node.children.forEach { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "divider" -> Divider(modifier = modifier)

        "scaffold" -> {
            val topBarNode = node.props["top_bar"]
            val bottomBarNode = node.props["bottom_bar"]
            val fabNode = node.props["floating_action_button"]
            Scaffold(
                topBar = {
                    if (topBarNode is JSONObject) {
                        RenderComposableNode(parseComposableTree(topBarNode), mruby, activity)
                    }
                },
                bottomBar = {
                    if (bottomBarNode is JSONObject) {
                        RenderComposableNode(parseComposableTree(bottomBarNode), mruby, activity)
                    }
                },
                floatingActionButton = {
                    if (fabNode is JSONObject) {
                        RenderComposableNode(parseComposableTree(fabNode), mruby, activity)
                    }
                },
                modifier = modifier,
            ) { padding ->
                val contentNode = node.children.firstOrNull()
                if (contentNode != null) {
                    Box(modifier = Modifier.padding(padding)) {
                        RenderComposableNode(contentNode, mruby, activity)
                    }
                }
            }
        }

        "top_app_bar" -> {
            val title = node.content ?: node.props["title"]?.toString() ?: ""
            val actionsNode = node.props["actions"]
            val actionsList = if (actionsNode is JSONArray) {
                parseComposableTreeArray(actionsNode)
            } else emptyList()

            TopAppBar(
                title = { Text(title) },
                actions = {
                    actionsList.forEach { actionNode ->
                        val icon = parseIconName(actionNode.props["icon"])
                        val cbId = actionNode.callbackId
                        IconButton(onClick = {
                            if (cbId > 0) mruby.eval("Mrboto.dispatch_callback($cbId)")
                        }) {
                            Icon(imageVector = materialIcon(icon), contentDescription = null)
                        }
                    }
                },
                modifier = modifier,
            )
        }

        "bottom_app_bar" -> {
            BottomAppBar(modifier = modifier) {
                node.children.forEach { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        // ── AndroidView (embed native View) ──
        "android_view" -> {
            AndroidView(
                modifier = modifier,
                factory = { context ->
                    // For LiquidGlassView and other native views,
                    // we build them via the existing View creation mechanism
                    buildAndroidView(node, context, activity, mruby)
                },
                update = { view ->
                    updateAndroidView(view, node, activity, mruby)
                },
            )
        }

        // ── Image / Icon ──
        "image" -> {
            // Placeholder for now
            Box(modifier = modifier) {
                Text(text = "[image]")
            }
        }

        "icon" -> {
            val iconName = parseIconName(node.props["name"])
            Icon(
                imageVector = materialIcon(iconName),
                contentDescription = null,
                modifier = modifier,
            )
        }

        else -> {
            // Fallback: render as Box
            Box(modifier = modifier) {
                Text(text = "[unknown: ${node.type}]")
            }
        }
    }
}

/**
 * Build a Compose Modifier from Ruby DSL props.
 */
@Composable
fun buildModifier(props: Map<String, Any?>, activity: MrbotoActivityBase): Modifier {
    var modifier = Modifier

    // ── modifier list from props ──
    val modifierList = props["modifier"]
    if (modifierList is JSONArray) {
        modifier = applyModifierArray(modifierList, activity, modifier)
    }

    // ── shorthand props ──
    props["padding"]?.let { v ->
        val dpVal = toDp(v)
        modifier = modifier.padding(dpVal)
    }
    props["fill_max_width"]?.let {
        if (it == true || it is Number && it.toFloat() > 0) {
            modifier = modifier.fillMaxWidth(
                if (it is Number) it.toFloat() else 1f
            )
        }
    }
    props["fill_max_height"]?.let {
        if (it is Number) {
            modifier = modifier.fillMaxHeight(it.toFloat())
        } else if (it == true) {
            modifier = modifier.fillMaxHeight()
        }
    }
    props["width"]?.let { modifier = modifier.width(toDp(it)) }
    props["height"]?.let { modifier = modifier.height(toDp(it)) }
    props["weight"]?.let { modifier = modifier.weight((it as? Number)?.toFloat() ?: 1f) }
    props["background_color"]?.let {
        modifier = modifier.background(parseColor(it))
    }
    props["align"]?.let {
        modifier = modifier.align(parseAlignment(it.toString()))
    }
    props["aspect_ratio"]?.let {
        modifier = modifier.aspectRatio((it as? Number)?.toFloat() ?: 1f)
    }

    return modifier
}

/**
 * Apply a chain of modifiers from a JSON array.
 * Each element: { "type": "padding", "value": 16 } or { "type": "fill_max_width" }
 */
@Composable
fun applyModifierArray(arr: JSONArray, activity: MrbotoActivityBase, modifier: Modifier): Modifier {
    var m = modifier
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val type = obj.optString("type", "")
        when (type) {
            "padding" -> {
                val v = toDp(obj.opt("value") ?: 0)
                m = m.padding(v)
            }
            "fill_max_width" -> {
                val v = if (obj.has("value")) obj.getDouble("value").toFloat() else 1f
                m = m.fillMaxWidth(v)
            }
            "fill_max_height" -> {
                val v = if (obj.has("value")) obj.getDouble("value").toFloat() else 1f
                m = m.fillMaxHeight(v)
            }
            "weight" -> m = m.weight(obj.optDouble("value", 1.0).toFloat())
            "width" -> m = m.width(toDp(obj.get("value")))
            "height" -> m = m.height(toDp(obj.get("value")))
            "background" -> m = m.background(parseColor(obj.get("value")))
            "align" -> m = m.align(parseAlignment(obj.getString("value")))
            "aspect_ratio" -> m = m.aspectRatio(obj.getDouble("value").toFloat())
            "clip" -> m = m.clip(MaterialTheme.shapes.medium)
            else -> {}
        }
    }
    return m
}

// ── Helper functions ──

@SuppressLint("DiscouragedApi")
private fun buildAndroidView(
    node: ComposableNode,
    context: android.content.Context,
    activity: MrbotoActivityBase,
    mruby: MRuby,
): android.view.View {
    val viewType = node.props["view_type"]?.toString() ?: "android.widget.LinearLayout"
    val registryId = (activity as? MrbotoComposeActivityBase)?.composeRegistryId
        ?: return android.widget.LinearLayout(context)
    val propsJson = buildPropsJson(node.props)
    val result = mruby.eval(
        "Mrboto._create_view($registryId, '$viewType', $propsJson)"
    )
    val viewId = result.toIntOrNull() ?: 0
    if (viewId > 0) {
        val view = mruby.lookupJavaObject<android.view.View>(viewId)
        if (view != null) return view
    }
    // Fallback: create a simple LinearLayout
    android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
    }
}

private fun buildPropsJson(props: Map<String, Any?>): String {
    val sb = StringBuilder("{")
    var first = true
    props.forEach { (k, v) ->
        if (!first) sb.append(",")
        first = false
        sb.append("\"$k\":")
        sb.append(jsonValue(v))
    }
    sb.append("}")
    return sb.toString()
}

private fun jsonValue(v: Any?): String {
    return when (v) {
        null -> "null"
        is String -> "\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        is Boolean -> v.toString()
        is Number -> v.toString()
        else -> "\"${v.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }
}

private fun updateAndroidView(
    view: android.view.View,
    node: ComposableNode,
    activity: MrbotoActivityBase,
    mruby: MRuby,
) {
    // Rebuild child views if needed — for LiquidGlassView,
    // children are already built in the factory phase
}

fun toDp(value: Any): Dp {
    return when (value) {
        is Number -> value.toFloat().dp
        is String -> value.toFloatOrNull()?.dp ?: 0.dp
        else -> 0.dp
    }
}

fun parseColor(value: Any?): Color {
    return when (value) {
        null -> Color.Unspecified
        is String -> {
            val hex = if (value.startsWith("#")) value.substring(1) else value
            try {
                val c = hex.toLong(16)
                if (hex.length <= 6) {
                    Color(0xFF000000L or c)
                } else {
                    Color(c)
                }
            } catch (_: NumberFormatException) {
                Color.Unspecified
            }
        }
        is Number -> Color(value.toLong())
        else -> Color.Unspecified
    }
}

fun parseTextSize(value: Any?): TextUnit {
    return when (value) {
        is Number -> value.toFloat().sp
        is String -> value.toFloatOrNull()?.sp ?: 14.sp
        else -> 14.sp
    }
}

fun parseAlignment(value: Any?): Alignment.Horizontal {
    return when (value?.toString()?.lowercase()) {
        "center", "center_horizontal" -> Alignment.CenterHorizontally
        "end", "right" -> Alignment.End
        "start", "left" -> Alignment.Start
        else -> Alignment.Start
    }
}

fun parseVerticalAlignment(value: Any?): Alignment.Vertical {
    return when (value?.toString()?.lowercase()) {
        "center", "center_vertical" -> Alignment.CenterVertically
        "bottom" -> Alignment.Bottom
        "top" -> Alignment.Top
        else -> Alignment.Top
    }
}

fun parseContentAlignment(value: Any?): Alignment {
    return when (value?.toString()?.lowercase()) {
        "center" -> Alignment.Center
        "top_start", "top_left" -> Alignment.TopStart
        "top_end", "top_right" -> Alignment.TopEnd
        "bottom_start", "bottom_left" -> Alignment.BottomStart
        "bottom_end", "bottom_right" -> Alignment.BottomEnd
        "center_start", "center_left" -> Alignment.CenterStart
        "center_end", "center_right" -> Alignment.CenterEnd
        else -> Alignment.TopStart
    }
}

fun parseArrangement(value: Any?): Arrangement.Horizontal {
    return when (value?.toString()?.lowercase()) {
        "center", "center_horizontal" -> Arrangement.Center
        "end", "right" -> Arrangement.End
        "space_between" -> Arrangement.SpaceBetween
        "space_around" -> Arrangement.SpaceAround
        "space_evenly" -> Arrangement.SpaceEvenly
        "start", "left" -> Arrangement.Start
        else -> Arrangement.Start
    }
}

fun parseVerticalArrangement(value: Any?): Arrangement.Vertical {
    return when (value?.toString()?.lowercase()) {
        "center", "center_vertical" -> Arrangement.Center
        "bottom" -> Arrangement.Bottom
        "top" -> Arrangement.Top
        "space_between" -> Arrangement.SpaceBetween
        "space_around" -> Arrangement.SpaceAround
        "space_evenly" -> Arrangement.SpaceEvenly
        else -> Arrangement.Top
    }
}

fun parseTextAlign(value: Any?): TextAlign {
    return when (value?.toString()?.lowercase()) {
        "center" -> TextAlign.Center
        "end", "right" -> TextAlign.End
        "start", "left" -> TextAlign.Start
        "justify" -> TextAlign.Justify
        else -> TextAlign.Start
    }
}

fun parseFontFamily(value: Any?): FontFamily {
    return when (value?.toString()?.lowercase()) {
        "monospace" -> FontFamily.Monospace
        "serif" -> FontFamily.Serif
        "sans_serif", "sans-serif" -> FontFamily.SansSerif
        "cursive" -> FontFamily.Cursive
        else -> FontFamily.Default
    }
}

fun parseIconName(value: Any?): String {
    return value?.toString() ?: ""
}

fun parseRange(value: Any?): ClosedFloatingPointRange<Float> {
    return if (value is JSONArray && value.length() == 2) {
        value.getDouble(0).toFloat()..value.getDouble(1).toFloat()
    } else {
        0f..1f
    }
}

fun materialIcon(name: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (name.lowercase()) {
        "light_mode" -> Icons.Default.LightMode
        "dark_mode" -> Icons.Default.DarkMode
        "settings" -> Icons.Default.Settings
        "delete" -> Icons.Default.Delete
        "add" -> Icons.Default.Add
        "close" -> Icons.Default.Close
        "menu" -> Icons.Default.Menu
        "search" -> Icons.Default.Search
        "home" -> Icons.Default.Home
        "arrow_back" -> Icons.Default.ArrowBack
        "arrow_forward" -> Icons.Default.ArrowForward
        "refresh" -> Icons.Default.Refresh
        "play_arrow" -> Icons.Default.PlayArrow
        "save" -> Icons.Default.Save
        "content_copy" -> Icons.Default.ContentCopy
        "content_paste" -> Icons.Default.ContentPaste
        else -> Icons.Default.Info
    }
}
