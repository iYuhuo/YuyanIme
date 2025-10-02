package com.yuyan.imemodule.keyboard.container

import android.annotation.SuppressLint
import android.content.Context
import com.yuyan.imemodule.keyboard.InputView
import com.yuyan.imemodule.keyboard.TextKeyboard

@SuppressLint("ViewConstructor")
open class InputBaseContainer(context: Context?, inputView: InputView) : BaseContainer(context!!, inputView) {

    @JvmField
    protected var mMajorView: TextKeyboard? = null

    
    fun updateStates() {
        mMajorView!!.updateStates()
    }
}