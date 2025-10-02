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
    private var isRecording = false
    private var recordingThread: Thread? = null
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    )
    
    private var recognitionListener: RecognitionListener? = null
    
    private val sherpaRecognizer = SherpaOnnxRecognizer(context)
    
    interface RecognitionListener {
        fun onRecordingStart()
        fun onRecordingStop()
        fun onRecognitionResult(text: String)
        fun onRecognitionError(error: String)
        fun onPartialResult(text: String)
    }
    
    
    fun setRecognitionListener(listener: RecognitionListener?) {
        this.recognitionListener = listener
    }
    
    
    fun initialize(): Boolean {
        return try {
            val success = sherpaRecognizer.initialize()
            
            if (success) {
                Log.d(TAG, "Sherpa-ONNX initialized successfully")
            } else {
                Log.w(TAG, "Sherpa-ONNX initialization failed, using mock implementation")
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize VoiceRecognizer", e)
            false
        }
    }
    
    
    fun startRecognition() {
        if (isRecording) {
            Log.w(TAG, "Already recording")
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
            
            sherpaRecognizer.startRecognition { result ->
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
    
    
    private fun processAudioData() {
        try {
            val buffer = ByteArray(bufferSize)
            var lastPartialText = ""
            
            val warmupFrames = 3
            repeat(warmupFrames) {
                audioRecord?.read(buffer, 0, bufferSize)
            }
            
            Log.d(TAG, "音频预热完成，开始识别")
            
            while (isRecording) {
                val readSize = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                
                if (readSize > 0) {
                    val data = buffer.copyOf(readSize)
                    
                    val floatData = sherpaRecognizer.convertBytesToFloat(data)
                    
                    val partialText = sherpaRecognizer.recognizeStreaming(floatData)
                    
                    if (partialText != lastPartialText && partialText.isNotEmpty()) {
                        lastPartialText = partialText
                        recognitionListener?.onPartialResult(partialText)
                    }
                    
                    if (sherpaRecognizer.isEndpoint()) {
                    }
                }
            }
            
            val finalText = sherpaRecognizer.finishRecognition()
            Log.d(TAG, "语音识别完成: $finalText")
            recognitionListener?.onRecognitionResult(finalText)
        } catch (e: Exception) {
            Log.e(TAG, "处理音频数据时出错", e)
            recognitionListener?.onRecognitionError("语音识别出错: ${e.message}")
        }
    }
    
    
    private fun recognizeAudio(audioData: List<ByteArray>) {
        try {
            
            val totalSize = audioData.sumOf { it.size }
            val combinedAudio = ByteArray(totalSize)
            var offset = 0
            for (chunk in audioData) {
                System.arraycopy(chunk, 0, combinedAudio, offset, chunk.size)
                offset += chunk.size
            }
            
            val recognizedText = performRecognition(combinedAudio)
            
            recognitionListener?.onRecognitionResult(recognizedText)
            
        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed", e)
            recognitionListener?.onRecognitionError("识别失败")
        }
    }
    
    
    
    fun release() {
        stopRecognition()
        sherpaRecognizer.release()
        instance = null
    }
    
    
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
    
    
    private fun performRecognition(audioData: ByteArray): String {
        return if (sherpaRecognizer.isInitialized()) {
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
