package moe.bemly.mrboto

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Adapter
import android.widget.AdapterView
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.RatingBar
import android.widget.SeekBar
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

/**
 * View event listeners that delegate to mruby callback procs.
 *
 * Each listener holds a callback registry ID. When the event fires,
 * it calls back into mruby via Mrboto.dispatch_callback(id, ...).
 */

/**
 * OnClickListener that delegates to mruby.
 * The mruby callback is a proc/block identified by registry ID.
 */
class MrbotoClickListener(
    private val activity: MrbotoActivityBase,
    private val callbackId: Int
) : View.OnClickListener {
    override fun onClick(v: View?) {
        v ?: return
        val viewId = activity.mruby.registerJavaObject(v)
        activity.mruby.eval("Mrboto.dispatch_callback($callbackId, $viewId)")
    }
}

/**
 * TextWatcher that delegates to mruby.
 * Calls back when text changes.
 */
class MrbotoTextWatcher(
    private val activity: MrbotoActivityBase,
    private val onChangeCallbackId: Int
) : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        val text = s?.toString() ?: ""
        val escaped = text.replace("\\", "\\\\").replace("\"", "\\\"")
        activity.mruby.eval("Mrboto.dispatch_text_changed($onChangeCallbackId, \"$escaped\")")
    }

    override fun afterTextChanged(s: Editable?) {}
}

/**
 * OnCheckedChangeListener for CheckBox, Switch, etc.
 */
class MrbotoCheckChangeListener(
    private val activity: MrbotoActivityBase,
    private val callbackId: Int
) : CompoundButton.OnCheckedChangeListener {
    override fun onCheckedChanged(button: CompoundButton, isChecked: Boolean) {
        activity.mruby.eval("Mrboto.dispatch_checked($callbackId, $isChecked)")
    }
}

/**
 * OnTouchListener that delegates to mruby.
 * Dispatches Mrboto.dispatch_touch(callbackId, viewId, action, rawX, rawY).
 */
class MrbotoTouchListener(
    private val activity: MrbotoActivityBase,
    private val callbackId: Int
) : View.OnTouchListener {
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        v ?: return false
        event ?: return false
        val viewId = activity.mruby.registerJavaObject(v)
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()
        val result = activity.mruby.eval(
            "Mrboto.dispatch_touch($callbackId, $viewId, ${event.action}, $x, $y)"
        )
        return result == "true"
    }
}

/**
 * OnLongClickListener that delegates to mruby.
 */
class MrbotoLongClickListener(
    private val activity: MrbotoActivityBase,
    private val callbackId: Int
) : View.OnLongClickListener {
    override fun onLongClick(v: View?): Boolean {
        v ?: return false
        val viewId = activity.mruby.registerJavaObject(v)
        val result = activity.mruby.eval("Mrboto.dispatch_callback($callbackId, $viewId)")
        return result == "true"
    }
}

/**
 * OnScrollChangeListener that delegates to mruby.
 */
class MrbotoScrollChangeListener(
    private val activity: MrbotoActivityBase,
    private val callbackId: Int
) : View.OnScrollChangeListener {
    override fun onScrollChange(v: View?, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
        v ?: return
        val viewId = activity.mruby.registerJavaObject(v)
        activity.mruby.eval("Mrboto.dispatch_callback($callbackId, $viewId, $scrollX, $scrollY, $oldScrollX, $oldScrollY)")
    }
}

/**
 * OnFocusChangeListener that delegates to mruby.
 */
class MrbotoFocusChangeListener(
    private val activity: MrbotoActivityBase,
    private val callbackId: Int
) : View.OnFocusChangeListener {
    override fun onFocusChange(v: View?, hasFocus: Boolean) {
        v ?: return
        val viewId = activity.mruby.registerJavaObject(v)
        activity.mruby.eval("Mrboto.dispatch_callback($callbackId, $viewId, $hasFocus)")
    }
}

/**
 * OnKeyListener that delegates to mruby.
 */
class MrbotoKeyListener(
    private val activity: MrbotoActivityBase,
    private val callbackId: Int
) : View.OnKeyListener {
    override fun onKey(v: View?, keyCode: Int, event: android.view.KeyEvent?): Boolean {
        v ?: return false
        val viewId = activity.mruby.registerJavaObject(v)
        val action = event?.action ?: -1
        val result = activity.mruby.eval("Mrboto.dispatch_callback($callbackId, $viewId, $keyCode, $action)")
        return result == "true"
    }
}

/**
 * OnDragListener that delegates to mruby.
 */
class MrbotoDragListener(
    private val activity: MrbotoActivityBase,
    private val callbackId: Int
) : View.OnDragListener {
    override fun onDrag(v: View?, event: DragEvent?): Boolean {
        v ?: return false
        event ?: return false
        val viewId = activity.mruby.registerJavaObject(v)
        val eventType = event.action
        val x = event.x.toInt()
        val y = event.y.toInt()
        val result = activity.mruby.eval("Mrboto.dispatch_callback($callbackId, $viewId, $eventType, $x, $y)")
        return result == "true"
    }
}

