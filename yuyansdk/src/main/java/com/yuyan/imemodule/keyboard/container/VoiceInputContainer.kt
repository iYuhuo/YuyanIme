package com.yuyan.imemodule.keyboard.container

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.yuyan.imemodule.R
import com.yuyan.imemodule.data.theme.ThemeManager.activeTheme
import com.yuyan.imemodule.keyboard.InputView
import com.yuyan.imemodule.keyboard.KeyboardManager
import com.yuyan.imemodule.prefs.behavior.PopupMenuMode
import com.yuyan.imemodule.singleton.EnvironmentSingleton.Companion.instance
import com.yuyan.imemodule.voice.VoiceRecognizer
import splitties.dimensions.dp
import splitties.views.textResource
import kotlin.math.abs
import kotlin.random.Random

/**
 * 音量可视化视图 - 显示录音时的音量波形
 */
class VolumeVisualizerView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barHeights = FloatArray(20) // 20个波形柱
    private var currentVolume = 0f
    
    init {
        paint.color = Color.parseColor("#4FC3F7")
        paint.strokeWidth = dp(3).toFloat()
        paint.strokeCap = Paint.Cap.ROUND
    }
    
    fun updateVolume(volume: Float) {
        currentVolume = volume.coerceIn(0f, 1f)
        // 向左移动数据
        for (i in 0 until barHeights.size - 1) {
            barHeights[i] = barHeights[i + 1]
        }
        // 添加新数据（使用真实音量，不再添加随机波动）
        barHeights[barHeights.size - 1] = currentVolume
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val width = width.toFloat()
        val height = height.toFloat()
        val barWidth = width / barHeights.size
        val maxBarHeight = height * 0.8f
        
        for (i in barHeights.indices) {
            val barHeight = (barHeights[i].coerceIn(0f, 1f) * maxBarHeight)
            val x = i * barWidth + barWidth / 2
            val top = (height - barHeight) / 2
            val bottom = top + barHeight
            
            // 渐变透明度效果
            val alpha = (255 * (i.toFloat() / barHeights.size)).toInt()
            paint.alpha = alpha
            
            canvas.drawLine(x, top, x, bottom, paint)
        }
    }
}

/**
 * 语音输入容器
 * 
 * 实现离线语音转文字功能
 */
@SuppressLint("ViewConstructor")
class VoiceInputContainer(context: Context, inputView: InputView) : BaseContainer(context, inputView) {
    
    private val mainLayout: LinearLayout = LinearLayout(context)
    private val buttonContainer: LinearLayout = LinearLayout(context)
    private val microphoneIcon: ImageView = ImageView(context)
    private val deleteButton: ImageView = ImageView(context)
    private val statusText: TextView = TextView(context)
    private val volumeVisualizer: VolumeVisualizerView = VolumeVisualizerView(context)
    private var isRecording = false
    private val voiceRecognizer: VoiceRecognizer = VoiceRecognizer.getInstance(context)
    private var isListenerSet = false
    
    // 动画对象
    private var pulseAnimator: ValueAnimator? = null
    private var colorAnimator: ValueAnimator? = null
    private var textAnimator: Runnable? = null
    private var volumeAnimator: Runnable? = null
    
    // 淡蓝色（录音时使用）
    private val lightBlueColor = Color.parseColor("#4FC3F7") // Material Light Blue
    
    // 可爱的状态文字（流式识别：识别和聆听是同时进行的）
    private val listeningTexts = arrayOf("✨ 识别中", "✨ 识别中.", "✨ 识别中..", "✨ 识别中...")
    
    // 记录上一次的部分识别结果
    private var lastPartialText = ""
    
    // 标志：是否是取消操作（而非正常停止）
    private var isCancelled = false
    
    // 音频管理器（用于暂停媒体）
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hadAudioFocus = false
    
    // 触觉反馈
    private val vibrator: Vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    
    // 长按删除
    private var deleteRunnable: Runnable? = null
    private var deleteInterval = 50L // 初始删除间隔
    private val minDeleteInterval = 10L // 最小删除间隔
    private val deleteAcceleration = 10L // 每次递减的间隔
    
    // 滑动取消录音
    private var startY = 0f
    private var startX = 0f
    private val cancelThreshold = dp(80) // 滑动距离超过此值取消录音
    
