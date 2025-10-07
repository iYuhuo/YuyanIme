package com.yuyan.imemodule.manager

import android.content.Context
import com.yuyan.imemodule.application.Launcher
import java.io.File

/**
 * Rime词库编辑器
 * 专门用于编辑繁简转换词库
 */
object RimeDictionaryEditor {
    
    private val context: Context get() = Launcher.instance.context
    
    /**
     * 繁简字条目
     */
    data class CharacterEntry(
        val simplified: String,
        val traditional: String
    )
    
    /**
     * 繁简词组条目
     */
    data class PhraseEntry(
        val simplified: String,
        val traditional: String
    )
    
    /**
     * 加载繁简字词库
     */
    fun loadCharacterDictionary(): List<CharacterEntry> {
        val entries = mutableListOf<CharacterEntry>()
        
        try {
            val inputStream = context.assets.open("rime/opencc/STCharacters.txt")
            val text = inputStream.bufferedReader().readText()
            inputStream.close()
            
            text.lines().forEach { line ->
                if (line.isNotBlank() && line.contains("\t")) {
                    val parts = line.split("\t")
                    if (parts.size >= 2) {
                        val simplified = parts[0].trim()
                        val traditional = parts[1].trim()
                        entries.add(CharacterEntry(simplified, traditional))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return entries
    }
    
    /**
     * 加载繁简词组词库
     */
    fun loadPhraseDictionary(): List<PhraseEntry> {
        val entries = mutableListOf<PhraseEntry>()
        
        try {
            val inputStream = context.assets.open("rime/opencc/STPhrases.txt")
            val text = inputStream.bufferedReader().readText()
            inputStream.close()
            
            text.lines().forEach { line ->
                if (line.isNotBlank() && line.contains("\t")) {
                    val parts = line.split("\t")
                    if (parts.size >= 2) {
                        val simplified = parts[0].trim()
                        val traditional = parts[1].trim()
                        entries.add(PhraseEntry(simplified, traditional))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return entries
    }
    
    /**
     * 更新繁简字词库
     */
    fun updateCharacterDictionary(entries: List<CharacterEntry>): Boolean {
        return try {
            val content = entries.joinToString("\n") { entry ->
                "${entry.simplified}\t${entry.traditional}"
            }
            saveToExternalFile("STCharacters.txt", content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 更新繁简词组词库
     */
    fun updatePhraseDictionary(entries: List<PhraseEntry>): Boolean {
        return try {
            val content = entries.joinToString("\n") { entry ->
                "${entry.simplified}\t${entry.traditional}"
            }
            saveToExternalFile("STPhrases.txt", content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 添加繁简字条目
     */
    fun addCharacterEntry(simplified: String, traditional: String): Boolean {
        val entries = loadCharacterDictionary().toMutableList()
        entries.add(CharacterEntry(simplified, traditional))
        return updateCharacterDictionary(entries)
    }
    
    /**
     * 添加繁简词组条目
     */
    fun addPhraseEntry(simplified: String, traditional: String): Boolean {
        val entries = loadPhraseDictionary().toMutableList()
        entries.add(PhraseEntry(simplified, traditional))
        return updatePhraseDictionary(entries)
    }
    
    /**
     * 删除繁简字条目
     */
    fun deleteCharacterEntry(simplified: String): Boolean {
        val entries = loadCharacterDictionary().filter { it.simplified != simplified }
        return updateCharacterDictionary(entries)
    }
    
    /**
     * 删除繁简词组条目
     */
    fun deletePhraseEntry(simplified: String): Boolean {
        val entries = loadPhraseDictionary().filter { it.simplified != simplified }
        return updatePhraseDictionary(entries)
    }
    
    /**
     * 搜索繁简字
     */
    fun searchCharacter(query: String): List<CharacterEntry> {
        return loadCharacterDictionary().filter { 
            it.simplified.contains(query) || it.traditional.contains(query) 
        }
    }
    
    /**
     * 搜索繁简词组
     */
    fun searchPhrase(query: String): List<PhraseEntry> {
        return loadPhraseDictionary().filter { 
            it.simplified.contains(query) || it.traditional.contains(query) 
        }
    }
    
    /**
     * 获取词库统计信息
     */
    fun getDictionaryStatistics(): Map<String, Int> {
        return mapOf(
            "繁简字数量" to loadCharacterDictionary().size,
            "繁简词组数量" to loadPhraseDictionary().size
        )
    }
    
    /**
     * 保存到外部文件
     */
    private fun saveToExternalFile(fileName: String, content: String): Boolean {
        return try {
            val externalDir = context.getExternalFilesDir("rime/opencc")
            if (externalDir != null && !externalDir.exists()) {
                externalDir.mkdirs()
            }
            
            val file = File(externalDir, fileName)
            file.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
