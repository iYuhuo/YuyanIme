package com.yuyan.imemodule.service

import android.view.KeyEvent
import androidx.lifecycle.MutableLiveData
import com.yuyan.inputmethod.core.CandidateListItem
import com.yuyan.inputmethod.core.Kernel

object DecodingInfo {

    var activeCandidate = 0
    var activeCandidateBar = 0
    val candidatesLiveData = MutableLiveData<List<CandidateListItem>>()

    var isAssociate = false
    private var isReset = false

    
    fun reset() {
        isAssociate = false
        isReset = true
        activeCandidate = 0
        activeCandidateBar = 0
        candidatesLiveData.value = emptyList()
        Kernel.reset()
    }

    val isCandidatesListEmpty: Boolean
        get() = candidatesLiveData.value.isNullOrEmpty()

    val candidateSize: Int
        get() = if(isCandidatesListEmpty) 0 else candidatesLiveData.value!!.size

    val candidates: List<CandidateListItem>
        get() = candidatesLiveData.value?:emptyList()

    fun getCurrentRimeSchema(): String {
        return Kernel.getCurrentRimeSchema()
    }

    fun inputAction(event: KeyEvent) {
        isReset = false
        activeCandidate = 0
        activeCandidateBar = 0
        Kernel.inputKeyCode(event)
        isAssociate = false
    }

    
    fun selectPrefix(position: Int) {
        activeCandidate = 0
        activeCandidateBar = 0
        Kernel.selectPrefix(position)
    }

    val prefixs: Array<String>
        get() = Kernel.prefixs

    
    fun deleteAction() {
        activeCandidate = 0
        activeCandidateBar = 0
        if (isEngineFinish || isAssociate) {
            reset()
        } else {
            Kernel.deleteAction()
        }
    }

    val isFinish: Boolean
        get() = isEngineFinish && isCandidatesListEmpty

    val isEngineFinish: Boolean
        get() = Kernel.isFinish

    val composingStrForDisplay: String
        get() = Kernel.wordsShowPinyin

    val composingStrForCommit: String
        get() = Kernel.wordsShowPinyin.replace("'", "").ifEmpty { getCandidate(0)?.text?:""}

    val nextPageCandidates: Int
        get() {
            val cands = Kernel.nextPageCandidates
            if (cands.isNotEmpty()) {
                candidatesLiveData.postValue(candidatesLiveData.value?.plus(cands))
                return cands.size
            }
            return 0
        }

    
    fun chooseDecodingCandidate(candId: Int): String {
        activeCandidate = 0
        activeCandidateBar = 0
        if (candId >= 0) Kernel.getWordSelectedWord(candId)
        val newCandidates = Kernel.candidates
        return if(newCandidates.isNotEmpty()){
            candidatesLiveData.value = newCandidates
            Kernel.commitText
        } else if(candId in 0..<candidateSize){
            Kernel.commitText.ifEmpty { candidatesLiveData.value!![candId].text }
        } else ""
    }

    
    fun updateDecodingCandidate() {
        activeCandidate = 0
        activeCandidateBar = 0
        candidatesLiveData.value = Kernel.candidates
    }

    
    fun getCandidate(candId: Int): CandidateListItem? {
        return candidatesLiveData.value?.getOrNull(candId)
    }

    fun cacheCandidates(words: Array<CandidateListItem>) {
        activeCandidate = 0
        activeCandidateBar = 0
        isReset = false
        candidatesLiveData.value = words.asList()
    }

    
    fun getAssociateWord(words: String) {
        Kernel.getAssociateWord(words)
    }
}