/**
 * OnItemClickListener that delegates to mruby.
 */
class MrbotoItemClickListener(
    private val activity: MrbotoActivityBase,
    private val callbackId: Int
) : AdapterView.OnItemClickListener {
    override fun onItemClick(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
        parent ?: return
        val parentId = activity.mruby.registerJavaObject(parent)
        val viewId = if (v != null) activity.mruby.registerJavaObject(v) else 0
        activity.mruby.eval("Mrboto.dispatch_callback($callbackId, $parentId, $position, $viewId)")
    }
}

/**
 * OnItemLongClickListener that delegates to mruby.
 */
class MrbotoItemLongClickListener(
    private val activity: MrbotoActivityBase,
    private val callbackId: Int
) : AdapterView.OnItemLongClickListener {
    override fun onItemLongClick(parent: AdapterView<*>?, v: View?, position: Int, id: Long): Boolean {
        parent ?: return false
        val parentId = activity.mruby.registerJavaObject(parent)
        val viewId = if (v != null) activity.mruby.registerJavaObject(v) else 0
        val result = activity.mruby.eval("Mrboto.dispatch_callback($callbackId, $parentId, $position, $viewId)")
        return result == "true"
    }
}

/**
 * ViewPager2 page change callback that delegates to mruby.
 */
class MrbotoPageChangeCallback(
    private val activity: MrbotoActivityBase,
    private val callbackId: Int
) : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
    override fun onPageSelected(position: Int) {
        activity.mruby.eval("Mrboto.dispatch_callback($callbackId, $position)")
    }
}

/**
 * SeekBar change listener that delegates to mruby.
 */
class MrbotoSeekBarChangeListener(
    private val activity: MrbotoActivityBase,
    private val callbackId: Int
) : SeekBar.OnSeekBarChangeListener {
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        seekBar ?: return
        val viewId = activity.mruby.registerJavaObject(seekBar)
        activity.mruby.eval("Mrboto.dispatch_callback($callbackId, $viewId, $progress, $fromUser)")
    }
    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
}

/**
 * RatingBar change listener that delegates to mruby.
 */
class MrbotoRatingBarChangeListener(
    private val activity: MrbotoActivityBase,
    private val callbackId: Int
) : RatingBar.OnRatingBarChangeListener {
    override fun onRatingChanged(ratingBar: RatingBar?, rating: Float, fromUser: Boolean) {
        ratingBar ?: return
        val viewId = activity.mruby.registerJavaObject(ratingBar)
        activity.mruby.eval("Mrboto.dispatch_callback($callbackId, $viewId, $rating, $fromUser)")
    }
}

/**
 * NavigationView item selected listener that delegates to mruby.
 */
class MrbotoNavigationViewListener(
    private val activity: MrbotoActivityBase,
    private val callbackId: Int
) : com.google.android.material.navigation.NavigationView.OnNavigationItemSelectedListener {
    override fun onNavigationItemSelected(item: android.view.MenuItem): Boolean {
        val itemId = item.itemId
        val title = (item.title?.toString() ?: "").replace("'", "\\'").replace("\\", "\\\\")
        val result = activity.mruby.eval("Mrboto.dispatch_callback($callbackId, $itemId, '$title')")
        return result == "true"
    }
}

/**
 * Simple RecyclerView.Adapter for ViewPager2 that wraps a list of pre-built Views.
 * Each view is created from a registry ID and managed by the adapter lifecycle.
 */
class ViewPagerAdapter(
    private val activity: MrbotoActivityBase,
    private val viewRegistryIds: List<Int>
) : RecyclerView.Adapter<ViewPagerAdapter.ViewHolder>() {

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun getItemCount(): Int = viewRegistryIds.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val container = FrameLayout(parent.context)
        container.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        Log.i("ViewPagerAdapter", "onCreateViewHolder: container=$container")
        return ViewHolder(container)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val registryId = viewRegistryIds[position]
        val createdView = activity.mruby.lookupJavaObject<View>(registryId)
        Log.i("ViewPagerAdapter", "onBindViewHolder: pos=$position registryId=$registryId " +
            "view=$createdView class=${createdView?.javaClass?.simpleName}")
        if (createdView != null) {
            val parent = holder.itemView as? ViewGroup
            Log.i("ViewPagerAdapter", "  parent=$parent parentType=${parent?.javaClass?.simpleName}")
            parent?.removeAllViews()
            parent?.addView(createdView)
            Log.i("ViewPagerAdapter", "  addView done, childCount=${parent?.childCount}")
        } else {
            Log.e("ViewPagerAdapter", "  createdView is NULL for registryId=$registryId")
        }
    }
}
