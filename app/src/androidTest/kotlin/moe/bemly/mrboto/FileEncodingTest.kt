package moe.bemly.mrboto

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FileEncodingTest {
    @get:Rule val mrbotoRule = MrbotoTestRule()
    private val mruby get() = mrbotoRule.mruby

    private fun setupActivity() {
        val act = mrbotoRule.createTestActivity()
        val actId = mruby.registerJavaObject(act)
        mruby.eval("Mrboto.current_activity_id = $actId")
        mruby.eval("""
            class TestActivity < Mrboto::Activity; def on_create(bundle); super; end; end
            Mrboto.current_activity = TestActivity.new(Mrboto.current_activity_id)
            Mrboto.current_activity.on_create(nil)
        """.trimIndent())
    }

    @Test fun file_write_encoding_utf8() {
        setupActivity()
        val result = mruby.eval("""
            Mrboto::Helpers.file_write_encoding("enc_test.txt", "你好UTF8", "UTF-8").to_s
        """.trimIndent())
        assertEquals("true", result)
    }

    @Test fun file_read_encoding_utf8() {
        setupActivity()
        val result = mruby.eval("""
            Mrboto::Helpers.file_write_encoding("enc_read.txt", "编码测试", "UTF-8")
            Mrboto::Helpers.file_read_encoding("enc_read.txt", "UTF-8")
        """.trimIndent())
        assertEquals("编码测试", result)
    }

    @Test fun file_list_dir_returns_array() {
        setupActivity()
        val result = mruby.eval("""
            Mrboto::Helpers.file_list_dir("").class.name
        """.trimIndent())
        assertEquals("Array", result)
    }

    @Test fun file_mkdir_creates_dir() {
        setupActivity()
        val result = mruby.eval("""
            dir = Mrboto.current_activity.files_dir + "/mrboto_test_dir"
            Mrboto::Helpers.file_delete_dir(dir) if Mrboto::Helpers.file_exists_path(dir)
            Mrboto::Helpers.file_mkdir(dir).to_s
        """.trimIndent())
        assertEquals("true", result)
    }

    @Test fun file_delete_dir_removes_dir() {
        setupActivity()
        val result = mruby.eval("""
            dir = Mrboto.current_activity.files_dir + "/mrboto_test_dir"
            Mrboto::Helpers.file_mkdir(dir) unless Mrboto::Helpers.file_exists_path(dir)
            Mrboto::Helpers.file_delete_dir(dir).to_s
        """.trimIndent())
        assertEquals("true", result)
    }

    @Test fun file_exists_path_check() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.file_exists_path('/data').to_s")
        assertEquals("true", result)
    }

    @Test fun file_is_dir_check() {
        setupActivity()
        val result = mruby.eval("Mrboto::Helpers.file_is_dir('/data').to_s")
        assertEquals("true", result)
    }
}
