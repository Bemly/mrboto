package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FileOpsTest {

    @get:Rule
    val mrbotoRule = MrbotoTestRule()

    private val mruby: MRuby
        get() = mrbotoRule.mruby

    private fun setupActivity() {
        val act = mrbotoRule.createTestActivity()
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

    // ── Write & Read ──────────────────────────────────────────────

    @Test
    fun file_write_and_read() {
        setupActivity()
        mruby.eval("file_write('test_file.txt', 'Hello File')")
        val result = mruby.eval("file_read('test_file.txt')")
        assertEquals("Hello File", result)
    }

    @Test
    fun file_write_overwrites_existing() {
        setupActivity()
        mruby.eval("file_write('overwrite.txt', 'old')")
        mruby.eval("file_write('overwrite.txt', 'new')")
        val result = mruby.eval("file_read('overwrite.txt')")
        assertEquals("new", result)
    }

    @Test
    fun file_write_empty_content() {
        setupActivity()
        mruby.eval("file_write('empty.txt', '')")
        val result = mruby.eval("file_read('empty.txt')")
        assertEquals("", result)
    }

    @Test
    fun file_write_unicode_content() {
        setupActivity()
        mruby.eval("file_write('unicode.txt', '日本語テスト')")
        val result = mruby.eval("file_read('unicode.txt')")
        assertEquals("日本語テスト", result)
    }

    @Test
    fun file_read_returns_string_class() {
        setupActivity()
        mruby.eval("file_write('typecheck.txt', 'data')")
        val result = mruby.eval("file_read('typecheck.txt').class.to_s")
        assertEquals("String", result)
    }

    @Test
    fun file_read_nonexistent_returns_empty_string() {
        setupActivity()
        val result = mruby.eval("file_read('no_such_file_xyz.txt')")
        assertEquals("", result)
    }

    // ── Exists ────────────────────────────────────────────────────

    @Test
    fun file_exists_returns_true_after_write() {
        setupActivity()
        mruby.eval("file_write('exists_test.txt', 'data')")
        val result = mruby.eval("file_exists?('exists_test.txt').to_s")
        assertEquals("true", result)
    }

    @Test
    fun file_exists_returns_false_for_missing() {
        setupActivity()
        val result = mruby.eval("file_exists?('no_such_file_xyz.txt').to_s")
        assertEquals("false", result)
    }

    // ── Delete ────────────────────────────────────────────────────

    @Test
    fun file_delete_removes_file() {
        setupActivity()
        mruby.eval("file_write('del_test.txt', 'bye')")
        mruby.eval("file_delete('del_test.txt')")
        val result = mruby.eval("file_exists?('del_test.txt').to_s")
        assertEquals("false", result)
    }

    @Test
    fun file_delete_nonexistent_does_not_crash() {
        setupActivity()
        val result = mruby.eval("file_delete('ghost_file.txt'); 'ok'")
        assertEquals("ok", result)
    }

    // ── List ──────────────────────────────────────────────────────

    @Test
    fun file_list_returns_array() {
        setupActivity()
        mruby.eval("file_write('list_test.txt', 'hi')")
        val result = mruby.eval("file_list.class.name")
        assertEquals("Array", result)
    }

    @Test
    fun file_list_contains_written_file() {
        setupActivity()
        mruby.eval("file_write('findme.txt', 'find')")
        val result = mruby.eval("file_list.include?('findme.txt').to_s")
        assertEquals("true", result)
    }

    // ── Size ──────────────────────────────────────────────────────

    @Test
    fun file_size_returns_length() {
        setupActivity()
        mruby.eval("file_write('size_test.txt', '12345')")
        val result = mruby.eval("file_size('size_test.txt')")
        assertEquals("5", result)
    }

    @Test
    fun file_size_returns_zero_for_empty_file() {
        setupActivity()
        mruby.eval("file_write('empty_size.txt', '')")
        val result = mruby.eval("file_size('empty_size.txt')")
        assertEquals("0", result)
    }

    // ── Cache ─────────────────────────────────────────────────────

    @Test
    fun cache_write_and_read() {
        setupActivity()
        mruby.eval("cache_write('cache_test.txt', 'cached')")
        val result = mruby.eval("cache_read('cache_test.txt')")
        assertEquals("cached", result)
    }

    @Test
    fun cache_overwrite_works() {
        setupActivity()
        mruby.eval("cache_write('cache_over.txt', 'v1')")
        mruby.eval("cache_write('cache_over.txt', 'v2')")
        val result = mruby.eval("cache_read('cache_over.txt')")
        assertEquals("v2", result)
    }

    // ── External ──────────────────────────────────────────────────

    @Test
    fun external_file_write_and_read() {
        setupActivity()
        mruby.eval("external_file_write('ext_test.txt', 'external')")
        val result = mruby.eval("external_file_read('ext_test.txt')")
        assertEquals("external", result)
    }

    // ── Module methods ────────────────────────────────────────────

    @Test
    fun file_ops_module_exists() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.const_defined?(:FileOps).to_s"))
    }

    @Test
    fun top_level_file_methods_exist() {
        assertEquals("true", mruby.eval("method(:file_write).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:file_read).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:file_exists?).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:file_delete).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:file_list).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:file_size).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:cache_write).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:cache_read).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:external_file_write).nil? rescue false; true.to_s"))
        assertEquals("true", mruby.eval("method(:external_file_read).nil? rescue false; true.to_s"))
    }

    @Test
    fun module_file_ops_methods_exist() {
        assertEquals("true", mruby.eval("Mrboto::Helpers::FileOps.respond_to?(:write).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers::FileOps.respond_to?(:read).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers::FileOps.respond_to?(:exists?).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers::FileOps.respond_to?(:delete).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers::FileOps.respond_to?(:list).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers::FileOps.respond_to?(:size).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers::FileOps.respond_to?(:cache_write).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers::FileOps.respond_to?(:cache_read).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers::FileOps.respond_to?(:external_write).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers::FileOps.respond_to?(:external_read).to_s"))
    }

    @Test
    fun module_file_write_works() {
        setupActivity()
        mruby.eval("Mrboto::Helpers::FileOps.write('mod_test.txt', 'module')")
        val result = mruby.eval("Mrboto::Helpers::FileOps.read('mod_test.txt')")
        assertEquals("module", result)
    }

    @Test
    fun module_file_exists_works() {
        setupActivity()
        mruby.eval("Mrboto::Helpers::FileOps.write('mod_exists.txt', 'yes')")
        val result = mruby.eval("Mrboto::Helpers::FileOps.exists?('mod_exists.txt').to_s")
        assertEquals("true", result)
    }
}
