package com.mrboto

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CompoundButton

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
    override fun onCheckedChanged(button: CompoundButton?, isChecked: Boolean) {
        activity.mruby.eval("Mrboto.dispatch_checked($callbackId, $isChecked)")
    }
}
