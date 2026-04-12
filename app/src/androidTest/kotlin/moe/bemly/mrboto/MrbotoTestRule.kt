package moe.bemly.mrboto

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit Rule that provides a fresh MRuby instance with all core scripts loaded.
 * Each test gets its own clean VM state.
 */
class MrbotoTestRule : TestRule {

    lateinit var mruby: MRuby
        private set

    val context: Context
        get() = ApplicationProvider.getApplicationContext()

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                mruby = MRuby()
                mruby.registerAndroidClasses()
                loadCoreScripts()
                try {
                    base.evaluate()
                } finally {
                    mruby.close()
                }
            }
        }
    }

    private fun loadCoreScripts() {
        val coreFiles = listOf(
            "mrboto/core.rb",
            "mrboto/layout.rb",
            "mrboto/activity.rb",
            "mrboto/widgets.rb",
            "mrboto/helpers.rb"
        )
        for (file in coreFiles) {
            val result = mruby.loadAssetScript(context.assets, file)
            if (result.startsWith("Error:")) {
                throw RuntimeException("Failed to load $file: $result")
            }
        }
        // In instrumented tests, C-side _app_context returns null because
        // ActivityThread.currentApplication() can't find the Application.
        // Register the test context and override _app_context to return it as a JavaObject.
        val ctxId = mruby.registerJavaObject(context)
        mruby.eval("class << Mrboto; def _app_context; Mrboto::JavaObject.from_registry($ctxId); end; end")
    }
}
