package moe.bemly.mrboto

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
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

    /** Create a TestMrbotoActivity instance without launching it via Intent. */
    fun createTestActivity(): TestMrbotoActivity {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val component = ComponentName(context, TestMrbotoActivity::class.java)
        return instrumentation.newActivity(
            TestMrbotoActivity::class.java.classLoader!!,
            TestMrbotoActivity::class.java.name,
            null
        ) as TestMrbotoActivity
    }

    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                mruby = MRuby()
                mruby.registerAndroidClasses()
                loadCoreScripts()
                try {
                    base.evaluate()
                } finally {
                    // Clear animation on the test View BEFORE closing the VM,
                    // so we can still look up the View object
                    clearTestViewAnimation()
                    mruby.close()
                    mruby.clearRegistry()
                    drainPendingOperations()
                }
            }
        }
    }

    private var testViewId: Int = 0
    private var testViewRef: android.view.View? = null

    private fun clearTestViewAnimation() {
        testViewRef?.clearAnimation()
    }

    private fun drainPendingOperations() {
        // Post a no-op to the main thread and wait — this ensures all
        // previously posted messages (animation frames, dialog callbacks,
        // etc.) from the prior test are fully processed before the next
        // test starts.
        val latch = java.util.concurrent.CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { latch.countDown() }
        latch.await(500, java.util.concurrent.TimeUnit.MILLISECONDS)

        // Trigger GC to clean up any dangling JNI references
        System.gc()
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
            if (result.contains("Error") || result.contains("SyntaxError") ||
                result.contains("NoMethodError") || result.contains("NameError")) {
                throw RuntimeException("Failed to load $file: $result")
            }
        }
        // In instrumented tests, C-side _app_context returns null because
        // ActivityThread.currentApplication() can't find the Application.
        // Register the test context and override _app_context to return it as a JavaObject.
        val ctxId = mruby.registerJavaObject(context)
        mruby.eval("class << Mrboto; def _app_context; Mrboto::JavaObject.from_registry($ctxId); end; end")
        mruby.eval("Mrboto._test_ctx_id = $ctxId")

        // Create a simple View for snackbar/popup/animation tests
        val testView = android.view.View(context)
        testViewRef = testView
        testViewId = mruby.registerJavaObject(testView)
        mruby.eval("Mrboto._test_view_id = $testViewId")
    }
}
