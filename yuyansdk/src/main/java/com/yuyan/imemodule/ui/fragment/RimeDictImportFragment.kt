package com.yuyan.imemodule.ui.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceScreen
import com.yuyan.imemodule.ui.fragment.base.CsPreferenceFragment
import com.yuyan.imemodule.utils.addPreference
import com.yuyan.imemodule.manager.RimeUserDictImporter
import kotlinx.coroutines.launch

/**
 * 词库学习Fragment
 * 通过模拟输入让学习已有词汇，提升其词频
 */
class RimeDictImportFragment : CsPreferenceFragment() {
    
    private lateinit var importLauncher: ActivityResultLauncher<String>
    private lateinit var previewLauncher: ActivityResultLauncher<String>
    private var progressDialog: AlertDialog? = null
    private var isT9Mode = false
    
    override fun onDestroy() {
        super.onDestroy()
        progressDialog?.dismiss()
        progressDialog = null
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        importLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { importDictionary(it) }
        }
        
        previewLauncher = registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { previewDictionary(it) }
        }
    }
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceScreen = preferenceManager.createPreferenceScreen(preferenceManager.context)
        onPreferenceUiCreated(preferenceScreen)
    }
    
    private fun onPreferenceUiCreated(screen: PreferenceScreen) {
        // 选择词库
        screen.addPreference("📋 选择词库文件") {
            showImportConfirm()
        }
        
        // 使用说明
        screen.addPreference("📖 使用说明") {
            showInstructions()
        }
        
        // 支持格式说明
        screen.addPreference("📝 支持的格式") {
            showFormatInfo()
        }
    }
    
    private fun showImportConfirm() {
        // 创建对话框布局
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 10)
        }
        
        // 说明文本
        val message = TextView(requireContext()).apply {
            text = """
                📌 工作原理：模拟输入学习
                ⏱️ 处理速度：约50-100词/秒
                💾 保存位置：用户词库
                📊 建议大小：<1万词条
                
                ⚠️ 重要提示：
                • 只能学习词库中已存在的词汇
                • 不能添加新词，只能提升已有词的词频
                • 如果词库中没有该词，会自动跳过
                • 学习期间请勿操作输入法
            """.trimIndent()
            textSize = 14f
            setPadding(0, 0, 0, 20)
        }
        layout.addView(message)
        
        // T9模式复选框
        val t9Checkbox = CheckBox(requireContext()).apply {
            text = "T9数字格式 (如: 6446 你好)"
            isChecked = isT9Mode
            setOnCheckedChangeListener { _, checked ->
                isT9Mode = checked
            }
        }
        layout.addView(t9Checkbox)
        
        AlertDialog.Builder(requireContext())
            .setTitle("📚 学习词库（提升词频）")
            .setView(layout)
            .setPositiveButton("选择文件") { _, _ ->
                importLauncher.launch("text/plain")
            }
            .setNegativeButton("取消", null)
            .setNeutralButton("预览") { _, _ ->
                previewLauncher.launch("text/plain")
            }
            .show()
    }
    
    private fun importDictionary(uri: android.net.Uri) {
        // 创建进度对话框
        val progressView = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            
            addView(ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                id = View.generateViewId()
                isIndeterminate = false
                max = 100
            })
            
            addView(TextView(context).apply {
                id = View.generateViewId()
                text = "准备导入..."
                textSize = 16f
                setPadding(0, 20, 0, 0)
            })
            
            addView(TextView(context).apply {
                id = View.generateViewId()
                text = "进度: 0/0"
                textSize = 14f
                setPadding(0, 10, 0, 0)
            })
            
            addView(TextView(context).apply {
                id = View.generateViewId()
                text = "当前词: "
                textSize = 12f
                setPadding(0, 10, 0, 0)
            })
            
            addView(TextView(context).apply {
                id = View.generateViewId()
                text = "成功: 0 | 跳过: 0 | 失败: 0"
                textSize = 11f
                setPadding(0, 10, 0, 0)
                setTextColor(0xFF666666.toInt())
            })
        }
        
        progressDialog = AlertDialog.Builder(requireContext())
            .setTitle("📚 正在学习词库...")
            .setView(progressView)
            .setCancelable(false)
            .create()
        
        progressDialog?.show()
        
        val progressBar = progressView.getChildAt(0) as ProgressBar
        val titleText = progressView.getChildAt(1) as TextView
        val progressText = progressView.getChildAt(2) as TextView
        val currentWordText = progressView.getChildAt(3) as TextView
        val statsText = progressView.getChildAt(4) as TextView
        
        lifecycleScope.launch {
            try {
                val result = RimeUserDictImporter.importFromTxt(uri, isT9Mode) { current, total, word, stats ->
                    // 安全更新UI - 检查Fragment是否还存活
                    if (isAdded && !isDetached) {
                        val progress = (current * 100 / total).coerceIn(0, 100)
                        progressBar.progress = progress
                        titleText.text = "正在学习... ($progress%)"
                        progressText.text = "进度: $current/$total"
                        currentWordText.text = "当前词: $word"
                        statsText.text = "✓ 已学习: ${stats.success} | ⊘ 跳过: ${stats.skipped} | ✗ 失败: ${stats.failed}"
                    }
                }
                
                result.fold(
                    onSuccess = { stats ->
                        if (isAdded) {
                            showResultDialog(stats)
                        }
                    },
                    onFailure = { error ->
                        if (isAdded) {
                            AlertDialog.Builder(requireContext())
                                .setTitle("❌ 学习失败")
                                .setMessage("错误: ${error.message}\n\n请检查词库格式是否正确")
                                .setPositiveButton("确定", null)
                                .show()
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                if (isAdded) {
                    Toast.makeText(requireContext(), "学习出错: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                // 确保dialog一定会被关闭
                progressDialog?.dismiss()
                progressDialog = null
            }
        }
    }
    
    private fun showResultDialog(stats: RimeUserDictImporter.ImportStats) {
        val message = """
            📊 学习统计：
            
            总计词条：${stats.total}
            ✓ 已学习：${stats.success}
            ⊘ 跳过（词库中无此词）：${stats.skipped}
            ✗ 失败：${stats.failed}
            
            学习成功率：${stats.successRate}%
            
            ${if (stats.success > 0) "✅ 已学习的词汇词频已提升\n下次输入时会优先显示" else "⚠️ 没有成功学习任何词条\n可能原因：词库中没有这些词汇"}
        """.trimIndent()
        
        AlertDialog.Builder(requireContext())
            .setTitle("✅ 学习完成")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun previewDictionary(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val result = RimeUserDictImporter.previewDict(uri)
                if (!isAdded) return@launch
                
                result.fold(
                    onSuccess = { entries ->
                        if (entries.isEmpty()) {
                            Toast.makeText(requireContext(), "⚠️ 词库为空或格式不正确", Toast.LENGTH_SHORT).show()
                        } else {
                            showPreviewDialog(entries)
                        }
                    },
                    onFailure = { error ->
                        Toast.makeText(requireContext(), "预览失败: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                if (isAdded) {
                    Toast.makeText(requireContext(), "预览出错: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showPreviewDialog(entries: List<RimeUserDictImporter.WordEntry>) {
        val preview = entries.take(50).joinToString("\n") { "${it.word} → ${it.pinyin}" }
        val message = """
            📋 词库预览（前50条）：
            
            $preview
            
            ${if (entries.size > 50) "\n... 还有 ${entries.size - 50} 条" else ""}
            
            总计：${entries.size} 条词汇
            
            💡 提示：只有Rime词库中已存在的词才能被学习
        """.trimIndent()
        
        AlertDialog.Builder(requireContext())
            .setTitle("📄 词库预览")
            .setMessage(message)
            .setPositiveButton("开始学习") { _, _ ->
                importLauncher.launch("text/plain")
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showInstructions() {
        AlertDialog.Builder(requireContext())
            .setTitle("📖 使用说明")
            .setMessage("""
                🎯 工作原理
                通过模拟输入让学习已有词汇，提升其词频。词频越高的词会优先显示在候选列表中。
                
                ❗ 核心限制
                • 只能学习词库中已存在的词汇
                • 不能添加新词，只能提升已有词的词频
                • 如果词库中没有该词，会自动跳过
                
                📝 支持格式
                1. 词语 拼音 [权重]
                2. 拼音 词语 [权重]
                3. T9数字 词语 [权重]
                
                ⚙️ 工作流程
                1. 选择txt词库文件
                2. 自动解析并识别格式
                3. 逐个模拟输入学习
                4. 提升词频并保存
                
                ⚡ 性能建议
                • 推荐: <5000词条（约1-2分钟）
                • 可接受: <10000词条（约2-5分钟）
                • 大词库: 建议分批学习
                
                💡 实际效果
                • 学习后的词汇会优先显示
                • 适合提升专业术语、常用词的优先级
                • 学习后立即生效
                
                🔧 建议工具
                使用"深蓝词库转换"生成txt格式
            """.trimIndent())
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun showFormatInfo() {
        AlertDialog.Builder(requireContext())
            .setTitle("📝 支持的格式")
            .setMessage("""
                ✅ 格式1：词语 拼音
                你好 nihao
                世界 shijie
                中国 zhongguo
                
                ✅ 格式2：拼音 词语
                nihao 你好
                shijie 世界
                zhongguo 中国
                
                ✅ 格式3：词语 拼音 权重
                你好 nihao 1000
                世界 shijie 500
                中国 zhongguo 2000
                （权重越高，学习次数越多）
                
                ✅ 格式4：T9数字 词语
                6446 你好
                74543 世界
                946642 中国
                
                ✅ 格式5：深蓝词库格式
                你好	ni hao	1000
                世界	shi jie	500
                
                📌 分隔符
                支持：空格、Tab
                
                📌 注释
                支持：# 或 // 开头的行
                
                📌 编码
                支持：UTF-8、GBK、GB2312
            """.trimIndent())
            .setPositiveButton("确定", null)
            .show()
    }
}
