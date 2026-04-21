package moe.bemly.mrboto

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import org.json.JSONObject

/**
 * Base class for Compose-backed Ruby Activities.
 *
 * Extends MrbotoActivityBase to inherit all lifecycle, file, network,
 * and other APIs. Uses `setContent { ... }` to render Compose UI
 * driven by a JSON tree built from the Ruby DSL.
 *
 * Usage in Ruby:
 *   class MyActivity < Mrboto::ComposeActivity
 *     def on_create(bundle)
 *       super
 *       column {
 *         text("Hello Compose!")
 *         button("Click") { toast("Clicked!") }
 *       }
 *       set_compose_content
 *     end
 *   end
 */
abstract class MrbotoComposeActivityBase : MrbotoActivityBase() {

    /** Mutable state holding the Compose UI tree. Updating this triggers recomposition. */
    private var composeTreeState by mutableStateOf<ComposableNode?>(null)

    /** Expose the registry ID for ComposableTree's AndroidView factory. */
    val composeRegistryId: Int
        get() = rubyInstanceId

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up Compose content that reacts to tree state changes
        setContent {
            val tree = composeTreeState
            if (tree != null) {
                RenderComposableNode(tree, mruby, this)
            }
        }
    }

    /**
     * Called from Ruby DSL to set the Compose UI tree.
     * The Ruby side builds a tree structure and serializes it to JSON.
     * This method parses the JSON and updates the MutableState,
     * which triggers Compose recomposition.
     *
     * @param jsonStr JSON string representing the ComposableNode tree
     */
    fun setComposeContent(jsonStr: CharSequence) {
        try {
            val json = JSONObject(jsonStr.toString())
            composeTreeState = parseComposableTree(json)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "setComposeContent failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "MrbotoComposeActivity"
    }
}
