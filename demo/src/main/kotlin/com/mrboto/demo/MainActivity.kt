package com.mrboto.demo

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mrboto.demo.databinding.ActivityMainBinding
import com.mrboto.MRuby

/**
 * mrboto Demo — 执行嵌入的 Ruby 脚本
 *
 * 所有 Ruby 脚本以 .rb 源码形式存储在 assets/ 目录中，
 * 点击按钮时读取源码并用 mruby 运行时编译器执行。
 */
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
            runRuby("arithmetic")
        }

        binding.btnStringOps.setOnClickListener {
            runRuby("strings")
        }

        binding.btnArrayOps.setOnClickListener {
            runRuby("arrays")
        }

        binding.btnFibonacci.setOnClickListener {
            runRuby("fibonacci")
        }

        binding.btnHashOps.setOnClickListener {
            runRuby("hashes")
        }

        binding.btnSyntaxError.setOnClickListener {
            runRuby("syntax_error")
        }

        binding.btnRuntimeError.setOnClickListener {
            runRuby("runtime_error")
        }

        binding.btnMultiEval.setOnClickListener {
            runRuby("multi_eval")
        }

        binding.btnGc.setOnClickListener {
            mruby.gc()
            appendResult("垃圾回收", "GC completed")
        }

        binding.btnClear.setOnClickListener {
            binding.tvOutput.text = ""
        }
    }

    /**
     * 从 assets 加载并执行 .rb 源码文件。
     * mruby 包含运行时编译器，可直接编译执行 Ruby 源码字符串。
     */
    private fun runRuby(name: String) {
        val result = try {
            val source = assets.open("${name}.rb").bufferedReader().use { it.readText() }
            mruby.eval(source)
        } catch (e: java.io.IOException) {
            "Error: 无法加载 ${name}.rb — ${e.message}"
        }
        appendResult(name, result)
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
