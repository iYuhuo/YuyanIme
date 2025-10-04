package com.yuyan.imemodule.voice

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 语音识别模型信息
 */
data class VoiceModel(
    val id: String,                    // 模型唯一ID
    val name: String,                  // 模型显示名称
    val language: String,              // 语言（zh, en等）
    val modelType: String,             // 模型类型（zipformer, paraformer等）
    val modelDir: String,              // 模型目录（相对于models目录或assets）
    val isBuiltIn: Boolean = false,    // 是否内置模型
    val sampleRate: Int = 16000,      // 采样率
    val description: String = "",      // 模型描述
    val size: Long = 0,                // 模型大小（字节）
    val useInt8: Boolean = true        // 是否使用int8量化版本（优先选择）
) {
    /**
     * 获取模型配置路径
     */
    fun getEncoderPath(): String = "$modelDir/encoder-epoch-99-avg-1.onnx"
    fun getDecoderPath(): String = "$modelDir/decoder-epoch-99-avg-1.onnx"
    fun getJoinerPath(): String = "$modelDir/joiner-epoch-99-avg-1.onnx"
    fun getTokensPath(): String = "$modelDir/tokens.txt"
    
    /**
     * 动态获取模型文件路径
     */
    fun getEncoderPath(context: Context): String {
        val path = if (isBuiltIn) {
            getEncoderPath()
        } else {
            findModelFile(context, "encoder") ?: getEncoderPath()
        }
        Log.i("VoiceModel", "获取Encoder路径: $path")
        return path
    }
    
    fun getDecoderPath(context: Context): String {
        val path = if (isBuiltIn) {
            getDecoderPath()
        } else {
            findModelFile(context, "decoder") ?: getDecoderPath()
        }
        Log.i("VoiceModel", "获取Decoder路径: $path")
        return path
    }
    
    fun getJoinerPath(context: Context): String {
        val path = if (isBuiltIn) {
            getJoinerPath()
        } else {
            findModelFile(context, "joiner") ?: getJoinerPath()
        }
        Log.i("VoiceModel", "获取Joiner路径: $path")
        return path
    }
    
    fun getTokensPath(context: Context): String {
        val path = if (isBuiltIn) {
            getTokensPath()
        } else {
            findModelFile(context, "tokens") ?: getTokensPath()
        }
        Log.i("VoiceModel", "获取Tokens路径: $path")
        return path
    }
    
    /**
     * 查找模型文件
     */
    private fun findModelFile(context: Context, prefix: String): String? {
        val modelsDir = File(context.filesDir, "models")
        val modelDir = File(modelsDir, this.modelDir)
        
        if (!modelDir.exists()) {
            Log.w("VoiceModel", "模型目录不存在: ${modelDir.absolutePath}")
            return null
        }
        
        val files = modelDir.listFiles() ?: return null
        
        val fileName = when (prefix) {
            "encoder" -> {
                if (useInt8) {
                    // 优先使用int8版本，如果没有则使用普通版本
                    files.find { it.name.startsWith("encoder") && it.name.contains("int8") && it.name.endsWith(".onnx") }?.name
                        ?: files.find { it.name.startsWith("encoder") && !it.name.contains("int8") && it.name.endsWith(".onnx") }?.name
                } else {
                    // 优先使用普通版本，如果没有则使用int8版本
                    files.find { it.name.startsWith("encoder") && !it.name.contains("int8") && it.name.endsWith(".onnx") }?.name
                        ?: files.find { it.name.startsWith("encoder") && it.name.contains("int8") && it.name.endsWith(".onnx") }?.name
                }
            }
            "decoder" -> {
                if (useInt8) {
                    files.find { it.name.startsWith("decoder") && it.name.contains("int8") && it.name.endsWith(".onnx") }?.name
                        ?: files.find { it.name.startsWith("decoder") && !it.name.contains("int8") && it.name.endsWith(".onnx") }?.name
                } else {
                    files.find { it.name.startsWith("decoder") && !it.name.contains("int8") && it.name.endsWith(".onnx") }?.name
                        ?: files.find { it.name.startsWith("decoder") && it.name.contains("int8") && it.name.endsWith(".onnx") }?.name
                }
            }
            "joiner" -> {
                if (useInt8) {
                    files.find { it.name.startsWith("joiner") && it.name.contains("int8") && it.name.endsWith(".onnx") }?.name
                        ?: files.find { it.name.startsWith("joiner") && !it.name.contains("int8") && it.name.endsWith(".onnx") }?.name
                } else {
                    files.find { it.name.startsWith("joiner") && !it.name.contains("int8") && it.name.endsWith(".onnx") }?.name
                        ?: files.find { it.name.startsWith("joiner") && it.name.contains("int8") && it.name.endsWith(".onnx") }?.name
                }
            }
            "tokens" -> files.find { it.name == "tokens.txt" }?.name
            else -> null
        }
        
        if (fileName != null) {
            val fullPath = "${modelDir.absolutePath}/$fileName"
            Log.i("VoiceModel", "找到模型文件 ($prefix, int8=$useInt8): $fullPath")
            return fullPath
        } else {
            Log.w("VoiceModel", "未找到模型文件: $prefix (int8=$useInt8)")
            return null
        }
    }
    
    /**
     * 检查模型文件是否存在
     */
    fun isValid(context: Context): Boolean {
        return if (isBuiltIn) {
            // 检查assets中的文件
            try {
                context.assets.open(getEncoderPath()).close()
                context.assets.open(getDecoderPath()).close()
                context.assets.open(getJoinerPath()).close()
                context.assets.open(getTokensPath()).close()
                true
            } catch (e: Exception) {
                false
            }
        } else {
            // 检查外部存储中的文件
            val modelsDir = File(context.filesDir, "models")
            val modelDir = File(modelsDir, this.modelDir)
            
            // 检查目录是否存在
            if (!modelDir.exists()) {
                Log.w("VoiceModel", "模型目录不存在: ${modelDir.absolutePath}")
                return false
            }
            
            // 查找模型文件（支持不同的文件名）
            val files = modelDir.listFiles()
            if (files == null || files.isEmpty()) {
                Log.w("VoiceModel", "模型目录为空: ${modelDir.absolutePath}")
                return false
            }
            
            // 检查是否有必要的文件类型
            val hasOnnxFiles = files.any { it.name.endsWith(".onnx") }
            val hasTokensFile = files.any { it.name == "tokens.txt" }
            
            Log.i("VoiceModel", "模型文件检查: onnx文件=${hasOnnxFiles}, tokens文件=${hasTokensFile}")
            Log.i("VoiceModel", "找到的文件: ${files.map { it.name }}")
            
            hasOnnxFiles && hasTokensFile
        }
    }
    
    /**
     * 转换为JSON
     */
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("language", language)
            put("modelType", modelType)
            put("modelDir", modelDir)
            put("isBuiltIn", isBuiltIn)
            put("sampleRate", sampleRate)
            put("description", description)
            put("size", size)
            put("useInt8", useInt8)
        }
    }
    
    companion object {
        /**
         * 从JSON创建
         */
        fun fromJson(json: JSONObject): VoiceModel {
            return VoiceModel(
                id = json.getString("id"),
                name = json.getString("name"),
                language = json.getString("language"),
                modelType = json.getString("modelType"),
                modelDir = json.getString("modelDir"),
                isBuiltIn = json.optBoolean("isBuiltIn", false),
                sampleRate = json.optInt("sampleRate", 16000),
                description = json.optString("description", ""),
                size = json.optLong("size", 0),
                useInt8 = json.optBoolean("useInt8", true)
            )
        }
        
        /**
         * 内置中文模型
         */
        fun getBuiltInChineseModel(): VoiceModel {
            return VoiceModel(
                id = "builtin_zh",
                name = "中文离线模型（内置）",
                language = "zh",
                modelType = "zipformer",
                modelDir = "sherpa-onnx-streaming-zh",
                isBuiltIn = true,
                description = "内置的中文语音识别模型"
            )
        }
    }
}


