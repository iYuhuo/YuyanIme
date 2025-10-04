package com.yuyan.imemodule.ui.fragment

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.yuyan.imemodule.R
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.ui.activity.VoiceModelManagementActivity
import com.yuyan.imemodule.ui.fragment.base.ManagedPreferenceFragment
import com.yuyan.imemodule.voice.VoiceModelManager
import com.yuyan.imemodule.voice.VoiceRecognizer

class VoiceSettingsFragment: ManagedPreferenceFragment(AppPrefs.getInstance().voice) {
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
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
    
}