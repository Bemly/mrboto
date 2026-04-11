package moe.bemly.mrboto

import org.junit.Assert

/**
 * Helper methods for evaluating Ruby code and asserting results in tests.
 */
object RubyEvalHelper {

    fun MRuby.evalRuby(code: String): String {
        return eval(code)
    }

    fun MRuby.assertEval(expected: String, code: String) {
        val result = eval(code)
        Assert.assertEquals("Ruby eval failed for: $code", expected, result)
    }

    fun MRuby.assertEvalContains(substring: String, code: String) {
        val result = eval(code)
        Assert.assertTrue(
            "Expected '$result' to contain '$substring'",
            result.contains(substring)
        )
    }

    fun MRuby.assertEvalStartsWith(prefix: String, code: String) {
        val result = eval(code)
        Assert.assertTrue(
            "Expected '$result' to start with '$prefix'",
            result.startsWith(prefix)
        )
    }

    fun MRuby.assertEvalEndsWith(suffix: String, code: String) {
        val result = eval(code)
        Assert.assertTrue(
            "Expected '$result' to end with '$suffix'",
            result.endsWith(suffix)
        )
    }

    fun MRuby.assertEvalNotEmpty(code: String) {
        val result = eval(code)
        Assert.assertTrue(
            "Expected non-empty result for: $code",
            result.isNotEmpty()
        )
    }
}
