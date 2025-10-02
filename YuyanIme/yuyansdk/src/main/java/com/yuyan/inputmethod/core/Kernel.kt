package com.yuyan.inputmethod.core

import android.view.KeyEvent
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.service.DecodingInfo
import com.yuyan.inputmethod.RimeEngine

object Kernel {

    @Synchronized
    fun initImeSchema(schema: String) {
        RimeEngine.selectSchema(schema)
        nativeUpdateImeOption()
    }

    fun getCurrentRimeSchema(): String {
        return RimeEngine.getCurrentRimeSchema()
    }

    fun inputKeyCode(event: KeyEvent) {
        RimeEngine.onNormalKey(event)
    }

    val isFinish: Boolean
        get() = RimeEngine.isFinish()

    val candidates: List<CandidateListItem>
        get() = RimeEngine.showCandidates

    val nextPageCandidates: Array<CandidateListItem>
        get() = RimeEngine.getNextPageCandidates()

    val prefixs: Array<String>
        get() = RimeEngine.getPrefixs()

    fun selectPrefix(index: Int) {
        RimeEngine.selectPinyin(index)
    }

    fun getWordSelectedWord(index: Int) {
        if (DecodingInfo.isAssociate) RimeEngine.selectAssociation(index)
        else RimeEngine.selectCandidate(index)
    }

    val wordsShowPinyin: String
        get() = RimeEngine.showComposition

    val commitText: String
        get() = RimeEngine.preCommitText

    fun deleteAction() {
        RimeEngine.onDeleteKey()
    }

    fun reset() {
        RimeEngine.reset()
    }

    fun resetIme() {
        RimeEngine.destroy()
        initImeSchema(AppPrefs.getInstance().internal.pinyinModeRime.getValue())
    }

    fun getAssociateWord(words: String) {
        RimeEngine.predictAssociationWords(words)
    }

    fun nativeUpdateImeOption() {
        val chineseFanTi = AppPrefs.getInstance().input.chineseFanTi.getValue()
        RimeEngine.setImeOption("traditionalization", chineseFanTi)
        val emojiInput = AppPrefs.getInstance().input.emojiInput.getValue()
        RimeEngine.setImeOption("emoji", emojiInput)
    }
}