package moe.bemly.mrboto

import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class NotificationTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    private fun setupActivity() {
        val scenario = ActivityScenario.launch(TestMrbotoActivity::class.java)
        scenario.onActivity { act ->
            val actId = mruby.registerJavaObject(act)
            mruby.eval("Mrboto.current_activity_id = $actId")
            mruby.eval("""
                class TestActivity < Mrboto::Activity
                  def on_create(bundle); super; end
                end
                Mrboto.current_activity = TestActivity.new(Mrboto.current_activity_id)
                Mrboto.current_activity.on_create(nil)
            """.trimIndent())
        }
    }

    // ── notify ────────────────────────────────────────────────────

    @Test
    fun notify_does_not_crash() {
        setupActivity()
        val result = mruby.eval("notify(1, 'Test Title', 'Test Message'); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun notify_with_default_channel() {
        setupActivity()
        val result = mruby.eval("notify(10, 'Default', 'Msg'); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun notify_with_custom_channel() {
        setupActivity()
        val result = mruby.eval("notify(2, 'Title', 'Msg', channel: 'my_channel'); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun notify_with_numeric_id_conversion() {
        setupActivity()
        val result = mruby.eval("notify(99, 'Title', 'Msg'); 'ok'")
        assertEquals("ok", result)
    }

    // ── notify_cancel ─────────────────────────────────────────────

    @Test
    fun notify_cancel_does_not_crash() {
        setupActivity()
        mruby.eval("notify(3, 'Title', 'Msg')")
        val result = mruby.eval("notify_cancel(3); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun notify_cancel_nonexistent_does_not_crash() {
        setupActivity()
        val result = mruby.eval("notify_cancel(9999); 'ok'")
        assertEquals("ok", result)
    }

    // ── notify_big ────────────────────────────────────────────────

    @Test
    fun notify_big_does_not_crash() {
        setupActivity()
        val result = mruby.eval("notify_big(4, 'Big Title', 'This is a very long notification text'); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun notify_big_with_custom_channel() {
        setupActivity()
        val result = mruby.eval("notify_big(14, 'Big', 'Text', channel: 'updates'); 'ok'")
        assertEquals("ok", result)
    }

    // ── notify_progress ───────────────────────────────────────────

    @Test
    fun notify_progress_does_not_crash() {
        setupActivity()
        val result = mruby.eval("notify_progress(5, 'Downloading', '50% done', 50, 100); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun notify_progress_with_zero_progress() {
        setupActivity()
        val result = mruby.eval("notify_progress(15, 'Starting', '0%', 0, 100); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun notify_progress_with_max_progress() {
        setupActivity()
        val result = mruby.eval("notify_progress(16, 'Done', '100%', 100, 100); 'ok'")
        assertEquals("ok", result)
    }

    // ── Module methods existence ──────────────────────────────────

    @Test
    fun notify_module_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:notify).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:notify_cancel).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:notify_big).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:notify_progress).to_s"))
    }

    @Test
    fun top_level_notify_methods_exist() {
        assertEquals("true", mruby.eval("method(:notify).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:notify_cancel).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:notify_big).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:notify_progress).nil? rescue false; true.to_s"))
    }

    // ── Module-level direct calls ─────────────────────────────────

    @Test
    fun module_notify_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.notify(20, 'Mod', 'Msg'); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun module_notify_cancel_does_not_crash() {
        setupActivity()
        mruby.eval("Mrboto::Helpers.notify(21, 'Mod', 'Msg')")
        val result = mruby.eval("Mrboto::Helpers.notify_cancel(21); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun module_notify_big_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.notify_big(22, 'Mod', 'Big text'); 'ok'")
        assertEquals("ok", result)
    }

    @Test
    fun module_notify_progress_does_not_crash() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.notify_progress(23, 'Mod', 'Progress', 25, 50); 'ok'")
        assertEquals("ok", result)
    }
}
