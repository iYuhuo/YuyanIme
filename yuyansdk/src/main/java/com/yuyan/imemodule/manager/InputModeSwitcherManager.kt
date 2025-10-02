package com.yuyan.imemodule.manager

import android.view.inputmethod.EditorInfo
import com.yuyan.imemodule.application.CustomConstant
import com.yuyan.imemodule.prefs.AppPrefs.Companion.getInstance
import com.yuyan.imemodule.keyboard.KeyboardManager
import com.yuyan.inputmethod.core.Kernel

object InputModeSwitcherManager {

    
    const val USER_DEF_KEYCODE_SHIFT_1 = -1

    
    const val USER_DEF_KEYCODE_LANG_2 = -2

    
    const val USER_DEF_KEYCODE_SYMBOL_3 = -3

    
    const val USER_DEF_KEYCODE_EMOJI_4 = -4

    
    const val USER_DEF_KEYCODE_NUMBER_5 = -5

    
    const val USER_DEF_KEYCODE_RETURN_6 = -6

    
    const val USER_DEF_KEYCODE_TEXTEDIT_7 = -7

    
    const val USER_DEF_KEYCODE_EMOJI_8 = -8

    
    const val USER_DEF_KEYCODE_CURSOR_DIRECTION_9 = -9

    
    const val USER_DEF_KEYCODE_LEFT_SYMBOL_12 = -12

    
    const val USER_DEF_KEYCODE_LEFT_COMMA_13 = -13
    
    const val USER_DEF_KEYCODE_LEFT_PERIOD_14 = -14
    
    const val USER_DEF_KEYCODE_STAR_17 = -17

    
    const val USER_DEF_KEYCODE_SELECT_MODE = -18
    const val USER_DEF_KEYCODE_SELECT_ALL = -19
    const val USER_DEF_KEYCODE_CUT = -20
    const val USER_DEF_KEYCODE_COPY = -21
    const val USER_DEF_KEYCODE_PASTE = -22
    const val USER_DEF_KEYCODE_MOVE_START = -23
    const val USER_DEF_KEYCODE_MOVE_END = -24

    
    const val MASK_SKB_LAYOUT = 0xff00

    
    const val MASK_SKB_LAYOUT_QWERTY_PINYIN = 0x1000

    
    const val MASK_SKB_LAYOUT_T9_PINYIN = 0x2000

    
    const val MASK_SKB_LAYOUT_HANDWRITING = 0x3000

    
    const val MASK_SKB_LAYOUT_QWERTY_ABC = 0x4000

    
    const val MASK_SKB_LAYOUT_NUMBER = 0x5000

    
    const val MASK_SKB_LAYOUT_LX17 = 0x6000

    
    const val MASK_SKB_LAYOUT_STROKE = 0x7000

    
    const val MASK_SKB_LAYOUT_TEXTEDIT= 0x8000

    
    private const val MASK_LANGUAGE = 0x00f0

    
    const val MASK_LANGUAGE_CN = 0x0010

    
    private const val MASK_LANGUAGE_EN = 0x0020

    
    private const val MASK_CASE = 0x000f

    
    private const val MASK_CASE_LOWER = 0x0000

    
    const val MASK_CASE_UPPER = 0x0001

    
    private const val MASK_CASE_UPPER_LOCK = 0x0002

    
    const val MODE_T9_CHINESE = MASK_SKB_LAYOUT_T9_PINYIN or MASK_LANGUAGE_CN

    
    const val MODE_HANDWRITING_CHINESE = MASK_SKB_LAYOUT_HANDWRITING or MASK_LANGUAGE_CN

    
    private const val MODE_SKB_ENGLISH_LOWER =
        MASK_SKB_LAYOUT_QWERTY_ABC or MASK_LANGUAGE_EN or MASK_CASE_LOWER

    
    private const val MODE_SKB_ENGLISH_UPPER =
        MASK_SKB_LAYOUT_QWERTY_ABC or MASK_LANGUAGE_EN or MASK_CASE_UPPER

    
    private const val MODE_SKB_ENGLISH_UPPER_LOCK =
        MASK_SKB_LAYOUT_QWERTY_ABC or MASK_LANGUAGE_EN or MASK_CASE_UPPER_LOCK

    
    private const val MODE_UNSET = 0
    
    private var mInputMode = MODE_UNSET

    
    private var mRecentLauageInputMode = MODE_UNSET

    
	@JvmField
	val mToggleStates = ToggleStates()

    
    class ToggleStates {
        @JvmField
        var charCase = MASK_CASE_LOWER
        @JvmField
		var mStateEnter = 0
    }

    init {
        mInputMode = getInstance().internal.inputDefaultMode.getValue()
    }

    val skbImeLayout: Int
        
        get() = mRecentLauageInputMode and MASK_SKB_LAYOUT

    val skbLayout: Int
        
        get() = mInputMode and MASK_SKB_LAYOUT

    private var lsatClickTime = 0L

    private var isChineseMode = true
    