    init {
        initView(context)
        // 异步初始化语音识别器，避免阻塞UI
        initializeVoiceRecognizerAsync()
        // 设置监听器（只设置一次）
        setupVoiceRecognizer()
        isListenerSet = true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initView(context: Context) {
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.gravity = Gravity.CENTER
        
        // 麦克风容器（居中）
        buttonContainer.orientation = LinearLayout.HORIZONTAL
        buttonContainer.gravity = Gravity.CENTER
        
        // 麦克风图标 - 按住说话，松手停止（类似微信）
        // 根据键盘高度动态调整大小（约占键盘高度的 30%）
        microphoneIcon.apply {
            setImageResource(R.drawable.ic_menu_voice)
            setColorFilter(activeTheme.keyTextColor)
            val keyboardHeight = instance.skbHeight
            val size = (keyboardHeight * 0.3f).toInt().coerceIn(dp(80), dp(180))  // 限制在 80dp-180dp 之间
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                val topMargin = (keyboardHeight * 0.08f).toInt()
                val bottomMargin = (keyboardHeight * 0.05f).toInt()
                setMargins(0, topMargin, 0, bottomMargin)
            }
            // 使用触摸监听器实现按住录音
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = event.rawY
                        startX = event.rawX
                        // 按下动画
                        animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                        // 开始录音
                        performHapticFeedback()
                        startRecording()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isRecording) {
                            val deltaY = startY - event.rawY
                            val deltaX = abs(event.rawX - startX)
                            // 检测向上滑动取消
                            if (deltaY > cancelThreshold && deltaX < cancelThreshold) {
                                // 显示取消提示
                                statusText.text = "👆 松开取消"
                                statusText.setTextColor(Color.parseColor("#FF5252"))
                            } else {
                                // 恢复正常提示
                                if (statusText.text == "👆 松开取消") {
                                    statusText.setTextColor(lightBlueColor)
                                    statusText.text = listeningTexts[0]
                                }
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // 释放动画
                        animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                        if (isRecording) {
                            val deltaY = startY - event.rawY
                            val deltaX = abs(event.rawX - startX)
                            if (deltaY > cancelThreshold && deltaX < cancelThreshold) {
                                // 取消录音
                                cancelRecording()
                                performHapticFeedback()
                            } else {
                                // 正常停止录音
                                stopRecording()
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
        }
        
        // 删除按钮 - 放在右上角，小尺寸（使用 ConstraintLayout 布局参数）
        deleteButton.apply {
            setImageResource(R.drawable.sdk_skb_key_delete_icon)
            setColorFilter(activeTheme.keyTextColor)
            val size = dp(32) // 更小的尺寸
            id = View.generateViewId()
            layoutParams = ConstraintLayout.LayoutParams(size, size).apply {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                topMargin = dp(10)
                marginEnd = dp(10)
            }
            alpha = 0.6f
            setOnClickListener {
                performHapticFeedback()
                deleteText()
            }
            // 长按连续删除
            setOnLongClickListener {
                performHapticFeedback(20)
                startContinuousDelete()
                true
            }
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        animate().scaleX(0.85f).scaleY(0.85f).setDuration(100).start()
                        false
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                        stopContinuousDelete()
                        false
                    }
                    else -> false
                }
            }
        }
        
        // 添加麦克风到中间容器
        buttonContainer.addView(microphoneIcon)
        
        // 音量可视化视图 - 根据键盘高度动态调整
        volumeVisualizer.apply {
            val keyboardHeight = instance.skbHeight
            val visualizerHeight = (keyboardHeight * 0.15f).toInt().coerceIn(dp(50), dp(80))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                visualizerHeight
            ).apply {
                val horizontalMargin = (keyboardHeight * 0.08f).toInt()
                val bottomMargin = (keyboardHeight * 0.02f).toInt()
                setMargins(horizontalMargin, 0, horizontalMargin, bottomMargin)
            }
            alpha = 0f // 初始隐藏
        }
        
