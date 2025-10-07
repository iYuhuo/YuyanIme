package com.yuyan.imemodule.voice

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*
import java.io.File

/**
 * Sherpa-ONNX 语音识别实现
 * 
 * 基于 https://github.com/k2-fsa/sherpa-onnx
 * 支持离线流式语音识别，使用 next-gen Kaldi
 */
class SherpaOnnxRecognizer(private val context: Context) {
    
    companion object {
        private const val TAG = "SherpaOnnxRecognizer"
        private const val SAMPLE_RATE = 16000
    }
    
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var callback: ((String) -> Unit)? = null
    private var currentModel: VoiceModel? = null
    
    /**
     * 初始化 Sherpa-ONNX（使用默认模型）
     */
    fun initialize(): Boolean {
        val modelManager = VoiceModelManager.getInstance(context)
        val defaultModel = VoiceModel.getBuiltInChineseModel()
        return initialize(defaultModel)
    }
    
    /**
     * 使用指定模型初始化 Sherpa-ONNX（异步版本）
     */
    fun initializeAsync(model: VoiceModel, callback: ((Boolean) -> Unit)? = null) {
        Thread {
            val success = initialize(model)
            callback?.invoke(success)
        }.start()
    }

    /**
     * 使用指定模型初始化 Sherpa-ONNX
     */
    fun initialize(model: VoiceModel): Boolean {
        return try {
            Log.i(TAG, "开始初始化 Sherpa-ONNX，模型: ${model.name}")

            // 如果已初始化，先释放
            if (recognizer != null) {
                release()
                // 等待资源完全释放，避免访问已销毁的互斥锁
                Thread.sleep(50) // 减少等待时间
                // 强制垃圾回收
                System.gc()
                Thread.sleep(50)
            }
            
            // 对于自定义模型，使用空字符串让sherpa-onnx自动检测modelType
            val actualModelType = if (model.isBuiltIn) model.modelType else ""

            // 检查模型大小，如果是大模型则使用更少的线程以减少内存压力
            val modelSize = model.size
            val numThreads = when {
                modelSize > 100 * 1024 * 1024 -> 1  // 大于100MB，使用单线程
                modelSize > 50 * 1024 * 1024 -> 2   // 大于50MB，使用双线程
                else -> 2                             // 普通模型使用双线程
            }

            Log.i(TAG, "模型大小: ${modelSize / (1024 * 1024)}MB, 使用线程数: $numThreads")

            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = model.sampleRate,
                    featureDim = 80
                ),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = model.getEncoderPath(context),
                        decoder = model.getDecoderPath(context),
                        joiner = model.getJoinerPath(context)
                    ),
                    tokens = model.getTokensPath(context),
                    numThreads = numThreads,
                    provider = "cpu",
                    debug = false,  // 生产环境关闭debug减少日志输出
                    modelType = actualModelType
                ),
                endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(false, 2.4f, 0.0f),
                    rule2 = EndpointRule(false, 1.2f, 0.0f),
                    rule3 = EndpointRule(false, 0.0f, 20.0f)
                ),
                decodingMethod = "greedy_search",
                maxActivePaths = 4,
                enableEndpoint = true
            )
            
            Log.i(TAG, "使用 modelType: '$actualModelType' (自定义模型使用空字符串自动检测)")
            
            // 检查外部模型文件是否存在
            if (!model.isBuiltIn) {
                Log.i(TAG, "使用自定义模型路径")
                Log.i(TAG, "Encoder: ${model.getEncoderPath(context)}")
                Log.i(TAG, "Decoder: ${model.getDecoderPath(context)}")
                Log.i(TAG, "Joiner: ${model.getJoinerPath(context)}")
                Log.i(TAG, "Tokens: ${model.getTokensPath(context)}")
                
                val encoderFile = File(model.getEncoderPath(context))
                val decoderFile = File(model.getDecoderPath(context))
                val joinerFile = File(model.getJoinerPath(context))
                val tokensFile = File(model.getTokensPath(context))
                
                Log.i(TAG, "文件存在检查:")
                Log.i(TAG, "  Encoder: ${encoderFile.exists()} (${encoderFile.length()} bytes)")
                Log.i(TAG, "  Decoder: ${decoderFile.exists()} (${decoderFile.length()} bytes)")
                Log.i(TAG, "  Joiner: ${joinerFile.exists()} (${joinerFile.length()} bytes)")
                Log.i(TAG, "  Tokens: ${tokensFile.exists()} (${tokensFile.length()} bytes)")
                
                if (!encoderFile.exists() || !decoderFile.exists() || !joinerFile.exists() || !tokensFile.exists()) {
                    Log.e(TAG, "模型文件不存在，初始化失败")
                    return false
                }
            }
            
            // 根据模型类型选择加载方式
            try {
                recognizer = if (model.isBuiltIn) {
                    // 内置模型：使用 AssetManager
                    OnlineRecognizer(
                        assetManager = context.assets,
                        config = config
                    )
                } else {
                    // 外部模型：直接使用文件路径创建
                    Log.i(TAG, "正在加载自定义模型，这可能需要几秒钟...")
                    OnlineRecognizer(config = config)
                }
            } catch (e: Exception) {
                Log.e(TAG, "创建 OnlineRecognizer 失败", e)
                recognizer = null
                currentModel = null
                return false
            }
            
            // 验证recognizer是否成功创建
            if (recognizer == null) {
                Log.e(TAG, "Recognizer 创建失败")
                currentModel = null
                return false
            }
            
            currentModel = model
            Log.i(TAG, "Sherpa-ONNX 初始化成功，当前模型: ${model.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Sherpa-ONNX 初始化失败", e)
            recognizer = null
            currentModel = null
            false
        }
    }
    
    /**
     * 获取当前模型
     */
    fun getCurrentModel(): VoiceModel? {
        return currentModel
    }
    
    /**
     * 开始识别
     */
    fun startRecognition(callback: (String) -> Unit) {
        this.callback = callback
        try {
            // 创建新的音频流
            stream?.release()
            stream = recognizer?.createStream()
            Log.d(TAG, "开始语音识别会话")
        } catch (e: Exception) {
            Log.e(TAG, "创建音频流失败", e)
            callback("创建音频流失败: ${e.message}")
        }
    }
    
    /**
     * 停止识别
     */
    fun stopRecognition(): String {
        return try {
            stream?.inputFinished()
            val result = stream?.let { recognizer?.getResult(it) }
            val text = result?.text ?: ""
            Log.d(TAG, "语音识别完成: $text")
            
            // 重置流以便下次使用
            stream?.let { recognizer?.reset(it) }
            
            text
        } catch (e: Exception) {
            Log.e(TAG, "停止识别失败", e)
            "识别失败: ${e.message}"
        }
    }
    
    /**
     * 处理音频数据（流式识别）
     */
    fun processAudio(audioData: FloatArray): String {
        return try {
            stream?.acceptWaveform(audioData, SAMPLE_RATE)
            
            // 获取部分识别结果
            val isReady = stream?.let { recognizer?.isReady(it) } ?: false
            if (isReady) {
                recognizer?.decode(stream!!)
                val result = stream?.let { recognizer?.getResult(it) }
                result?.text ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理音频失败", e)
            ""
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        try {
            stream?.release()
            stream = null
            recognizer?.release()
            recognizer = null
            Log.d(TAG, "释放 Sherpa-ONNX 资源")
        } catch (e: Exception) {
            Log.e(TAG, "释放资源失败", e)
        } finally {
            // 确保引用都被清空
            stream = null
            recognizer = null
            callback = null
            currentModel = null
        }
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean {
        return recognizer != null
    }
    
    /**
     * 转换字节数组为浮点数组
     */
    fun convertBytesToFloat(data: ByteArray): FloatArray {
        val floatArray = FloatArray(data.size / 2)
        for (i in floatArray.indices) {
            val sample = ((data[i * 2 + 1].toInt() shl 8) or (data[i * 2].toInt() and 0xFF)).toShort()
            floatArray[i] = sample / 32768.0f
        }
        return floatArray
    }
    
    /**
     * 流式识别
     */
    fun recognizeStreaming(floatData: FloatArray): String {
        return processAudio(floatData)
    }
    
    /**
     * 检查是否到达端点
     */
    fun isEndpoint(): Boolean {
        return try {
            recognizer?.isEndpoint(stream!!) ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 完成识别
     */
    fun finishRecognition(): String {
        return stopRecognition()
    }
}
