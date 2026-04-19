package moe.bemly.mrboto

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.FrameLayout
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
