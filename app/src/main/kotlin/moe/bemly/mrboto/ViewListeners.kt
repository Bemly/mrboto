package moe.bemly.mrboto

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
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
        val view = View(parent.context)
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val registryId = viewRegistryIds[position]
        val createdView = activity.mruby.lookupJavaObject<View>(registryId)
        if (createdView != null) {
            val parent = holder.itemView as? ViewGroup
            parent?.removeAllViews()
            parent?.addView(createdView)
        }
    }
}
