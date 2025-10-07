package com.yuyan.imemodule.manager

import android.content.Context
import android.net.Uri
import com.yuyan.imemodule.application.Launcher
import com.yuyan.inputmethod.core.Rime
import com.yuyan.inputmethod.util.T9PinYinUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.charset.Charset

/**
 * 词库学习工具
 * 通过模拟输入让学习已有词汇，提升其词频
 * 
 * ⚠️ 重要说明：
 * - 只能学习词库中已存在的词汇
 * - 不能添加新词，只能提升已有词的词频
 * - 如果词库中没有该词，会自动跳过
 * 
 * 支持的格式：
 * 1. 词语 拼音 (如: 你好 nihao)
 * 2. 拼音 词语 (如: nihao 你好)
 * 3. 深蓝词库格式 (如: 你好	ni hao	1)
 * 4. T9数字格式 (如: 6446 你好)
 */
object RimeUserDictImporter {
    
    private val context: Context get() = Launcher.instance.context
    
    data class WordEntry(
        val word: String,
        val pinyin: String,
        val weight: Int = 0 // 词频权重
    )
    
    data class ImportStats(
        val total: Int,
        val success: Int,
        val skipped: Int,
        val failed: Int
    ) {
        val successRate: Int get() = if (total > 0) (success * 100 / total) else 0
    }
    
