package com.yuyan.imemodule.keyboard.container

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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

@SuppressLint("ViewConstructor")
class VoiceInputContainer(context: Context, inputView: InputView) : BaseContainer(context, inputView) {
    
    private val mainLayout: LinearLayout = LinearLayout(context)
    private val buttonContainer: LinearLayout = LinearLayout(context)
    private val microphoneIcon: ImageView = ImageView(context)
    private val deleteButton: ImageView = ImageView(context)
    private val statusText: TextView = TextView(context)
    private var isRecording = false
    private val voiceRecognizer: VoiceRecognizer = VoiceRecognizer.getInstance(context)
    private var isListenerSet = false
    
    private var pulseAnimator: ValueAnimator? = null
    private var colorAnimator: ValueAnimator? = null
    private var textAnimator: Runnable? = null
    
    private val lightBlueColor = Color.parseColor("#4FC3F7")
    
    private val listeningTexts = arrayOf("🎤 正在聆听", "🎤 正在聆听.", "🎤 正在聆听..", "🎤 正在聆听...")
    
    private var lastPartialText = ""
    
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hadAudioFocus = false
    
    init {
        initView(context)
        voiceRecognizer.initialize()
        setupVoiceRecognizer()
        isListenerSet = true
    }

    private fun initView(context: Context) {
        mainLayout.orientation = LinearLayout.VERTICAL
        mainLayout.gravity = Gravity.CENTER
        
        buttonContainer.orientation = LinearLayout.HORIZONTAL
        buttonContainer.gravity = Gravity.CENTER
        
        microphoneIcon.apply {
            setImageResource(R.drawable.ic_menu_voice)
            setColorFilter(activeTheme.keyTextColor)
            val size = dp(80)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(0, dp(20), dp(30), dp(20))
            }
            setOnClickListener {
                toggleRecording()
            }
        }
        
