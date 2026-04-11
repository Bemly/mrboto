package com.mrboto.demo

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mrboto.demo.databinding.ActivityMainBinding
import com.mrboto.MRuby
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var mruby: MRuby

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        try {
            mruby = MRuby()
            binding.tvVersion.text = "mruby version: ${mruby.version()}"
        } catch (e: IllegalStateException) {
            binding.tvVersion.text = "Failed to initialize mruby: ${e.message}"
            showError("Could not initialize mruby VM")
            return
        }

        setupExamples()
    }

    private fun setupExamples() {
        binding.btnBasicArithmetic.setOnClickListener {
            appendResult("Basic Arithmetic", mruby.eval("3 + 4 * 2"))
        }

        binding.btnStringOps.setOnClickListener {
            appendResult("String Operations",
                mruby.eval("\"hello world\".upcase.reverse"))
        }

        binding.btnArrayOps.setOnClickListener {
            appendResult("Array Operations",
                mruby.eval("[3, 1, 4, 1, 5].sort.uniq.join(\", \")"))
        }

        binding.btnFibonacci.setOnClickListener {
            appendResult("Fibonacci (recursive)",
                mruby.eval("""
                    def fib(n)
                      n <= 1 ? n : fib(n - 1) + fib(n - 2)
                    end
                    fib(15)
                """.trimIndent()))
        }

        binding.btnHashOps.setOnClickListener {
            appendResult("Hash Operations",
                mruby.eval("{ name: \"mruby\", version: 3.4 }[:name].to_s"))
        }

        binding.btnSyntaxError.setOnClickListener {
            appendResult("Syntax Error Demo",
                mruby.eval("def foo("))
        }

        binding.btnRuntimeError.setOnClickListener {
            appendResult("Runtime Error Demo",
                mruby.eval("1 / 0"))
        }

        binding.btnEvalMrb.setOnClickListener {
            loadAndEvalMrb()
        }

        binding.btnMultipleEvals.setOnClickListener {
            runMultipleEvals()
        }

        binding.btnGc.setOnClickListener {
            mruby.gc()
            appendResult("Garbage Collection", "GC completed")
        }

        binding.btnClear.setOnClickListener {
            binding.tvOutput.text = ""
        }
    }

    private fun loadAndEvalMrb() {
        try {
            val bytecode = assets.open("hello.mrb").use { it.readBytes() }
            val result = mruby.evalBytecode(bytecode)
            appendResult("Bytecode (hello.mrb)", result)
        } catch (e: IOException) {
            appendResult("Bytecode Error", "Could not load hello.mrb from assets: ${e.message}")
        }
    }

    private fun runMultipleEvals() {
        val sb = StringBuilder()

        sb.appendLine("--- Multiple Evals ---")
        sb.appendLine("Define variable: ${mruby.eval("x = 42")}")
        sb.appendLine("Use variable:    ${mruby.eval("x * 2")}")
        sb.appendLine("Define method:   ${mruby.eval("def double(n); n * 2; end")}")
        sb.appendLine("Call method:     ${mruby.eval("double(21)")}")

        appendResult("State Persistence", sb.toString().trim())
    }

    private fun appendResult(label: String, result: String) {
        val entry = "[$label]\n  => $result\n"
        binding.tvOutput.append(entry)
        binding.scrollView.fullScroll(android.view.View.FOCUS_DOWN)
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mruby.isInitialized) {
            mruby.close()
        }
    }
}
