package com.mrboto.demo

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mrboto.demo.databinding.ActivityMainBinding
import com.mrboto.MRuby
import java.io.IOException

/**
 * mrboto Demo — 执行嵌入的 Ruby 脚本
 *
 * 所有 Ruby 脚本以 .mrb 预编译字节码形式存储在 assets/ 目录中，
 * 点击按钮时加载并执行对应的字节码文件。
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
            runMrb("arithmetic")
        }

        binding.btnStringOps.setOnClickListener {
            runMrb("strings")
        }

        binding.btnArrayOps.setOnClickListener {
            runMrb("arrays")
        }

        binding.btnFibonacci.setOnClickListener {
            runMrb("fibonacci")
        }

        binding.btnHashOps.setOnClickListener {
            runMrb("hashes")
        }

        binding.btnSyntaxError.setOnClickListener {
            // 语法错误无法预编译，使用运行时 eval 方式
            appendResult("语法错误", mruby.eval("def foo("))
        }

        binding.btnRuntimeError.setOnClickListener {
            runMrb("runtime_error")
        }

        binding.btnMultiEval.setOnClickListener {
            runMrb("multi_eval")
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
     * 从 assets 加载并执行 .mrb 字节码文件。
     */
    private fun runMrb(name: String) {
        try {
            val bytecode = assets.open("${name}.mrb").use { it.readBytes() }
            val result = mruby.evalBytecode(bytecode)
            appendResult(name, result)
        } catch (e: IOException) {
            appendResult(name, "Error: 无法加载 ${name}.mrb — ${e.message}")
        }
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
