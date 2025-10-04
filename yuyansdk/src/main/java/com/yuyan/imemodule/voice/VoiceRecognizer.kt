package com.yuyan.imemodule.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 离线语音识别器
 * 
 * 集成语音识别功能，支持中文语音转文字
 */
class VoiceRecognizer(private val context: Context) {
    
    companion object {
        private const val TAG = "VoiceRecognizer"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        private var instance: VoiceRecognizer? = null
        
        fun getInstance(context: Context): VoiceRecognizer {
            if (instance == null) {
                instance = VoiceRecognizer(context.applicationContext)
            }
            return instance!!
        }
    }
    
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false
    @Volatile
    private var recordingThread: Thread? = null
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    )
    
    private var recognitionListener: RecognitionListener? = null
    
    // 当前活跃的Sherpa-ONNX 识别器
    private var sherpaRecognizer = SherpaOnnxRecognizer(context)
    private var isModelReady = false
    
    interface RecognitionListener {
        fun onRecordingStart()
        fun onRecordingStop()
        fun onRecognitionResult(text: String)
        fun onRecognitionError(error: String)
        fun onPartialResult(text: String)  // 流式输出部分结果
        fun onVolumeChanged(volume: Float)  // 音量变化回调
    }
    
    /**
     * 设置识别监听器
     * @param listener 监听器，传入null可清除监听器
     */
    fun setRecognitionListener(listener: RecognitionListener?) {
        this.recognitionListener = listener
    }
    
    /**
     * 初始化语音识别引擎
     */
    fun initialize(): Boolean {
        return try {
            // 获取用户选择的模型
            val prefs = com.yuyan.imemodule.prefs.AppPrefs.getInstance()
            val modelId = prefs.internal.voiceModelId.getValue()
            val modelManager = VoiceModelManager.getInstance(context)
            val model = modelManager.getModelById(modelId) ?: VoiceModel.getBuiltInChineseModel()
            
            Log.d(TAG, "开始初始化语音识别，模型: ${model.name}, 大小: ${model.size} bytes, 是否内置: ${model.isBuiltIn}")
            
            // 根据模型大小选择初始化策略
            val success = if (model.size > 50 * 1024 * 1024) { // 大于50MB的模型
                Log.d(TAG, "检测到大模型，开始异步初始化: ${model.name}")
                
                // 大模型异步初始化，避免阻塞
                sherpaRecognizer.initializeAsync(model) { initSuccess ->
                    if (initSuccess) {
                        isModelReady = true
                        Log.d(TAG, "✓ 大模型异步初始化完成: ${model.name}")
                    } else {
                        isModelReady = false
                        Log.e(TAG, "✗ 大模型异步初始化失败: ${model.name}")
                    }
                }
                
                // 对于大模型，立即返回true表示初始化请求已接受
                true
            } else {
                // 小模型同步初始化
                Log.d(TAG, "小模型同步初始化: ${model.name}")
                val initSuccess = sherpaRecognizer.initialize(model)
                Log.d(TAG, "初始化结果: $initSuccess, recognizer已创建: ${sherpaRecognizer.isInitialized()}")
                
                if (initSuccess) {
                    isModelReady = true
                    Log.d(TAG, "✓ Sherpa-ONNX initialized successfully with model: ${model.name}")
                } else {
                    isModelReady = false
                    Log.e(TAG, "✗ Sherpa-ONNX initialization failed for model: ${model.name}")
                }
                initSuccess
            }
            
            Log.d(TAG, "初始化完成，success=$success, isModelReady=$isModelReady, isInitialized=${sherpaRecognizer.isInitialized()}")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize VoiceRecognizer", e)
            isModelReady = false
            false
        }
    }
    
    /**
     * 切换模型并重新初始化
     */
    fun switchModel(modelId: String): Boolean {
        return try {
            val modelManager = VoiceModelManager.getInstance(context)
            val model = modelManager.getModelById(modelId) ?: return false

            Log.d(TAG, "开始切换模型: ${model.name}")

            // 释放当前识别器
            sherpaRecognizer.release()
            isModelReady = false

            // 根据模型大小选择初始化策略
            val success = if (model.size > 50 * 1024 * 1024) { // 大于50MB的模型
                Log.d(TAG, "检测到大模型，开始异步初始化: ${model.name}")

                // 大模型异步初始化，避免阻塞
                sherpaRecognizer.initializeAsync(model) { initSuccess ->
                    if (initSuccess) {
                        isModelReady = true
                        Log.d(TAG, "大模型异步初始化完成: ${model.name}")
                    } else {
                        Log.w(TAG, "大模型异步初始化失败: ${model.name}")
                    }
                }

                // 对于大模型，立即返回true表示切换请求已接受
                // 实际是否准备好需要通过isModelReady()检查
                true
            } else {
                // 小模型同步初始化
                val initSuccess = sherpaRecognizer.initialize(model)
                if (initSuccess) {
                    isModelReady = true
                    Log.d(TAG, "切换到模型: ${model.name}")
                }
                initSuccess
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "切换模型失败", e)
            false
        }
    }

    /**
     * 检查模型是否已准备好
     */
    fun isModelReady(): Boolean {
        return isModelReady && sherpaRecognizer.isInitialized()
    }
    
    /**
     * 检查识别器是否已初始化（不管模型是否准备好）
     */
    fun isInitialized(): Boolean {
        return sherpaRecognizer.isInitialized()
    }
    
    /**
     * 开始录音识别
     */
    @Synchronized
    fun startRecognition() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return
        }

        // 检查模型是否准备好
        if (!isModelReady()) {
            Log.w(TAG, "模型尚未准备好，拒绝开始录音")
            recognitionListener?.onRecognitionError("语音模型正在加载中，请稍后重试")
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                recognitionListener?.onRecognitionError("麦克风初始化失败")
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            recognitionListener?.onRecordingStart()

            // 启动 Sherpa-ONNX 识别会话
            sherpaRecognizer.startRecognition { result ->
                // 处理识别结果
                Log.d(TAG, "Recognition result: $result")
            }

            recordingThread = Thread {
                processAudioData()
            }.apply {
                start()
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Microphone permission denied", e)
            recognitionListener?.onRecognitionError("没有麦克风权限")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            recognitionListener?.onRecognitionError("录音启动失败")
        }
    }
    
    /**
     * 停止录音识别
     */
    @Synchronized
    fun stopRecognition() {
        if (!isRecording) {
            return
        }

        isRecording = false
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            recordingThread?.join(1000)
            recordingThread = null
            
            recognitionListener?.onRecordingStop()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
        }
    }
    
    /**
     * 处理音频数据并进行识别
     * 支持流式识别，边录音边输出结果，使用 Sherpa-ONNX
     */
    private fun processAudioData() {
        try {
            val buffer = ByteArray(bufferSize)
            var lastPartialText = ""
            
            // 预热期：丢弃前几帧音频，给 AudioRecord 预热时间（约 200-300ms）
            val warmupFrames = 3
            repeat(warmupFrames) {
                audioRecord?.read(buffer, 0, bufferSize)
            }
            
            Log.d(TAG, "音频预热完成，开始识别")
            
            while (isRecording) {
                val readSize = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                
                if (readSize > 0) {
                    // 复制数据
                    val data = buffer.copyOf(readSize)
                    
                    // 转换为 Float 格式
                    val floatData = sherpaRecognizer.convertBytesToFloat(data)
                    
                    // 计算音量（RMS - 均方根）
                    val volume = calculateVolume(floatData)
                    recognitionListener?.onVolumeChanged(volume)
                    
                    // 使用 Sherpa-ONNX 进行流式识别
                    val partialText = sherpaRecognizer.recognizeStreaming(floatData)
                    
                    // 只有当识别结果变化时才回调
                    if (partialText != lastPartialText && partialText.isNotEmpty()) {
                        lastPartialText = partialText
                        recognitionListener?.onPartialResult(partialText)
                    }
                    
                    // 检测端点（用户停止说话）
                    if (sherpaRecognizer.isEndpoint()) {
                        // 可以选择自动停止录音
                        // break
                    }
                }
            }
            
            // 录音结束，获取最终识别结果
            val finalText = sherpaRecognizer.finishRecognition()
            Log.d(TAG, "语音识别完成: $finalText")
            recognitionListener?.onRecognitionResult(finalText)
        } catch (e: Exception) {
            Log.e(TAG, "处理音频数据时出错", e)
            recognitionListener?.onRecognitionError("语音识别出错: ${e.message}")
        }
    }
    
    /**
     * 计算音频音量（使用 RMS 均方根）
     * @return 归一化的音量值 (0.0 - 1.0)
     */
    private fun calculateVolume(audioData: FloatArray): Float {
        if (audioData.isEmpty()) return 0f
        
        var sum = 0.0
        for (sample in audioData) {
            sum += (sample * sample).toDouble()
        }
        val rms = kotlin.math.sqrt(sum / audioData.size)
        
        // 将 RMS 值归一化到 0-1 范围
        // 通常语音的 RMS 在 0.01-0.3 之间，这里做一个映射
        val normalizedVolume = (rms * 3.0).coerceIn(0.0, 1.0).toFloat()
        return normalizedVolume
    }
    
    /**
     * 识别完整的音频
     */
    private fun recognizeAudio(audioData: List<ByteArray>) {
        try {
            // TODO: 调用离线语音识别引擎
            // 这里应该使用加载的模型进行识别
            
            // 示例：将音频数据合并
            val totalSize = audioData.sumOf { it.size }
            val combinedAudio = ByteArray(totalSize)
            var offset = 0
            for (chunk in audioData) {
                System.arraycopy(chunk, 0, combinedAudio, offset, chunk.size)
                offset += chunk.size
            }
            
            // 调用识别引擎 (存根实现)
            val recognizedText = performRecognition(combinedAudio)
            
            recognitionListener?.onRecognitionResult(recognizedText)
            
        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed", e)
            recognitionListener?.onRecognitionError("识别失败")
        }
    }
    
    
    /**
     * 释放资源
     */
    fun release() {
        stopRecognition()
        sherpaRecognizer.release()
        instance = null
    }
    
    /**
     * 检查麦克风权限
     */
    fun checkPermission(): Boolean {
        return try {
            val testRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            val hasPermission = testRecord.state == AudioRecord.STATE_INITIALIZED
            testRecord.release()
            hasPermission
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 执行语音识别
     */
    private fun performRecognition(audioData: ByteArray): String {
        return if (sherpaRecognizer.isInitialized()) {
            // 使用Sherpa-ONNX进行识别
            try {
                Log.d(TAG, "使用Sherpa-ONNX进行语音识别，音频数据大小: ${audioData.size}")
                sherpaRecognizer.finishRecognition()
            } catch (e: Exception) {
                Log.e(TAG, "Sherpa-ONNX识别失败", e)
                "语音识别出错: ${e.message}"
            }
        } else {
            Log.w(TAG, "Sherpa-ONNX未初始化")
            "语音识别功能暂不可用 - 请检查模型文件和库文件"
        }
    }
}

