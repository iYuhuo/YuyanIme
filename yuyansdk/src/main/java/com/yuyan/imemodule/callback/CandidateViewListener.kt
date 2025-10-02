package com.yuyan.imemodule.callback

import com.yuyan.imemodule.prefs.behavior.SkbMenuMode

interface CandidateViewListener {
    fun onClickChoice(choiceId: Int)
    fun onClickMore(level: Int)
    fun onClickMenu(skbMenuMode: SkbMenuMode)
    fun onClickClearCandidate()
    fun onClickClearClipBoard()
}