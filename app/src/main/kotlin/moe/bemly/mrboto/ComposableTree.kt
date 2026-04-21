package moe.bemly.mrboto

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ComposableTree"

/**
 * Represents a single node in the Compose UI tree built from Ruby DSL.
 */
data class ComposableNode(
    val type: String,
    val props: Map<String, Any?> = emptyMap(),
    val children: List<ComposableNode> = emptyList(),
    val callbackId: Int = 0,
    val content: String? = null,
)

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

fun parseComposableTreeArray(jsonArray: JSONArray): List<ComposableNode> {
    return (0 until jsonArray.length()).map { i ->
        parseComposableTree(jsonArray.getJSONObject(i))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderComposableNode(
    node: ComposableNode,
    mruby: MRuby,
    activity: MrbotoActivityBase,
) {
    val mod = buildModifier(node.props)

    when (node.type) {
        "column" -> {
            val vertArr = parseVerticalArrangement(node.props["vertical_arrangement"])
            val horzAlign = parseHorizontalAlignment(node.props["horizontal_alignment"])
            Column(
                modifier = mod,
                verticalArrangement = vertArr,
                horizontalAlignment = horzAlign,
            ) {
                node.children.forEach { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "row" -> {
            val horzArr = parseHorizontalArrangement(node.props["horizontal_arrangement"])
            val vertAlign = parseVerticalAlignment(node.props["vertical_alignment"])
            Row(
                modifier = mod,
                horizontalArrangement = horzArr,
                verticalAlignment = vertAlign,
            ) {
                node.children.forEach { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "box" -> {
            val contentAlign = parseContentAlignment(node.props["content_alignment"])
            Box(
                modifier = mod,
                contentAlignment = contentAlign,
            ) {
                node.children.forEach { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "spacer" -> Spacer(modifier = mod)

        "vertical_scroll" -> {
            val scrollState = rememberScrollState()
            Column(modifier = mod.verticalScroll(scrollState)) {
                node.children.forEach { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "horizontal_scroll" -> {
            val scrollState = rememberScrollState()
            Row(modifier = mod.horizontalScroll(scrollState)) {
                node.children.forEach { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "lazy_column" -> {
            LazyColumn(modifier = mod) {
                items(node.children) { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "lazy_row" -> {
            LazyRow(modifier = mod) {
                items(node.children) { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "text" -> {
            Text(
                text = node.content ?: "",
                modifier = mod,
                color = parseColor(node.props["color"]),
                fontSize = parseTextSize(node.props["font_size"]),
                textAlign = parseTextAlign(node.props["text_align"]),
                fontFamily = parseFontFamily(node.props["font_family"]),
            )
        }

        "button" -> {
            Button(
                onClick = {
                    if (node.callbackId > 0) {
                        mruby.eval("Mrboto.dispatch_callback(${node.callbackId})")
                    }
                },
                modifier = mod,
            ) { Text(text = node.content ?: "") }
        }

        "text_button" -> {
            TextButton(
                onClick = {
                    if (node.callbackId > 0) {
                        mruby.eval("Mrboto.dispatch_callback(${node.callbackId})")
                    }
                },
                modifier = mod,
            ) { Text(text = node.content ?: "") }
        }

        "floating_action_button" -> {
            FloatingActionButton(
                onClick = {
                    if (node.callbackId > 0) {
                        mruby.eval("Mrboto.dispatch_callback(${node.callbackId})")
                    }
                },
                modifier = mod,
            ) {
                node.children.forEach { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "icon_button" -> {
            val iconName = node.props["icon"]?.toString() ?: "info"
            IconButton(
                onClick = {
                    if (node.callbackId > 0) {
                        mruby.eval("Mrboto.dispatch_callback(${node.callbackId})")
                    }
                },
                modifier = mod,
            ) {
                Icon(imageVector = materialIcon(iconName), contentDescription = null)
            }
        }

        "text_field" -> {
            var value by remember { mutableStateOf(node.content ?: "") }
            val cbId = node.callbackId
            TextField(
                value = value,
                onValueChange = { newValue ->
                    value = newValue
                    if (cbId > 0) {
                        val safe = newValue.replace("\\", "\\\\").replace("'", "\\'")
                        mruby.eval("Mrboto.dispatch_callback($cbId, '$safe')")
                    }
                },
                modifier = mod,
                singleLine = node.props["single_line"] == true,
            )
        }

        "outlined_text_field" -> {
            var value by remember { mutableStateOf(node.content ?: "") }
            val cbId = node.callbackId
            val hint = node.props["hint"]?.toString()
            val singleLine = node.props["single_line"] == true
            val maxLines = (node.props["max_lines"] as? Number)?.toInt() ?: if (singleLine) 1 else Int.MAX_VALUE
            OutlinedTextField(
                value = value,
                onValueChange = { newValue ->
                    value = newValue
                    if (cbId > 0) {
                        val safe = newValue.replace("\\", "\\\\").replace("'", "\\'")
                        mruby.eval("Mrboto.dispatch_callback($cbId, '$safe')")
                    }
                },
                modifier = mod,
                placeholder = if (hint != null) { { Text(hint) } } else null,
                singleLine = singleLine,
                maxLines = maxLines,
            )
        }

        "switch" -> {
            var checked by remember { mutableStateOf(node.props["checked"] == true) }
            val cbId = node.callbackId
            Switch(
                checked = checked,
                onCheckedChange = { newValue ->
                    checked = newValue
                    if (cbId > 0) {
                        mruby.eval("Mrboto.dispatch_callback($cbId, $newValue)")
                    }
                },
                modifier = mod,
            )
        }

        "checkbox" -> {
            var checked by remember { mutableStateOf(node.props["checked"] == true) }
            val cbId = node.callbackId
            Checkbox(
                checked = checked,
                onCheckedChange = { newValue ->
                    checked = newValue
                    if (cbId > 0) {
                        mruby.eval("Mrboto.dispatch_callback($cbId, $newValue)")
                    }
                },
                modifier = mod,
            )
        }

        "slider" -> {
            var value by remember { mutableStateOf((node.props["value"] as? Number)?.toFloat() ?: 0f) }
            val cbId = node.callbackId
            Slider(
                value = value,
                onValueChange = { newValue ->
                    value = newValue
                    if (cbId > 0) {
                        mruby.eval("Mrboto.dispatch_callback($cbId, $newValue)")
                    }
                },
                modifier = mod,
                valueRange = parseRange(node.props["value_range"]),
            )
        }

        "card" -> {
            Card(modifier = mod) {
                node.children.forEach { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "divider" -> HorizontalDivider(modifier = mod)

        "scaffold" -> {
            val topBarNode = node.props["top_bar"]
            val bottomBarNode = node.props["bottom_bar"]
            val fabNode = node.props["floating_action_button"]
            android.util.Log.d(TAG, "Scaffold: topBar=${topBarNode != null}, bottomBar=${bottomBarNode != null}, fab=${fabNode != null}, children=${node.children.size}")
            if (topBarNode is JSONObject) android.util.Log.d(TAG, "  topBar type=${topBarNode.optString("type")}")
            if (bottomBarNode is JSONObject) android.util.Log.d(TAG, "  bottomBar type=${bottomBarNode.optString("type")}")

            // Determine bottom bar content children and glass props
            val bottomBarContentNodes = parseBottomBarContent(bottomBarNode as? JSONObject)
            val bottomBarGlassProps = findGlassPropsInTree(bottomBarNode as? JSONObject)

            Scaffold(
                topBar = {
                    if (topBarNode is JSONObject) {
                        RenderComposableNode(parseComposableTree(topBarNode), mruby, activity)
                    }
                },
                bottomBar = {
                    if (bottomBarContentNodes.isNotEmpty()) {
                        val blurRadius = (bottomBarGlassProps?.get("blur_radius") as? Number)?.toFloat() ?: 25f
                        val vibrancyEnabled = bottomBarGlassProps?.get("vibrancy") == true
                        val cornerRadius = (bottomBarGlassProps?.get("corner_radius") as? Number)?.toFloat() ?: 16f
                        val shapeType = bottomBarGlassProps?.get("shape_type")?.toString() ?: "rounded_rect"
                        val shape = when (shapeType.lowercase()) {
                            "circle" -> CircleShape
                            else -> RoundedCornerShape(cornerRadius.dp)
                        }

                        // Glass bar container with styled background + optional backdrop blur
                        RenderGlassBar(
                            blurRadius = blurRadius,
                            vibrancyEnabled = vibrancyEnabled,
                            shape = shape,
                            content = {
                                bottomBarContentNodes.forEach { child ->
                                    RenderComposableNode(child, mruby, activity)
                                }
                            },
                        )
                    }
                },
                floatingActionButton = {
                    if (fabNode is JSONObject) {
                        RenderComposableNode(parseComposableTree(fabNode), mruby, activity)
                    }
                },
                modifier = mod,
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
                        val iconName = actionNode.props["icon"]?.toString() ?: "info"
                        val cbId = actionNode.callbackId
                        IconButton(onClick = {
                            if (cbId > 0) mruby.eval("Mrboto.dispatch_callback($cbId)")
                        }) {
                            Icon(imageVector = materialIcon(iconName), contentDescription = null)
                        }
                    }
                },
                modifier = mod,
            )
        }

        "bottom_app_bar" -> {
            BottomAppBar(modifier = mod) {
                node.children.forEach { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            }
        }

        "android_view" -> {
            val viewType = node.props["view_type"]?.toString() ?: ""
            if (viewType.contains("LiquidGlassView")) {
                // LiquidGlassView in content area: use Compose glass effect directly
                RenderLiquidGlassCompose(node, mruby, activity, mod)
            } else if (node.children.isNotEmpty()) {
                // Has Compose children — render them directly
                node.children.forEach { child ->
                    RenderComposableNode(child, mruby, activity)
                }
            } else {
                // Native View only
                AndroidView(
                    modifier = mod,
                    factory = { ctx ->
                        buildAndroidView(node, ctx, activity, mruby)
                    },
                    update = { view ->
                        updateAndroidView(view, node, activity, mruby)
                    },
                )
            }
        }

        "liquid_glass_view" -> {
            // Glass effect handled at scaffold bottomBar level.
            // If used in content area, use Compose-based glass.
            RenderLiquidGlassCompose(node, mruby, activity, mod)
        }

        "image" -> {
            Box(modifier = mod) { Text(text = "[image]") }
        }

        "icon" -> {
            val iconName = node.props["name"]?.toString() ?: "info"
            Icon(
                imageVector = materialIcon(iconName),
                contentDescription = null,
                modifier = mod,
            )
        }

        else -> {
            Box(modifier = mod) { Text(text = "[unknown: ${node.type}]") }
        }
    }
}

@Composable
fun buildModifier(props: Map<String, Any?>): Modifier {
    var m: Modifier = Modifier

    val modifierList = props["modifier"]
    if (modifierList is JSONArray) {
        m = applyModifierArray(modifierList, m)
    }

    props["padding"]?.let { v -> m = m.padding(toDp(v)) }
    props["fill_max_width"]?.let {
        val f = if (it is Number) it.toFloat() else 1f
        if (f > 0) m = m.fillMaxWidth(f)
    }
    props["fill_max_height"]?.let {
        m = if (it is Number) m.fillMaxHeight(it.toFloat()) else m.fillMaxHeight()
    }
    props["width"]?.let { m = m.width(toDp(it)) }
    props["height"]?.let { m = m.height(toDp(it)) }
    props["background_color"]?.let { m = m.background(parseColor(it)) }
    props["aspect_ratio"]?.let {
        m = m.aspectRatio((it as? Number)?.toFloat() ?: 1f)
    }

    return m
}

@Composable
fun applyModifierArray(arr: JSONArray, modifier: Modifier): Modifier {
    var m: Modifier = modifier
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        val type = obj.optString("type", "")
        when (type) {
            "padding" -> m = m.padding(toDp(obj.opt("value") ?: 0))
            "fill_max_width" -> {
                val v = if (obj.has("value")) obj.getDouble("value").toFloat() else 1f
                m = m.fillMaxWidth(v)
            }
            "fill_max_height" -> {
                val v = if (obj.has("value")) obj.getDouble("value").toFloat() else 1f
                m = m.fillMaxHeight(v)
            }
            "width" -> m = m.width(toDp(obj.get("value")))
            "height" -> m = m.height(toDp(obj.get("value")))
            "background" -> m = m.background(parseColor(obj.get("value")))
            "aspect_ratio" -> m = m.aspectRatio(obj.getDouble("value").toFloat())
            "clip" -> m = m.clip(MaterialTheme.shapes.medium)
        }
    }
    return m
}

@SuppressLint("DiscouragedApi")
private fun buildAndroidView(
    node: ComposableNode,
    context: android.content.Context,
    activity: MrbotoActivityBase,
    mruby: MRuby,
): android.view.View {
    val viewType = node.props["view_type"]?.toString() ?: "android.widget.LinearLayout"
    val registryId = (activity as? MrbotoComposeActivityBase)?.composeRegistryId
        ?: return android.widget.LinearLayout(context).apply { orientation = android.widget.LinearLayout.VERTICAL }
    val propsJson = buildPropsJson(node.props)
    val result = mruby.eval(
        "Mrboto._create_view($registryId, '$viewType', $propsJson)"
    )
    val viewId = result.toIntOrNull() ?: 0
    if (viewId > 0) {
        val view = mruby.lookupJavaObject<android.view.View>(viewId)
        if (view != null) return view
    }
    return android.widget.LinearLayout(context).apply { orientation = android.widget.LinearLayout.VERTICAL }
}

private fun buildPropsJson(props: Map<String, Any?>): String {
    val sb = StringBuilder("{")
    var first = true
    props.forEach { (k, v) ->
        if (!first) sb.append(",")
        first = false
        sb.append("\"$k\":").append(jsonValue(v))
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
    // Children already built in factory phase
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
                if (hex.length <= 6) Color(0xFF000000L or c) else Color(c)
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

fun parseHorizontalAlignment(value: Any?): Alignment.Horizontal {
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

fun parseHorizontalArrangement(value: Any?): Arrangement.Horizontal {
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
        "arrow_back" -> Icons.AutoMirrored.Filled.ArrowBack
        "arrow_forward" -> Icons.AutoMirrored.Filled.ArrowForward
        "refresh" -> Icons.Default.Refresh
        "play_arrow" -> Icons.Default.PlayArrow
        "save" -> Icons.Default.Save
        "content_copy" -> Icons.Default.ContentCopy
        "content_paste" -> Icons.Default.ContentPaste
        else -> Icons.Default.Info
    }
}

/**
 * Parse bottom bar node and extract the actual content children,
 * skipping any liquid_glass_view or android_view wrapper.
 */
private fun parseBottomBarContent(bottomBarNode: JSONObject?): List<ComposableNode> {
    if (bottomBarNode == null) return emptyList()
    val parsed = parseComposableTree(bottomBarNode)
    return unwrapGlass(parsed)
}

/**
 * Unwrap liquid_glass_view / android_view(LiquidGlassView) wrappers,
 * returning only the actual content children.
 */
private fun unwrapGlass(node: ComposableNode): List<ComposableNode> {
    val isGlass = node.type == "liquid_glass_view" ||
        (node.type == "android_view" &&
            node.props["view_type"]?.toString()?.contains("LiquidGlassView") == true)
    return if (isGlass) {
        node.children.flatMap { unwrapGlass(it) }
    } else {
        listOf(node)
    }
}

/**
 * Walk a bottom bar node tree looking for liquid_glass_view props.
 * Returns the props map if found, null otherwise.
 */
private fun findGlassPropsInTree(bottomBarNode: JSONObject?): Map<String, Any?>? {
    if (bottomBarNode == null) return null
    val parsed = parseComposableTree(bottomBarNode)
    return findGlassPropsInNode(parsed)
}

private fun findGlassPropsInNode(node: ComposableNode): Map<String, Any?>? {
    if (node.type == "liquid_glass_view") return node.props
    if (node.type == "android_view" &&
        node.props["view_type"]?.toString()?.contains("LiquidGlassView") == true
    ) {
        return node.props
    }
    for (child in node.children) {
        val result = findGlassPropsInNode(child)
        if (result != null) return result
    }
    return null
}

/**
 * Render a styled glass bar for Scaffold bottomBar.
 * Uses a semi-transparent colored background with rounded corners
 * to create a frosted glass appearance.
 */
@Composable
private fun RenderGlassBar(
    blurRadius: Float,
    vibrancyEnabled: Boolean,
    shape: Shape,
    content: @Composable () -> Unit,
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val barColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(barColor.copy(alpha = 0.85f))
    ) {
        content()
    }
}

/**
 * Render a liquid glass effect using Compose + kyant.backdrop directly,
 * without native View wrapping. Used when liquid_glass_view appears in
 * content area (not scaffold bottomBar).
 */
@Composable
fun RenderLiquidGlassCompose(
    node: ComposableNode,
    mruby: MRuby,
    activity: MrbotoActivityBase,
    modifier: Modifier = Modifier,
) {
    val backdrop = rememberLayerBackdrop()
    val blurRadius = (node.props["blur_radius"] as? Number)?.toFloat() ?: 25f
    val vibrancyEnabled = node.props["vibrancy"] == true
    val cornerRadius = (node.props["corner_radius"] as? Number)?.toFloat() ?: 16f
    val shapeType = node.props["shape_type"]?.toString() ?: "rounded_rect"

    val shape = when (shapeType.lowercase()) {
        "circle" -> CircleShape
        else -> RoundedCornerShape(cornerRadius.dp)
    }

    Box(modifier = modifier.clip(shape)) {
        // Capture children content into backdrop
        Box(
            modifier = Modifier.layerBackdrop(backdrop)
        ) {
            node.children.forEach { child ->
                RenderComposableNode(child, mruby, activity)
            }
        }

        // Draw glass overlay
        Box(
            modifier = Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        if (blurRadius > 0f) blur(blurRadius)
                        if (vibrancyEnabled) vibrancy()
                    },
                    shadow = { Shadow(alpha = 0.1f) }
                )
                .fillMaxSize()
        )
    }
}
