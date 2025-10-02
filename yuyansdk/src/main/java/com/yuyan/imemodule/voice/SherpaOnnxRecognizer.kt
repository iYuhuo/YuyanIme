package com.yuyan.imemodule.voice

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.*

class SherpaOnnxRecognizer(private val context: Context) {
    
    companion object {
        private const val TAG = "SherpaOnnxRecognizer"
        private const val MODEL_DIR = "sherpa-onnx-streaming-zh"
        private const val SAMPLE_RATE = 16000
    }
    
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var callback: ((String) -> Unit)? = null
    
    
    fun initialize(): Boolean {
        return try {
            Log.i(TAG, "开始初始化 Sherpa-ONNX")
            
            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = SAMPLE_RATE,
                    featureDim = 80
                ),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = "$MODEL_DIR/encoder-epoch-99-avg-1.onnx",
                        decoder = "$MODEL_DIR/decoder-epoch-99-avg-1.onnx",
                        joiner = "$MODEL_DIR/joiner-epoch-99-avg-1.onnx"
                    ),
                    tokens = "$MODEL_DIR/tokens.txt",
                    numThreads = 2,
                    provider = "cpu",
                    debug = false,
                    modelType = "zipformer"
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
            
            recognizer = OnlineRecognizer(
                assetManager = context.assets,
                config = config
            )
            
            Log.i(TAG, "Sherpa-ONNX 初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Sherpa-ONNX 初始化失败", e)
            false
        }
    }
    
    
    fun startRecognition(callback: (String) -> Unit) {
        this.callback = callback
        try {
            stream?.release()
            stream = recognizer?.createStream()
            Log.d(TAG, "开始语音识别会话")
        } catch (e: Exception) {
            Log.e(TAG, "创建音频流失败", e)
            callback("创建音频流失败: ${e.message}")
        }
    }
    
    
    fun stopRecognition(): String {
        return try {
            stream?.inputFinished()
            val result = stream?.let { recognizer?.getResult(it) }
            val text = result?.text ?: ""
            Log.d(TAG, "语音识别完成: $text")
            
            stream?.let { recognizer?.reset(it) }
            
            text
        } catch (e: Exception) {
            Log.e(TAG, "停止识别失败", e)
            "识别失败: ${e.message}"
        }
    }
    
    
    fun processAudio(audioData: FloatArray): String {
        return try {
            stream?.acceptWaveform(audioData, SAMPLE_RATE)
            
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
    
    
    fun release() {
        try {
            stream?.release()
            recognizer?.release()
            Log.d(TAG, "释放 Sherpa-ONNX 资源")
        } catch (e: Exception) {
            Log.e(TAG, "释放资源失败", e)
        }
        callback = null
    }
    
    
    fun isInitialized(): Boolean {
        return recognizer != null
    }
    
    
    fun convertBytesToFloat(data: ByteArray): FloatArray {
        val floatArray = FloatArray(data.size / 2)
        for (i in floatArray.indices) {
            val sample = ((data[i * 2 + 1].toInt() shl 8) or (data[i * 2].toInt() and 0xFF)).toShort()
            floatArray[i] = sample / 32768.0f
        }
        return floatArray
    }
    
    
    fun recognizeStreaming(floatData: FloatArray): String {
        return processAudio(floatData)
    }
    
    
    fun isEndpoint(): Boolean {
        return try {
            recognizer?.isEndpoint(stream!!) ?: false
        } catch (e: Exception) {
            false
        }
    }
    
    
    fun finishRecognition(): String {
        return stopRecognition()
    }
}