    fun switchModeForUserKey(userKey: Int) {
        var newInputMode = MODE_UNSET
        if (USER_DEF_KEYCODE_SHIFT_1 == userKey) {
            if(isChinese && !isChineseMode)isChineseMode = true
            newInputMode = if(System.currentTimeMillis() - lsatClickTime < 300){
                MODE_SKB_ENGLISH_UPPER_LOCK
            } else if (MODE_SKB_ENGLISH_LOWER == mInputMode) {
                MODE_SKB_ENGLISH_UPPER
            } else if (MODE_SKB_ENGLISH_UPPER == mInputMode || MODE_SKB_ENGLISH_UPPER_LOCK == mInputMode){
                if(isChineseMode) getInstance().internal.inputMethodPinyinMode.getValue() else MODE_SKB_ENGLISH_LOWER
            } else {
                MODE_SKB_ENGLISH_LOWER
            }
            lsatClickTime = System.currentTimeMillis()
        } else if (USER_DEF_KEYCODE_LANG_2 == userKey) {
            newInputMode = if (isChinese) {
                isChineseMode = false
                MODE_SKB_ENGLISH_LOWER
            } else {
                getInstance().internal.inputMethodPinyinMode.getValue()
            }
        } else if (USER_DEF_KEYCODE_NUMBER_5 == userKey) {
            newInputMode = MASK_SKB_LAYOUT_NUMBER
        } else if (USER_DEF_KEYCODE_TEXTEDIT_7 == userKey) {
            newInputMode = MASK_SKB_LAYOUT_TEXTEDIT
        } else if (USER_DEF_KEYCODE_RETURN_6 == userKey) {
            newInputMode = if (mRecentLauageInputMode != 0) mRecentLauageInputMode else getInstance().internal.inputMethodPinyinMode.getValue()
        }
        saveInputMode(newInputMode)
        KeyboardManager.instance.switchKeyboard()
    }

    
    fun requestInputWithSkb(editorInfo: EditorInfo) {
        var newInputMode = MODE_UNSET
        when (editorInfo.inputType and EditorInfo.TYPE_MASK_CLASS) {
            EditorInfo.TYPE_CLASS_NUMBER, EditorInfo.TYPE_CLASS_PHONE, EditorInfo.TYPE_CLASS_DATETIME -> newInputMode = MASK_SKB_LAYOUT_NUMBER
            else -> {
                val v = editorInfo.inputType and EditorInfo.TYPE_MASK_VARIATION
                newInputMode = if (v == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    || v == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
                    || v == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    || v == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD) {
                        MODE_SKB_ENGLISH_LOWER
                    } else if(getInstance().keyboardSetting.keyboardLockEnglish.getValue()){
                        getInstance().internal.inputDefaultMode.getValue()
                    } else{
                        getInstance().internal.inputMethodPinyinMode.getValue()
                    }
            }
        }
        val action = editorInfo.imeOptions and (EditorInfo.IME_MASK_ACTION or EditorInfo.IME_FLAG_NO_ENTER_ACTION)
        var enterState = 0
        when (action) {
            EditorInfo.IME_ACTION_SEARCH -> enterState = 1
            EditorInfo.IME_ACTION_SEND -> enterState = 2
            EditorInfo.IME_ACTION_NEXT -> {
                val f = editorInfo.inputType and EditorInfo.TYPE_MASK_FLAGS
                if (f != EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) {
                    enterState = 3
                }
            }
            EditorInfo.IME_ACTION_DONE -> enterState = 4
        }
        mToggleStates.mStateEnter = enterState
        if (newInputMode != mInputMode && MODE_UNSET != newInputMode) {
            saveInputMode(newInputMode)
        }
    }

    val isNumberSkb: Boolean
        
        get() = mInputMode and MASK_SKB_LAYOUT == MASK_SKB_LAYOUT_NUMBER

    val isTextEditSkb: Boolean
        get() = mInputMode and MASK_SKB_LAYOUT == MASK_SKB_LAYOUT_TEXTEDIT

    val isChinese: Boolean
        
        get() = mInputMode and MASK_LANGUAGE == MASK_LANGUAGE_CN
    val isChineseT9: Boolean
        
        get() = mInputMode and (MASK_SKB_LAYOUT or MASK_LANGUAGE) == MODE_T9_CHINESE

    val isQwert: Boolean
        
        get() = mInputMode and MASK_SKB_LAYOUT == MASK_SKB_LAYOUT_QWERTY_PINYIN || mInputMode and MASK_SKB_LAYOUT == MASK_SKB_LAYOUT_QWERTY_ABC

    val isChineseHandWriting: Boolean
        
        get() = mInputMode and (MASK_SKB_LAYOUT or MASK_LANGUAGE) == MODE_HANDWRITING_CHINESE
    val isEnglish: Boolean
        
        get() = mInputMode and MASK_LANGUAGE == MASK_LANGUAGE_EN
    val isEnglishLower: Boolean
        
        get() = mInputMode and (MASK_SKB_LAYOUT or MASK_LANGUAGE or MASK_CASE) == MODE_SKB_ENGLISH_LOWER
    val isEnglishUpperCase: Boolean
        
        get() = mInputMode and (MASK_SKB_LAYOUT or MASK_LANGUAGE or MASK_CASE) == MODE_SKB_ENGLISH_UPPER

    val isEnglishUpperLockCase: Boolean
        
        get() = mInputMode and (MASK_SKB_LAYOUT or MASK_LANGUAGE or MASK_CASE) == MODE_SKB_ENGLISH_UPPER_LOCK

    
    fun saveInputMode(newInputMode: Int) {
        mInputMode = newInputMode
        if (isEnglish) {
            val charCase = mInputMode and MASK_CASE
            mToggleStates.charCase = charCase
            Kernel.initImeSchema(CustomConstant.SCHEMA_EN)
        } else {
            Kernel.initImeSchema(getInstance().internal.pinyinModeRime.getValue())
        }
        if (isChinese || isEnglish) {
            mRecentLauageInputMode = mInputMode
            getInstance().internal.inputDefaultMode.setValue(mInputMode)
        }
    }

}