        // 状态文本 - 根据键盘高度动态调整
        statusText.apply {
            text = "💬 按住说话"
            gravity = Gravity.CENTER
            setTextColor(activeTheme.keyTextColor)
            textSize = instance.candidateTextSize * 1.2f
            val keyboardHeight = instance.skbHeight
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                val topMargin = (keyboardHeight * 0.01f).toInt()
                setMargins(0, topMargin, 0, 0)
            }
        }
        
        mainLayout.addView(buttonContainer)
        mainLayout.addView(volumeVisualizer)
        mainLayout.addView(statusText)
        
        val layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        mainLayout.layoutParams = layoutParams
        
        // 先添加主布局（居中的内容）
        this.addView(mainLayout)
        // 再添加删除按钮（浮动在右上角）
        this.addView(deleteButton)
    }
    
    /**
     * 切换录音状态
     */
    private fun toggleRecording() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }
    
    /**
     * 设置语音识别器监听器（只在初始化时调用一次）
     */
    private fun setupVoiceRecognizer() {
        voiceRecognizer.setRecognitionListener(object : VoiceRecognizer.RecognitionListener {
            override fun onRecordingStart() {
                if (isAttachedToWindow) {
                    post {
                        isRecording = true
                        isCancelled = false  // 重置取消标志
                        // 流式识别：显示识别中（识别和聆听是同时进行的）
                        statusText.text = "✨ 识别中..."
                        // 平滑过渡到淡蓝色
                        animateColorChange(activeTheme.keyTextColor, lightBlueColor)
                        
                        // 延迟 300ms 后开始录音动画，给音频系统预热时间
                        postDelayed({
                            if (isRecording && isAttachedToWindow) {
                                // 启动可爱的文字动画
                                startTextAnimation()
                                // 启动脉冲动画
                                startPulseAnimation()
                            }
                        }, 300)
                    }
                }
            }
            
            override fun onRecordingStop() {
                if (isAttachedToWindow) {
                    post {
                        // 如果是取消操作，不要覆盖"已取消"的提示
                        if (isCancelled) return@post
                        
                        isRecording = false
                        // 停止文字动画
                        stopTextAnimation()
                        // 停止脉冲动画
                        stopPulseAnimation()
                        // 流式识别：松手后不显示额外状态，保持文字显示，等待最终结果
                        // 平滑过渡回正常颜色
                        animateColorChange(lightBlueColor, activeTheme.keyTextColor)
                    }
                }
            }
            
            override fun onRecognitionResult(text: String) {
                // 确保在View还附加到窗口时才处理回调
                if (isAttachedToWindow) {
                    post {
                        // 如果是取消操作，忽略识别结果
                        if (isCancelled) return@post
                        
                        // 清除部分结果的 composing 状态
                        if (lastPartialText.isNotEmpty()) {
                            inputView.clearPartialText()
                            lastPartialText = ""
                        }
                        handleRecognitionResult(text)
                    }
                }
            }
            
            override fun onRecognitionError(error: String) {
                if (isAttachedToWindow) {
                    post {
                        statusText.text = "😥 $error"
                        microphoneIcon.setColorFilter(Color.parseColor("#FF5252")) // 错误红色
                        postDelayed({
                            statusText.animate()
                                .alpha(0f)
                                .setDuration(200)
                                .withEndAction {
                                    statusText.text = "💬 按住说话"
                                    microphoneIcon.setColorFilter(activeTheme.keyTextColor)
                                    statusText.animate()
                                        .alpha(1f)
                                        .setDuration(200)
                                        .start()
                                }
                                .start()
                        }, 2000)
                    }
                }
            }
            
            override fun onPartialResult(text: String) {
                // 流式识别：实时输入到文本框
                if (isAttachedToWindow) {
                    post {
                        if (isRecording && text.isNotEmpty()) {
                            // 直接更新 composing 文本（会自动替换之前的）
                            if (text != lastPartialText) {
                                inputView.setPartialText(text)
                                lastPartialText = text
                            }
                        }
                    }
                }
            }
            
            override fun onVolumeChanged(volume: Float) {
                // 更新音量可视化（使用真实音量）
                if (isAttachedToWindow && isRecording) {
                    post {
                        volumeVisualizer.updateVolume(volume)
                    }
                }
            }
        })
    }
    
    /**
     * 异步初始化语音识别器
     */
    private fun initializeVoiceRecognizerAsync() {
        Thread {
            try {
                // 初始化语音识别器
                val success = voiceRecognizer.initialize()
                if (success) {
                    Log.d("VoiceInputContainer", "✓ 语音识别器初始化成功")
                    // 等待一小段时间确保模型完全加载
                    Thread.sleep(100)
                    post {
                        if (voiceRecognizer.isModelReady()) {
                            statusText.text = "点击麦克风开始录音"
                        }
                    }
                } else {
                    Log.e("VoiceInputContainer", "✗ 语音识别器初始化失败")
                    post {
                        statusText.text = "⚠️ 语音识别初始化失败，请重启应用"
                    }
                }
            } catch (e: Exception) {
                Log.e("VoiceInputContainer", "语音识别器异步初始化失败", e)
                post {
                    statusText.text = "⚠️ 语音识别初始化异常：${e.message}"
                }
            }
        }.start()
    }

    /**
     * 开始录音
     */
    private fun startRecording() {
        // 检查权限
        if (!checkMicrophonePermission()) {
            // 没有权限，请求用户授予
            requestMicrophonePermission()
            return
        }

        // 检查模型是否准备好
        if (!voiceRecognizer.isModelReady()) {
            // 识别器已初始化但模型未准备好（大模型正在异步加载）
            statusText.text = "⏳ 模型加载中，请稍候..."

            // 显示加载状态，并在不同时间点提供反馈
            var checkCount = 0
            val maxChecks = 20 // 最多检查20次
            val checkInterval = 500L // 每500毫秒检查一次，总共10秒
            
            fun scheduleCheck() {
                postDelayed({
                    checkCount++
                    if (voiceRecognizer.isModelReady()) {
                        // 模型已准备好
                        statusText.text = "✓ 模型加载完成，请点击麦克风开始"
                    } else if (checkCount >= 3 && !voiceRecognizer.isInitialized()) {
                        // 检查3次后如果识别器还未初始化，说明初始化失败
                        statusText.text = "⚠️ 模型初始化失败，请检查模型文件或重启应用"
                    } else if (checkCount < maxChecks) {
                        // 继续等待
                        val dots = ".".repeat((checkCount % 3) + 1)
                        statusText.text = "⏳ 模型加载中$dots"
                        scheduleCheck()
                    } else {
                        // 超时
                        statusText.text = "⚠️ 模型加载超时，请稍后重试或切换到小模型"
                    }
                }, checkInterval)
            }
            
            scheduleCheck()
            return
        }

        // 请求音频焦点以暂停媒体播放
        requestAudioFocus()

        // 重置部分结果
        lastPartialText = ""
        voiceRecognizer.startRecognition()
    }
    
    /**
     * 停止录音
     */
    private fun stopRecording() {
        voiceRecognizer.stopRecognition()
        // 释放音频焦点，恢复媒体播放
        abandonAudioFocus()
    }
    
    /**
     * 处理识别结果
     */
    private fun handleRecognitionResult(text: String) {
        if (text.isNotEmpty()) {
            // 直接输出识别的文本（不预览）
            inputView.directCommitText(text)
            // 重置状态，可以继续录音
            isRecording = false
            // 直接恢复到初始状态
            statusText.text = "💬 按住说话"
        } else {
            // 淡入显示空结果
            statusText.alpha = 0f
            statusText.text = "🤔 未识别到内容"
            statusText.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
            
            postDelayed({
                statusText.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        statusText.text = "💬 按住说话"
                        statusText.animate()
                            .alpha(1f)
                            .setDuration(300)
                            .start()
                    }
                    .start()
            }, 1500)
        }
    }
    
    /**
     * 显示语音输入界面
     */
    fun showVoiceInputView() {
        // 重置状态
        isRecording = false
        statusText.text = "💬 按住说话"
        microphoneIcon.setColorFilter(activeTheme.keyTextColor)
        // 微妙的入场动画
        microphoneIcon.scaleX = 0.8f
        microphoneIcon.scaleY = 0.8f
        microphoneIcon.alpha = 0f
        microphoneIcon.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .alpha(1.0f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
    
    /**
     * 隐藏语音输入界面（清理资源）
     */
    fun hideVoiceInputView() {
        // 如果正在录音，停止录音
        if (isRecording) {
            stopRecording()
        }
        // 清除部分识别结果
        if (lastPartialText.isNotEmpty()) {
            inputView.clearPartialText()
            lastPartialText = ""
        }
        // 释放音频焦点
        abandonAudioFocus()
        // 停止所有动画
        stopAllAnimations()
        // 重置状态
        isRecording = false
        statusText.text = "💬 按住说话"
        microphoneIcon.setColorFilter(activeTheme.keyTextColor)
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 停止所有动画
        stopAllAnimations()
        // 当View从窗口分离时，清理资源
        hideVoiceInputView()
        // 确保释放音频焦点
        abandonAudioFocus()
        // 清除监听器，避免内存泄漏
        voiceRecognizer.setRecognitionListener(null)
        isListenerSet = false
    }
    
    /**
     * 启动脉冲动画（录音时）+ 音量可视化
     */
    private fun startPulseAnimation() {
        stopPulseAnimation()
        
        // 麦克风呼吸动画
        pulseAnimator = ValueAnimator.ofFloat(1.0f, 1.15f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            
            addUpdateListener { animation ->
                val scale = animation.animatedValue as Float
                microphoneIcon.scaleX = scale
                microphoneIcon.scaleY = scale
            }
            
            start()
        }
        
        // 显示音量可视化
        volumeVisualizer.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
        
        // 启动音量动画更新
        startVolumeAnimation()
    }
    
    /**
     * 停止脉冲动画
     */
    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        
        // 停止音量动画
        stopVolumeAnimation()
        
        // 隐藏音量可视化
        volumeVisualizer.animate()
            .alpha(0f)
            .setDuration(300)
            .start()
        
        // 恢复正常大小
        microphoneIcon.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
    
    /**
     * 启动音量动画
     * 注意：音量数据现在从 onVolumeChanged 回调中获取，这个方法只负责显示可视化组件
     */
    private fun startVolumeAnimation() {
        stopVolumeAnimation()
        // 音量数据由 onVolumeChanged 回调更新，不再需要模拟数据
    }
    
    /**
     * 停止音量动画
     */
    private fun stopVolumeAnimation() {
        volumeAnimator?.let {
            removeCallbacks(it)
            volumeAnimator = null
        }
    }
    
    /**
     * 颜色过渡动画
     */
    private fun animateColorChange(fromColor: Int, toColor: Int) {
        colorAnimator?.cancel()
        
        colorAnimator = ValueAnimator.ofArgb(fromColor, toColor).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            
            addUpdateListener { animation ->
                val color = animation.animatedValue as Int
                microphoneIcon.setColorFilter(color)
            }
            
            start()
        }
    }
    
    /**
     * 成功反馈动画 - 更生动的效果
     */
    private fun showSuccessAnimation() {
        performHapticFeedback(15)
        
        // 弹跳效果
        microphoneIcon.animate()
            .scaleX(1.4f)
            .scaleY(1.4f)
            .setDuration(200)
            .setInterpolator(OvershootInterpolator())
            .withEndAction {
                microphoneIcon.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(200)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
            .start()
        
        // 绿色闪烁效果
        animateColorChange(activeTheme.keyTextColor, Color.parseColor("#4CAF50"))
        postDelayed({
            animateColorChange(Color.parseColor("#4CAF50"), activeTheme.keyTextColor)
        }, 400)
    }
    
    /**
     * 启动文字动画（闪烁的点点）
     */
    private fun startTextAnimation() {
        stopTextAnimation()
        var index = 0
        
        textAnimator = object : Runnable {
            override fun run() {
                if (isRecording && isAttachedToWindow) {
                    statusText.text = listeningTexts[index % listeningTexts.size]
                    index++
                    postDelayed(this, 500) // 每0.5秒切换一次
                }
            }
        }
        
        post(textAnimator!!)
    }
    
    /**
     * 停止文字动画
     */
    private fun stopTextAnimation() {
        textAnimator?.let {
            removeCallbacks(it)
            textAnimator = null
        }
    }
    
    /**
     * 停止所有动画
     */
    private fun stopAllAnimations() {
        pulseAnimator?.cancel()
        colorAnimator?.cancel()
        stopTextAnimation()
        stopVolumeAnimation()
        stopContinuousDelete()
        microphoneIcon.clearAnimation()
        statusText.clearAnimation()
        volumeVisualizer.clearAnimation()
    }
    
    /**
     * 删除文字
     */
    private fun deleteText() {
        val ic = inputView.getInputConnection() ?: return
        
        // 添加按压反馈动画
        deleteButton.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(100)
            .withEndAction {
                deleteButton.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
        
        // 获取光标前的文字
        val beforeCursor = ic.getTextBeforeCursor(1, 0)
        
        if (beforeCursor?.isNotEmpty() == true) {
            // 删除一个字符
            ic.deleteSurroundingText(1, 0)
        } else if (lastPartialText.isNotEmpty()) {
            // 如果没有已提交的文字，但有预览文字，清除预览
            inputView.clearPartialText()
            lastPartialText = ""
            statusText.text = "💬 按住说话"
        }
    }
    
    
    /**
     * 检查麦克风权限
     */
    private fun checkMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 请求麦克风权限
     */
    private fun requestMicrophonePermission() {
        try {
            // 显示友好提示
            statusText.text = "🎤 需要麦克风权限"
            
            // 由于输入法运行在 Service 中，无法直接请求运行时权限
            // 需要引导用户到应用设置页面手动授予权限
            postDelayed({
                openAppSettings()
            }, 300)
            
            postDelayed({
                statusText.text = "💬 按住说话"
            }, 3000)
        } catch (e: Exception) {
            statusText.text = "❌ 无法打开设置"
            postDelayed({
                statusText.text = "💬 按住说话"
            }, 2000)
        }
    }
    
    /**
     * 打开应用设置页面
     */
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 如果失败，尝试打开通用设置页面
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                // 忽略错误
            }
        }
    }
    
    /**
     * 请求音频焦点（暂停媒体播放）
     */
    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0 及以上
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { focusChange ->
                        // 处理音频焦点变化（如果需要）
                    }
                    .build()
                
                val result = audioManager.requestAudioFocus(audioFocusRequest!!)
                hadAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                // Android 8.0 以下
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    { focusChange -> /* 处理音频焦点变化 */ },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                hadAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            // 忽略错误
            hadAudioFocus = false
        }
    }
    
    /**
     * 释放音频焦点（恢复媒体播放）
     */
    private fun abandonAudioFocus() {
        if (!hadAudioFocus) return
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let {
                    audioManager.abandonAudioFocusRequest(it)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus { /* 焦点变化监听器 */ }
            }
            hadAudioFocus = false
        } catch (e: Exception) {
            // 忽略错误
        }
    }
    
    /**
     * 触觉反馈
     * @param duration 震动时长（毫秒），默认10ms
     */
    private fun performHapticFeedback(duration: Long = 10) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) {
            // 忽略错误
        }
    }
    
    /**
     * 开始连续删除
     */
    private fun startContinuousDelete() {
        stopContinuousDelete()
        
        // 重置删除间隔
        deleteInterval = 300L
        
        deleteRunnable = object : Runnable {
            override fun run() {
                if (isAttachedToWindow) {
                    deleteText()
                    // 轻微触觉反馈
                    performHapticFeedback(5)
                    
                    // 递减删除间隔，加快删除速度
                    deleteInterval = (deleteInterval - deleteAcceleration).coerceAtLeast(minDeleteInterval)
                    postDelayed(this, deleteInterval)
                }
            }
        }
        
        postDelayed(deleteRunnable!!, 300) // 延迟300ms开始连续删除
    }
    
    /**
     * 停止连续删除
     */
    private fun stopContinuousDelete() {
        deleteRunnable?.let {
            removeCallbacks(it)
            deleteRunnable = null
        }
    }
    
    /**
     * 取消录音（不保存结果）
     */
    private fun cancelRecording() {
        if (!isRecording) return
        
        // 设置取消标志，防止回调覆盖取消提示
        isCancelled = true
        
        // 停止录音
        voiceRecognizer.stopRecognition()
        isRecording = false
        
        // 清除部分结果
        if (lastPartialText.isNotEmpty()) {
            inputView.clearPartialText()
            lastPartialText = ""
        }
        
        // 停止所有动画
        stopPulseAnimation()
        stopTextAnimation()
        animateColorChange(lightBlueColor, activeTheme.keyTextColor)
        
        // 显示取消提示
        statusText.text = "❌ 已取消"
        statusText.setTextColor(Color.parseColor("#FF5252"))
        
        // 延迟恢复
        postDelayed({
            statusText.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    statusText.text = "💬 按住说话"
                    statusText.setTextColor(activeTheme.keyTextColor)
                    statusText.animate()
                        .alpha(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
        }, 800)
    }
}