        deleteButton.apply {
            setImageResource(R.drawable.sdk_skb_key_delete_icon)
            setColorFilter(activeTheme.keyTextColor)
            val size = dp(60)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(dp(30), dp(20), 0, dp(20))
            }
            alpha = 0.7f
            setOnClickListener {
                deleteText()
            }
        }
        
        buttonContainer.addView(microphoneIcon)
        buttonContainer.addView(deleteButton)
        
        statusText.apply {
            text = "💬 点击开始说话"
            gravity = Gravity.CENTER
            setTextColor(activeTheme.keyTextColor)
            textSize = instance.candidateTextSize * 1.2f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        mainLayout.addView(buttonContainer)
        mainLayout.addView(statusText)
        
        val layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        mainLayout.layoutParams = layoutParams
        
        this.addView(mainLayout)
    }
    
    
    private fun toggleRecording() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }
    
    
    private fun setupVoiceRecognizer() {
        voiceRecognizer.setRecognitionListener(object : VoiceRecognizer.RecognitionListener {
            override fun onRecordingStart() {
                if (isAttachedToWindow) {
                    post {
                        isRecording = true
                        statusText.text = "✨ 准备就绪，请说话"
                        animateColorChange(activeTheme.keyTextColor, lightBlueColor)
                        
                        postDelayed({
                            if (isRecording && isAttachedToWindow) {
                                startTextAnimation()
                                startPulseAnimation()
                            }
                        }, 300)
                    }
                }
            }
            
            override fun onRecordingStop() {
                if (isAttachedToWindow) {
                    post {
                        isRecording = false
                        stopTextAnimation()
                        stopPulseAnimation()
                        statusText.text = "✨ 识别中..."
                        animateColorChange(lightBlueColor, activeTheme.keyTextColor)
                    }
                }
            }
            
            override fun onRecognitionResult(text: String) {
                if (isAttachedToWindow) {
                    post {
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
                        microphoneIcon.setColorFilter(Color.parseColor("#FF5252"))
                        postDelayed({
                            statusText.animate()
                                .alpha(0f)
                                .setDuration(200)
                                .withEndAction {
                                    statusText.text = "💬 点击开始说话"
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
                if (isAttachedToWindow) {
                    post {
                        if (isRecording && text.isNotEmpty()) {
                            if (text != lastPartialText) {
                                inputView.setPartialText(text)
                                lastPartialText = text
                            }
                        }
                    }
                }
            }
        })
    }
    
    
    private fun startRecording() {
        if (!checkMicrophonePermission()) {
            requestMicrophonePermission()
            return
        }
        
        requestAudioFocus()
        
        lastPartialText = ""
        voiceRecognizer.startRecognition()
    }
    
    
    private fun stopRecording() {
        voiceRecognizer.stopRecognition()
        abandonAudioFocus()
    }
    
    
    private fun handleRecognitionResult(text: String) {
        if (text.isNotEmpty()) {
            showSuccessAnimation()
            inputView.directCommitText(text)
            isRecording = false
            statusText.text = "✓ 识别成功"
            postDelayed({
                statusText.alpha = 0f
                statusText.text = "💬 点击开始说话"
                statusText.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start()
            }, 600)
        } else {
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
                        statusText.text = "💬 点击开始说话"
                        statusText.animate()
                            .alpha(1f)
                            .setDuration(300)
                            .start()
                    }
                    .start()
            }, 1500)
        }
    }
    
    
    fun showVoiceInputView() {
        isRecording = false
        statusText.text = "💬 点击开始说话"
        microphoneIcon.setColorFilter(activeTheme.keyTextColor)
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
    
    
    fun hideVoiceInputView() {
        if (isRecording) {
            stopRecording()
        }
        if (lastPartialText.isNotEmpty()) {
            inputView.clearPartialText()
            lastPartialText = ""
        }
        abandonAudioFocus()
        stopAllAnimations()
        isRecording = false
        statusText.text = "💬 点击开始说话"
        microphoneIcon.setColorFilter(activeTheme.keyTextColor)
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAllAnimations()
        hideVoiceInputView()
        abandonAudioFocus()
        voiceRecognizer.setRecognitionListener(null)
        isListenerSet = false
    }
    
    
    private fun startPulseAnimation() {
        stopPulseAnimation()
        
        pulseAnimator = ValueAnimator.ofFloat(1.0f, 1.2f).apply {
            duration = 800
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
    }
    
    
    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        
        microphoneIcon.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
    
    
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
    
    
    private fun showSuccessAnimation() {
        microphoneIcon.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(150)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                microphoneIcon.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
            .start()
        
        val originalColor = microphoneIcon.colorFilter
        animateColorChange(activeTheme.keyTextColor, Color.GREEN)
        postDelayed({
            animateColorChange(Color.GREEN, activeTheme.keyTextColor)
        }, 300)
    }
    
    
    private fun startTextAnimation() {
        stopTextAnimation()
        var index = 0
        
        textAnimator = object : Runnable {
            override fun run() {
                if (isRecording && isAttachedToWindow) {
                    statusText.text = listeningTexts[index % listeningTexts.size]
                    index++
                    postDelayed(this, 500)
                }
            }
        }
        
        post(textAnimator!!)
    }
    
    
    private fun stopTextAnimation() {
        textAnimator?.let {
            removeCallbacks(it)
            textAnimator = null
        }
    }
    
    
    private fun stopAllAnimations() {
        pulseAnimator?.cancel()
        colorAnimator?.cancel()
        stopTextAnimation()
        microphoneIcon.clearAnimation()
        statusText.clearAnimation()
    }
    
    
    private fun deleteText() {
        val ic = inputView.getInputConnection() ?: return
        
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
        
        val beforeCursor = ic.getTextBeforeCursor(1, 0)
        
        if (beforeCursor?.isNotEmpty() == true) {
            ic.deleteSurroundingText(1, 0)
        } else if (lastPartialText.isNotEmpty()) {
            inputView.clearPartialText()
            lastPartialText = ""
            statusText.text = "💬 点击开始说话"
        }
    }
    
    
    private fun checkMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    
    private fun requestMicrophonePermission() {
        try {
            statusText.text = "🎤 需要麦克风权限"
            
            postDelayed({
                openAppSettings()
            }, 300)
            
            postDelayed({
                statusText.text = "💬 点击开始说话"
            }, 3000)
        } catch (e: Exception) {
            statusText.text = "❌ 无法打开设置"
            postDelayed({
                statusText.text = "💬 点击开始说话"
            }, 2000)
        }
    }
    
    
    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
            }
        }
    }
    
    
    private fun requestAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { focusChange ->
                    }
                    .build()
                
                val result = audioManager.requestAudioFocus(audioFocusRequest!!)
                hadAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    { focusChange ->  },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                hadAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            hadAudioFocus = false
        }
    }
    
    
    private fun abandonAudioFocus() {
        if (!hadAudioFocus) return
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let {
                    audioManager.abandonAudioFocusRequest(it)
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus {  }
            }
            hadAudioFocus = false
        } catch (e: Exception) {
        }
    }
}