    /**
     * 从txt文件学习词库
     * 通过模拟输入让学习已有词汇，提升词频
     */
    suspend fun importFromTxt(
        uri: Uri,
        isT9Mode: Boolean = false,
        onProgress: (Int, Int, String, ImportStats) -> Unit
    ): Result<ImportStats> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("无法打开文件"))

            val entries = inputStream.use { parseEntries(it, isT9Mode) }
            
            if (entries.isEmpty()) {
                return@withContext Result.failure(Exception("词库为空或格式不正确"))
            }
            
            // 逐个学习
            var successCount = 0
            var skippedCount = 0
            var failedCount = 0
            
            entries.forEachIndexed { index, entry ->
                val currentStats = ImportStats(
                    total = entries.size,
                    success = successCount,
                    skipped = skippedCount,
                    failed = failedCount
                )
                
                withContext(Dispatchers.Main) {
                    onProgress(index + 1, entries.size, entry.word, currentStats)
                }
                
                when (val result = learnWord(entry)) {
                    LearnResult.SUCCESS -> successCount++
                    LearnResult.SKIPPED -> skippedCount++
                    LearnResult.FAILED -> failedCount++
                }
                
                // 根据词频权重决定学习次数
                val repeatTimes = when {
                    entry.weight >= 1000 -> 5  // 高频词学习5次
                    entry.weight >= 100 -> 3   // 中频词学习3次
                    entry.weight > 0 -> 2      // 有权重的词学习2次
                    else -> 3                  // 默认学习3次（即使没有权重）
                }
                
                if (repeatTimes > 1) {
                    repeat(repeatTimes - 1) {
                        delay(5)  // 减少重复学习延迟 30ms -> 5ms
                        learnWord(entry)
                    }
                }
                
                // 大幅减少延迟，加快学习速度
                if (index % 100 == 0) {
                    delay(30)  // 每100个词稍微停顿一下
                }
            }
            
            val finalStats = ImportStats(
                total = entries.size,
                success = successCount,
                skipped = skippedCount,
                failed = failedCount
            )
            
            Result.success(finalStats)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    private enum class LearnResult {
        SUCCESS, SKIPPED, FAILED
    }
    
    /**
     * 解析txt词库文件
     * 支持多种格式：
     * 1. 词语 拼音 [权重]
     * 2. 拼音 词语 [权重]
     * 3. T9数字 词语
     * 4. 搜狗格式：'pin'yin 词语
     */
    private fun parseEntries(inputStream: InputStream, isT9Mode: Boolean): List<WordEntry> {
        val entries = mutableListOf<WordEntry>()

        // 检测文件编码
        val charset = detectCharset(inputStream)

        // 重置流位置，因为detectCharset已经读取了部分内容
        if (inputStream.markSupported()) {
            inputStream.reset()
        } else {
            android.util.Log.w("RimeDictImporter", "InputStream doesn't support mark, charset detection may be inaccurate")
        }

        // 使用指定的字符集创建Reader，避免一次性加载整个文件到内存
        val reader = inputStream.reader(charset)
        val uniqueWords = mutableSetOf<String>() // 去重

        reader.useLines { lines ->
            lines.forEachIndexed { lineNum, line -> 
            try {
                if (line.isBlank() || line.startsWith("#") || line.startsWith("//")) {
                    return@forEachIndexed
                }
                
                // 清理行首行尾空白
                val cleanLine = line.trim()
                
                // 尝试多种分隔符（Tab、空格、逗号），但保留拼音中的单引号
                val parts = cleanLine.split(Regex("[\t ]+")).filter { it.isNotBlank() }
                
                // 调试日志
                android.util.Log.d("RimeDictImporter", "Line $lineNum: '$cleanLine' -> parts: ${parts.joinToString("|")}")
                
                if (parts.size < 2) {
                    android.util.Log.d("RimeDictImporter", "  SKIP: parts.size < 2")
                    return@forEachIndexed
                }
                
                // 检查各个条件（添加字符码点调试）
                android.util.Log.d("RimeDictImporter", "  isT9Mode: $isT9Mode")
                android.util.Log.d("RimeDictImporter", "  parts[0]: '${parts[0]}' isDigit: ${parts[0].all { it.isDigit() }} isAlpha: ${isAlpha(parts[0])} isChinese: ${isChinese(parts[0])}")
                android.util.Log.d("RimeDictImporter", "  parts[1]: '${parts[1]}' isChinese: ${isChinese(parts[1])} isAlpha: ${isAlpha(parts[1])}")
                // 打印字符的Unicode码点来判断真实字符
                if (parts[1].isNotEmpty()) {
                    val codePoints = parts[1].take(3).map { "U+${it.code.toString(16).uppercase()}" }.joinToString(" ")
                    android.util.Log.d("RimeDictImporter", "  parts[1] codePoints: $codePoints")
                }
                
                val entry = when {
                    // T9模式：数字 词语
                    isT9Mode && parts[0].all { it.isDigit() } && isChinese(parts[1]) -> {
                        android.util.Log.d("RimeDictImporter", "  MATCH: T9 mode")
                        val t9Code = parts[0]
                        val word = parts[1]
                        val pinyinArray = T9PinYinUtils.t9KeyToPinyin(t9Code)
                        val pinyin = pinyinArray.firstOrNull()
                        if (pinyin.isNullOrBlank()) {
                            null  // T9转拼音失败，跳过此条
                        } else {
                            val weight = parts.getOrNull(2)?.toIntOrNull() ?: 0
                            WordEntry(word, pinyin, weight)
                        }
                    }
                    // 词语 拼音 [权重]
                    isChinese(parts[0]) && isAlpha(parts[1]) -> {
                        android.util.Log.d("RimeDictImporter", "  MATCH: 词语 拼音")
                        val word = parts[0]
                        // 搜狗拼音格式：单引号转为空格（音节分隔）
                        val pinyin = parts[1].replace("'", " ").trim()
                        val weight = parts.getOrNull(2)?.toIntOrNull() ?: 0
                        WordEntry(word, pinyin, weight)
                    }
                    // 拼音 词语 [权重]（搜狗格式：'pin'yin 词语）
                    isAlpha(parts[0]) && isChinese(parts[1]) -> {
                        android.util.Log.d("RimeDictImporter", "  MATCH: 拼音 词语 (pinyin='${parts[0]}')")
                        // 搜狗拼音格式：单引号转为空格（音节分隔）
                        val pinyin = parts[0].replace("'", " ").trim()
                        android.util.Log.d("RimeDictImporter", "  Converted pinyin: '$pinyin'")
                        val word = parts[1]
                        val weight = parts.getOrNull(2)?.toIntOrNull() ?: 0
                        WordEntry(word, pinyin, weight)
                    }
                    // T9数字 词语（自动检测）
                    parts[0].all { it.isDigit() } && parts[0].length in 2..6 && isChinese(parts[1]) -> {
                        android.util.Log.d("RimeDictImporter", "  MATCH: T9 auto detect")
                        val t9Code = parts[0]
                        val word = parts[1]
                        val pinyinArray = T9PinYinUtils.t9KeyToPinyin(t9Code)
                        val pinyin = pinyinArray.firstOrNull()
                        if (pinyin.isNullOrBlank()) {
                            null  // T9转拼音失败，跳过此条
                        } else {
                            val weight = parts.getOrNull(2)?.toIntOrNull() ?: 0
                            WordEntry(word, pinyin, weight)
                        }
                    }
                    else -> {
                        android.util.Log.d("RimeDictImporter", "  NO MATCH - skipped")
                        null
                    }
                }
                
                // 去重并添加
                if (entry != null && entry.word !in uniqueWords) {
                    uniqueWords.add(entry.word)
                    entries.add(entry)
                }
            } catch (e: Exception) {
                // 忽略解析错误的行
            }
        }
        }

        return entries
    }

    /**
     * 检测文件编码，避免一次性读取整个文件到内存
     */
    private fun detectCharset(inputStream: InputStream): Charset {
        // 尝试多种编码，只读取文件开头的一部分进行检测
        val charsets = listOf(
            Charset.forName("GBK"),      // 尝试GBK优先
            Charset.forName("GB2312"),   // 然后GB2312
            Charsets.UTF_8,              // 最后UTF-8
            Charset.forName("Big5"),     // 繁体中文
            Charsets.ISO_8859_1
        )

        // 读取文件开头的一部分用于编码检测
        val buffer = ByteArray(1024) // 只读取1KB用于检测
        val bytesRead = inputStream.read(buffer)
        val sampleBytes = if (bytesRead > 0) buffer.copyOf(bytesRead) else ByteArray(0)

        android.util.Log.d("RimeDictImporter", "Sample size: ${sampleBytes.size} bytes")

        var bestCharset = Charsets.UTF_8
        var minReplacementRatio = Double.MAX_VALUE

        for (charset in charsets) {
            try {
                val text = String(sampleBytes, charset)
                // 检查替换字符的比例
                val fffdCount = text.count { it == '\uFFFD' }
                val ratio = if (text.isNotEmpty()) fffdCount.toDouble() / text.length else 0.0

                android.util.Log.d("RimeDictImporter", "Charset $charset: replacement ratio=$ratio")

                if (ratio < minReplacementRatio) {
                    minReplacementRatio = ratio
                    bestCharset = charset
                }

                // 如果替换字符很少，认为找到了正确的编码
                if (ratio < 0.1) {
                    android.util.Log.d("RimeDictImporter", "✅ Selected charset: $charset (ratio=$ratio)")
                    return charset
                }
            } catch (e: Exception) {
                android.util.Log.d("RimeDictImporter", "❌ Failed with $charset: ${e.message}")
            }
        }

        android.util.Log.w("RimeDictImporter", "⚠️ No perfect charset found, using $bestCharset (ratio=$minReplacementRatio)")
        return bestCharset
    }

    /**
     * 让Rime学习一个词
     * 原理：输入拼音，提交词语，Rime会自动记录到用户词库
     */
    private suspend fun learnWord(entry: WordEntry): LearnResult = withContext(Dispatchers.Main) {
        try {
            // 清空当前输入
            Rime.clearComposition()
            
            // 输入拼音（转换为小写字母）
            val pinyin = entry.pinyin.lowercase()
                .replace("'", "")  // 移除分隔符
                .replace("-", "")  // 移除连字符
                .replace(" ", "")  // 移除空格（Rime不需要空格分隔）
                .filter { it in 'a'..'z' }
            
            if (pinyin.isBlank()) {
                return@withContext LearnResult.SKIPPED
            }
            
            // 逐字符输入
            pinyin.forEach { char ->
                Rime.processKey(char.code, 0)
            }
            
            delay(10) // 给Rime一点时间处理（优化：20ms -> 10ms）
            
            // 获取候选词
            val context = Rime.getRimeContext()
            val candidates = context?.candidates
            
            when {
                candidates == null || candidates.isEmpty() -> {
                    android.util.Log.w("RimeDictImporter", "  ⚠️ FAILED: No candidates for '${entry.word}' with pinyin '$pinyin'")
                    Rime.clearComposition()
                    LearnResult.FAILED
                }
                else -> {
                    // 查找目标词语是否在候选中
                    val targetIndex = candidates.indexOfFirst { it.text == entry.word }
                    
                    // 调试：打印前5个候选词
                    val candidateList = candidates.take(5).joinToString(", ") { it.text }
                    android.util.Log.d("RimeDictImporter", "  Candidates: [$candidateList] for word='${entry.word}'")
                    
                    when {
                        targetIndex >= 0 -> {
                            // 找到了，选择它
                            android.util.Log.d("RimeDictImporter", "  ✅ SUCCESS: Found at index $targetIndex")
                            Rime.selectCandidate(targetIndex)
                            delay(5)  // 优化：10ms -> 5ms
                            LearnResult.SUCCESS
                        }
                        // 检查是否有候选词长度匹配（避免"中国"匹配到"中国人"）
                        candidates.any { it.text.length == entry.word.length && 
                                         (entry.word.contains(it.text) || it.text.contains(entry.word)) } -> {
                            // 长度相同的部分匹配，可能是异体字或变体
                            val matchIndex = candidates.indexOfFirst { 
                                it.text.length == entry.word.length && 
                                (entry.word.contains(it.text) || it.text.contains(entry.word))
                            }
                            Rime.selectCandidate(matchIndex)
                            delay(5)  // 优化：10ms -> 5ms
                            LearnResult.SUCCESS
                        }
                        else -> {
                            // 没找到，清空
                            android.util.Log.w("RimeDictImporter", "  ⚠️ SKIPPED: Word '${entry.word}' not in candidates")
                            Rime.clearComposition()
                            LearnResult.SKIPPED
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            LearnResult.FAILED
        }
    }
    
    /**
     * 判断是否包含中文字符
     * 包括：基本汉字、扩展A区、兼容汉字
     */
    private fun isChinese(str: String): Boolean {
        return str.any { char ->
            val code = char.code
            code in 0x4E00..0x9FFF ||  // CJK统一汉字
            code in 0x3400..0x4DBF ||  // CJK扩展A
            code in 0xF900..0xFAFF     // CJK兼容汉字
        }
    }
    
    /**
     * 判断是否为纯字母（拼音）
     */
    private fun isAlpha(str: String): Boolean {
        return str.all { it in 'a'..'z' || it in 'A'..'Z' || it == '\'' || it == '-' || it == ' ' }
    }
    
    /**
     * 选择词库文件（读取前100条）
     */
    suspend fun previewDict(uri: Uri): Result<List<WordEntry>> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("无法打开文件"))
            
            // 尝试自动检测T9模式
            val entries = parseEntries(inputStream, false)
            inputStream.close()
            
            Result.success(entries.take(100))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