/**
 * 模型管理器
 */
class VoiceModelManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "VoiceModelManager"
        private const val MODELS_FILE = "voice_models.json"
        private const val MODELS_DIR = "models"
        
        @Volatile
        private var instance: VoiceModelManager? = null
        
        fun getInstance(context: Context): VoiceModelManager {
            return instance ?: synchronized(this) {
                instance ?: VoiceModelManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val modelsFile = File(context.filesDir, MODELS_FILE)
    private val modelsDir = File(context.filesDir, MODELS_DIR)
    private val models = mutableListOf<VoiceModel>()
    
    init {
        // 确保目录存在
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
        }
        // 加载模型列表
        loadModels()
    }
    
    /**
     * 加载模型列表
     */
    private fun loadModels() {
        models.clear()
        
        // 添加内置模型
        models.add(VoiceModel.getBuiltInChineseModel())
        
        // 从文件加载自定义模型
        if (modelsFile.exists()) {
            try {
                val json = modelsFile.readText()
                val jsonArray = JSONArray(json)
                for (i in 0 until jsonArray.length()) {
                    val model = VoiceModel.fromJson(jsonArray.getJSONObject(i))
                    Log.i(TAG, "正在验证模型: ${model.name}, ID: ${model.id}, 目录: ${model.modelDir}")
                    val isValid = model.isValid(context)
                    Log.i(TAG, "模型 ${model.name} 验证结果: $isValid")
                    if (isValid) {
                        models.add(model)
                        Log.i(TAG, "模型已添加: ${model.name}")
                    } else {
                        Log.w(TAG, "模型无效，已跳过: ${model.name}")
                    }
                }
                Log.i(TAG, "总共加载了 ${models.size} 个模型（包括内置模型）")
            } catch (e: Exception) {
                Log.e(TAG, "加载模型列表失败", e)
            }
        }
    }
    
    /**
     * 保存模型列表
     */
    private fun saveModels() {
        try {
            val jsonArray = JSONArray()
            models.filter { !it.isBuiltIn }.forEach {
                jsonArray.put(it.toJson())
            }
            modelsFile.writeText(jsonArray.toString(2))
            Log.i(TAG, "保存了 ${models.size} 个模型")
        } catch (e: Exception) {
            Log.e(TAG, "保存模型列表失败", e)
        }
    }
    
    /**
     * 获取所有模型
     */
    fun getAllModels(): List<VoiceModel> {
        return models.toList()
    }
    
    /**
     * 根据ID获取模型
     */
    fun getModelById(id: String): VoiceModel? {
        return models.find { it.id == id }
    }
    
    /**
     * 添加模型
     */
    fun addModel(model: VoiceModel): Boolean {
        return try {
            if (models.any { it.id == model.id }) {
                Log.w(TAG, "模型ID已存在: ${model.id}")
                return false
            }
            models.add(model)
            saveModels()
            true
        } catch (e: Exception) {
            Log.e(TAG, "添加模型失败", e)
            false
        }
    }
    
    /**
     * 删除模型
     */
    fun deleteModel(id: String): Boolean {
        return try {
            val model = models.find { it.id == id } ?: return false
            if (model.isBuiltIn) {
                Log.w(TAG, "不能删除内置模型")
                return false
            }
            
            // 删除模型文件
            val modelDir = File(modelsDir, model.modelDir)
            if (modelDir.exists()) {
                modelDir.deleteRecursively()
            }
            
            // 从列表中移除
            models.remove(model)
            saveModels()
            
            Log.i(TAG, "删除模型成功: ${model.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "删除模型失败", e)
            false
        }
    }
    
    /**
     * 导入模型从ZIP文件
     */
    fun importModelFromZip(zipFile: File, modelInfo: VoiceModel): Boolean {
        return try {
            Log.i(TAG, "开始导入模型: ${modelInfo.name}")
            Log.i(TAG, "ZIP文件: ${zipFile.absolutePath}, 大小: ${zipFile.length()} bytes")
            
            val targetDir = File(modelsDir, modelInfo.modelDir)
            Log.i(TAG, "目标目录: ${targetDir.absolutePath}")
            
            if (targetDir.exists()) {
                Log.i(TAG, "删除现有目录")
                targetDir.deleteRecursively()
            }
            
            if (!targetDir.mkdirs()) {
                Log.e(TAG, "无法创建目标目录: ${targetDir.absolutePath}")
                return false
            }
            
            // 解压ZIP文件
            Log.i(TAG, "开始解压ZIP文件")
            unzipFile(zipFile, targetDir)
            Log.i(TAG, "ZIP文件解压完成")
            
            // 验证模型文件
            Log.i(TAG, "开始验证模型文件")
            val isValid = modelInfo.isValid(context)
            Log.i(TAG, "模型文件验证结果: $isValid")
            
            if (!isValid) {
                Log.e(TAG, "导入的模型文件不完整，删除目录")
                targetDir.deleteRecursively()
                return false
            }
            
            // 添加到模型列表
            Log.i(TAG, "添加模型到列表")
            addModel(modelInfo)
            
            Log.i(TAG, "导入模型成功: ${modelInfo.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "导入模型失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 解压文件
     */
    private fun unzipFile(zipFile: File, targetDir: File) {
        if (!zipFile.exists()) {
            throw Exception("ZIP文件不存在: ${zipFile.absolutePath}")
        }
        
        if (zipFile.length() == 0L) {
            throw Exception("ZIP文件为空")
        }
        
        // 使用ZipFile来更好地处理ZIP结构
        java.util.zip.ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            var extractedFiles = 0
            var hasSubDir = false
            var subDirName = ""
            
            // 第一遍：检查是否有子目录
            val entryList = entries.toList()
            for (entry in entryList) {
                if (!entry.isDirectory && entry.name.contains("/")) {
                    val firstSlash = entry.name.indexOf("/")
                    val potentialSubDir = entry.name.substring(0, firstSlash)
                    if (subDirName.isEmpty()) {
                        subDirName = potentialSubDir
                    }
                    if (subDirName == potentialSubDir) {
                        hasSubDir = true
                    }
                }
            }
            
            Log.i(TAG, "检测到ZIP结构: 有子目录=$hasSubDir, 子目录名=$subDirName")
            
            // 第二遍：解压文件
            for (entry in entryList) {
                val entryFile = if (hasSubDir && entry.name.startsWith("$subDirName/")) {
                    // 如果有子目录，去掉子目录前缀
                    val relativePath = entry.name.substring(subDirName.length + 1)
                    File(targetDir, relativePath)
                } else {
                    File(targetDir, entry.name)
                }
                
                // 安全检查：防止ZIP炸弹攻击
                if (!entryFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    Log.w(TAG, "跳过不安全的文件路径: ${entry.name}")
                    continue
                }
                
                if (entry.isDirectory) {
                    if (!entryFile.exists() && !entryFile.mkdirs()) {
                        throw Exception("无法创建目录: ${entryFile.absolutePath}")
                    }
                } else {
                    entryFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        entryFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                        }
                    }
                    extractedFiles++
                }
            }
            
            if (extractedFiles == 0) {
                throw Exception("ZIP文件中没有找到任何文件")
            }
            
            Log.i(TAG, "解压完成，共解压 $extractedFiles 个文件")
        }
    }
    
    /**
     * 获取模型目录大小
     */
    fun getModelSize(modelDir: String): Long {
        val dir = File(modelsDir, modelDir)
        return if (dir.exists()) {
            dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        } else {
            0
        }
    }
    
    /**
     * 更新模型配置
     */
    fun updateModel(modelId: String, updater: (VoiceModel) -> VoiceModel): Boolean {
        return try {
            val index = models.indexOfFirst { it.id == modelId }
            if (index == -1) {
                Log.w(TAG, "未找到模型: $modelId")
                return false
            }
            
            val oldModel = models[index]
            val newModel = updater(oldModel)
            models[index] = newModel
            
            saveModels()
            Log.i(TAG, "模型配置已更新: ${newModel.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "更新模型配置失败", e)
            false
        }
    }
}

