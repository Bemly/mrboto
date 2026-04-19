package moe.bemly.mrboto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrop
import com.kyant.backdrop.rememberCanvasBackdrop

/**
 * LiquidGlassView — a FrameLayout that renders a liquid glass effect
 * over its child views using Jetpack Compose + AndroidLiquidGlass library.
 *
 * Child views added via addView() are redirected to an internal contentFrame.
 * The ComposeView captures contentFrame as a bitmap and uses it as a
 * CanvasBackdrop to render glass effects (blur, vibrancy, lens, opacity).
 *
 * Usage from Ruby:
 *   liquid_glass_view(blur: dp(4), vibrancy: true) do
 *     text_view(text: "Hello Glass")
 *   end
 */
class LiquidGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val contentFrame = FrameLayout(context)
    private val composeView = ComposeView(context)

    private var _blurRadius by mutableStateOf(8f)
    private var _vibrancy by mutableStateOf(false)
    private var _lensHeight by mutableStateOf(0f)
    private var _lensAmount by mutableStateOf(0f)
    private var _opacity by mutableStateOf(1f)
    private var _shapeType by mutableStateOf("rounded_rect")
    private var _cornerRadius by mutableStateOf(16f)
    private var _contentBitmap by mutableStateOf<Bitmap?>(null)

    init {
        // Only composeView is in the actual View tree.
        // contentFrame is held as a reference and embedded via AndroidView.
        super.addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        composeView.setContent { LiquidGlassContent() }
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (child === composeView) {
            super.addView(child, index, params)
        } else {
            contentFrame.addView(child, index, params)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed) {
            contentFrame.layout(0, 0, r - l, b - t)
            captureContentBitmap()
        }
    }

    private fun captureContentBitmap() {
        val w = contentFrame.width
        val h = contentFrame.height
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        contentFrame.draw(canvas)
        _contentBitmap = bmp
    }

    /** Force a bitmap recapture and recompose. Called by setters. */
    private fun updateEffect() {
        captureContentBitmap()
    }

    @Composable
    private fun LiquidGlassContent() {
        val bitmap = _contentBitmap
        val shape = when (_shapeType) {
            "circle" -> CircleShape
            "none" -> RoundedCornerShape(0.dp)
            else -> RoundedCornerShape(_cornerRadius.dp)
        }

        if (bitmap != null) {
            val backdrop = rememberCanvasBackdrop(onDraw = {
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawBitmap(bitmap, 0f, 0f, null)
                }
            })

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .backdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            if (_blurRadius > 0f) blur(_blurRadius)
                            if (_vibrancy) vibrancy()
                            if (_lensHeight > 0f && _lensAmount > 0f) {
                                lens(_lensHeight, _lensAmount)
                            }
                            if (_opacity < 1f) opacity(_opacity)
                        }
                    )
            )
        }
    }

    // ── Setters callable from Ruby via call_java_method (reflection) ──

    fun setBlurRadius(radius: Float) {
        _blurRadius = radius
        updateEffect()
    }

    fun setVibrancy(enabled: Boolean) {
        _vibrancy = enabled
        updateEffect()
    }

    fun setLens(height: Float, amount: Float) {
        _lensHeight = height
        _lensAmount = amount
        updateEffect()
    }

    fun setOpacity(alpha: Float) {
        _opacity = alpha
        updateEffect()
    }

    fun setShapeType(type: String) {
        _shapeType = type
        updateEffect()
    }

    fun setCornerRadius(radius: Float) {
        _cornerRadius = radius
        updateEffect()
    }
}
