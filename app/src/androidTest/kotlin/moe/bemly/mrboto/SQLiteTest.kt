package moe.bemly.mrboto

import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SQLiteTest {

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

    // ── Open ──────────────────────────────────────────────────────

    @Test
    fun sqlite_open_creates_database() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("test_db")
            db.class.name
        """.trimIndent())
        assertEquals("Mrboto::Helpers::SQLiteDB", result)
    }

    @Test
    fun sqlite_open_returns_SQLiteDB_instance() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("type_db")
            db.is_a?(Mrboto::Helpers::SQLiteDB).to_s
        """.trimIndent())
        assertEquals("true", result)
    }

    // ── Execute (DDL) ─────────────────────────────────────────────

    @Test
    fun sqlite_execute_creates_table() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("test_create")
            result = db.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, age INTEGER)")
            db.close
            result.to_s
        """.trimIndent())
        assertEquals("true", result)
    }

    @Test
    fun sqlite_execute_creates_multiple_tables() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("multi_table")
            db.execute("CREATE TABLE a (id INTEGER PRIMARY KEY)")
            db.execute("CREATE TABLE b (id INTEGER PRIMARY KEY)")
            rows = db.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
            db.close
            rows.length.to_s
        """.trimIndent())
        assertEquals("2", result)
    }

    // ── Insert ────────────────────────────────────────────────────

    @Test
    fun sqlite_insert_and_query() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("test_crud")
            db.execute("CREATE TABLE items (id INTEGER PRIMARY KEY, name TEXT)")
            db.insert("items", { "name" => "apple" })
            db.insert("items", { "name" => "banana" })
            rows = db.query("SELECT * FROM items ORDER BY id")
            db.close
            rows.length.to_s
        """.trimIndent())
        assertEquals("2", result)
    }

    @Test
    fun sqlite_insert_returns_positive_id() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("insert_id")
            db.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT)")
            id = db.insert("t", { "val" => "test" })
            db.close
            id > 0
        """.trimIndent())
        assertEquals("true", result)
    }

    @Test
    fun sqlite_insert_multiple_values() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("insert_multi")
            db.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, name TEXT, age INTEGER)")
            db.insert("t", { "name" => "Alice", "age" => 30 })
            db.insert("t", { "name" => "Bob", "age" => 25 })
            rows = db.query("SELECT * FROM t ORDER BY id")
            db.close
            rows.length.to_s
        """.trimIndent())
        assertEquals("2", result)
    }

    // ── Query ─────────────────────────────────────────────────────

    @Test
    fun sqlite_query_returns_hashes() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("test_hash")
            db.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT)")
            db.insert("t", { "val" => "hello" })
            rows = db.query("SELECT * FROM t")
            db.close
            rows[0]["val"]
        """.trimIndent())
        assertEquals("hello", result)
    }

    @Test
    fun sqlite_query_returns_array_of_hashes() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("query_array")
            db.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT)")
            db.insert("t", { "val" => "a" })
            db.insert("t", { "val" => "b" })
            rows = db.query("SELECT * FROM t ORDER BY id")
            db.close
            rows.class.name
        """.trimIndent())
        assertEquals("Array", result)
    }

    @Test
    fun sqlite_query_with_where_clause() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("query_where")
            db.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT)")
            db.insert("t", { "val" => "keep" })
            db.insert("t", { "val" => "drop" })
            rows = db.query("SELECT * FROM t WHERE val = 'keep'")
            db.close
            rows.length.to_s
        """.trimIndent())
        assertEquals("1", result)
    }

    @Test
    fun sqlite_raw_query_works() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("raw_query")
            db.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT)")
            db.insert("t", { "val" => "raw" })
            rows = db.raw_query("SELECT * FROM t")
            db.close
            rows[0]["val"]
        """.trimIndent())
        assertEquals("raw", result)
    }

    // ── Update ────────────────────────────────────────────────────

    @Test
    fun sqlite_update_modifies_rows() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("test_update")
            db.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT)")
            db.insert("t", { "val" => "old" })
            db.update("t", { "val" => "new" }, "id = 1")
            rows = db.query("SELECT * FROM t WHERE id = 1")
            db.close
            rows[0]["val"]
        """.trimIndent())
        assertEquals("new", result)
    }

    @Test
    fun sqlite_update_returns_affected_count() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("update_count")
            db.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT)")
            db.insert("t", { "val" => "a" })
            db.insert("t", { "val" => "b" })
            count = db.update("t", { "val" => "updated" }, "1=1")
            db.close
            count.to_s
        """.trimIndent())
        assertEquals("2", result)
    }

    // ── Delete ────────────────────────────────────────────────────

    @Test
    fun sqlite_delete_removes_rows() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("test_delete")
            db.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT)")
            db.insert("t", { "val" => "gone" })
            count_before = db.query("SELECT * FROM t").length
            db.delete("t", "id = 1")
            count_after = db.query("SELECT * FROM t").length
            db.close
            "#{count_before}:#{count_after}"
        """.trimIndent())
        assertEquals("1:0", result)
    }

    @Test
    fun sqlite_delete_with_condition() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("delete_cond")
            db.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, val TEXT)")
            db.insert("t", { "val" => "keep" })
            db.insert("t", { "val" => "drop" })
            db.delete("t", "val = 'drop'")
            rows = db.query("SELECT * FROM t")
            db.close
            rows.length.to_s
        """.trimIndent())
        assertEquals("1", result)
    }

    // ── Close ─────────────────────────────────────────────────────

    @Test
    fun sqlite_close_does_not_crash() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("close_test")
            db.execute("CREATE TABLE t (id INTEGER PRIMARY KEY)")
            db.close
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    @Test
    fun sqlite_double_close_does_not_crash() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("double_close")
            db.close
            db.close
            'ok'
        """.trimIndent())
        assertEquals("ok", result)
    }

    // ── Class and method existence ────────────────────────────────

    @Test
    fun sqlite_class_exists() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.const_defined?(:SQLiteDB).to_s"))
    }

    @Test
    fun top_level_sqlite_open_exists() {
        assertEquals("true", mruby.eval("method(:sqlite_open).nil? rescue false; true.to_s"))
    }

    @Test
    fun module_sqlite_open_exists() {
        assertEquals("true", mruby.eval("Mrboto::Helpers.respond_to?(:sqlite_open).to_s"))
    }

    @Test
    fun SQLiteDB_responds_to_crud_methods() {
        assertEquals("true", mruby.eval("Mrboto::Helpers::SQLiteDB.instance_methods.include?(:execute).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers::SQLiteDB.instance_methods.include?(:insert).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers::SQLiteDB.instance_methods.include?(:query).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers::SQLiteDB.instance_methods.include?(:update).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers::SQLiteDB.instance_methods.include?(:delete).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers::SQLiteDB.instance_methods.include?(:close).to_s"))
        assertEquals("true", mruby.eval("Mrboto::Helpers::SQLiteDB.instance_methods.include?(:raw_query).to_s"))
    }

    // ── Full CRUD workflow ────────────────────────────────────────

    @Test
    fun sqlite_full_crud_workflow() {
        setupActivity()
        val result = mruby.eval("""
            db = sqlite_open("full_crud")
            db.execute("CREATE TABLE notes (id INTEGER PRIMARY KEY, title TEXT, body TEXT)")
            id1 = db.insert("notes", { "title" => "First", "body" => "Hello" })
            id2 = db.insert("notes", { "title" => "Second", "body" => "World" })
            db.update("notes", { "title" => "Updated First" }, "id = #{id1}")
            db.delete("notes", "id = #{id2}")
            rows = db.query("SELECT * FROM notes ORDER BY id")
            db.close
            result = rows.map { |r| r["title"] }.join(",")
            result
        """.trimIndent())
        assertEquals("Updated First", result)
    }
}
