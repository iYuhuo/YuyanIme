package com.yuyan.imemodule.entity.keyboard

import android.graphics.drawable.Drawable
import com.yuyan.imemodule.keyboard.keyIconRecords
import java.util.Locale
import java.util.Objects

class SoftKeyToggle(code: Int) : SoftKey(code = code) {
    private var mToggleStates: List<ToggleState>? = null

    fun setToggleStates(toggleStates: List<ToggleState>?) {
        mToggleStates = toggleStates
    }

    
    fun enableToggleState(stateId: Int): Boolean {
        val oldStateId = super.stateId
        if (oldStateId == stateId) return false
        super.stateId = stateId
        return true
    }

    override val keyIcon: Drawable?
        get() {
            val state = toggleState
            return if (null != state) {
                keyIconRecords[Objects.hash(code, stateId)]
            } else super.keyIcon
        }

    override val keyLabel: String
        get() {
            val state = toggleState
            return state?.label ?: super.getkeyLabel()
        }

    override fun changeCase(upperCase: Boolean) {
        val state = toggleState
        if (state?.label != null) {
            state.label = if (upperCase) state.label.lowercase(Locale.getDefault())
            else state.label.uppercase(Locale.getDefault())
        }
    }

    private val toggleState: ToggleState?

        get() {
            for (state in mToggleStates!!) {
                if (state.stateId == stateId) {
                    return state
                }
            }
            return null
        }
}
