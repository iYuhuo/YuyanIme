package com.yuyan.imemodule.ui.fragment

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.yuyan.imemodule.R
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.ui.activity.VoiceModelManagementActivity
import com.yuyan.imemodule.ui.fragment.base.ManagedPreferenceFragment
import com.yuyan.imemodule.voice.VoiceModelManager
import com.yuyan.imemodule.voice.VoiceRecognizer

class VoiceSettingsFragment: ManagedPreferenceFragment(AppPrefs.getInstance().voice) {
    
    // 权限请求启动器
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(requireContext(), "✓ 麦克风权限已授权", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "⚠️ 未获得麦克风权限，语音功能将不可用", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        // 检查并请求麦克风权限
        checkMicrophonePermission()
    }
    
    override fun onResume() {
        super.onResume()
        updateModelSummary()
    }
    
    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        when (preference.key) {
            "voice_model_management" -> {
                // 打开模型管理界面
                val intent = Intent(requireContext(), VoiceModelManagementActivity::class.java)
                startActivity(intent)
                return true
            }
        }
        return super.onPreferenceTreeClick(preference)
    }
    
    
    private fun updateModelSummary() {
        val modelManager = VoiceModelManager.getInstance(requireContext())
        val currentModelId = AppPrefs.getInstance().internal.voiceModelId.getValue()
        val currentModel = modelManager.getModelById(currentModelId)
        
        // 更新模型管理项的摘要
        findPreference<Preference>("voice_model_management")?.apply {
            summary = currentModel?.let {
                "${it.name} (${it.language} | ${it.modelType})"
            } ?: getString(R.string.voice_model_builtin)
        }
    }
    
    /**
     * 检查麦克风权限
     */
    private fun checkMicrophonePermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            // 请求权限
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
}