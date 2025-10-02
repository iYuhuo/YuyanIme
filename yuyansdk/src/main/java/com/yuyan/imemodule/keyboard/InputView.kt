﻿package com.yuyan.imemodule.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get
import androidx.core.view.postDelayed
import com.yuyan.imemodule.R
import com.yuyan.imemodule.application.CustomConstant
import com.yuyan.imemodule.callback.CandidateViewListener
import com.yuyan.imemodule.callback.IResponseKeyEvent
import com.yuyan.imemodule.data.emojicon.EmojiconData.SymbolPreset
import com.yuyan.imemodule.data.theme.ThemeManager
import com.yuyan.imemodule.database.DataBaseKT
import com.yuyan.imemodule.entity.keyboard.SoftKey
import com.yuyan.imemodule.keyboard.container.CandidatesContainer
import com.yuyan.imemodule.keyboard.container.ClipBoardContainer
import com.yuyan.imemodule.keyboard.container.SymbolContainer
import com.yuyan.imemodule.keyboard.container.T9TextContainer
import com.yuyan.imemodule.manager.InputModeSwitcherManager
import com.yuyan.imemodule.prefs.AppPrefs.Companion.getInstance
import com.yuyan.imemodule.prefs.behavior.KeyboardOneHandedMod
import com.yuyan.imemodule.prefs.behavior.PopupMenuMode
import com.yuyan.imemodule.prefs.behavior.SkbMenuMode
import com.yuyan.imemodule.service.DecodingInfo
import com.yuyan.imemodule.service.ImeService
import com.yuyan.imemodule.singleton.EnvironmentSingleton
import com.yuyan.imemodule.utils.DevicesUtils
import com.yuyan.imemodule.utils.InputMethodUtil
import com.yuyan.imemodule.utils.KeyboardLoaderUtil
import com.yuyan.imemodule.utils.StringUtils
import com.yuyan.imemodule.view.CandidatesBar
import com.yuyan.imemodule.view.EditPhrasesView
import com.yuyan.imemodule.view.FullDisplayKeyboardBar
import com.yuyan.imemodule.view.popup.PopupComponent
import com.yuyan.imemodule.view.preference.ManagedPreference
import com.yuyan.imemodule.view.widget.LifecycleRelativeLayout
import com.yuyan.inputmethod.CustomEngine
import com.yuyan.inputmethod.core.CandidateListItem
import com.yuyan.inputmethod.core.Kernel
import splitties.views.bottomPadding
import splitties.views.rightPadding
import kotlin.math.absoluteValue

@SuppressLint("ViewConstructor")
class InputView(context: Context, service: ImeService) : LifecycleRelativeLayout(context), IResponseKeyEvent {
    private val clipboardItemTimeout = getInstance().clipboard.clipboardItemTimeout.getValue()
    private var chinesePrediction = true
    var isAddPhrases = false
    private var service: ImeService
    private var mImeState = ImeState.STATE_IDLE
    private var mChoiceNotifier = ChoiceNotifier()
    var mSkbRoot: RelativeLayout
    var mSkbCandidatesBarView: CandidatesBar
    private var mHoderLayoutLeft: LinearLayout
    private var mHoderLayoutRight: LinearLayout
    private lateinit var mOnehandHoderLayout: LinearLayout
    var mAddPhrasesLayout: EditPhrasesView
    private var mLlKeyboardBottomHolder: LinearLayout
    private var mInputKeyboardContainer: RelativeLayout
    private lateinit var mRightPaddingKey: ManagedPreference.PInt
    private lateinit var mBottomPaddingKey: ManagedPreference.PInt
    private var mFullDisplayKeyboardBar:FullDisplayKeyboardBar? = null
    var hasSelection = false
    var hasSelectionAll = false

    init {
        initNavbarBackground(service)
        this.service = service
        mSkbRoot = LayoutInflater.from(context).inflate(R.layout.sdk_skb_container, this, false) as RelativeLayout
        addView(mSkbRoot)
        mSkbCandidatesBarView = mSkbRoot.findViewById(R.id.candidates_bar)
        mHoderLayoutLeft = mSkbRoot.findViewById(R.id.ll_skb_holder_layout_left)
        mHoderLayoutRight = mSkbRoot.findViewById(R.id.ll_skb_holder_layout_right)
        mInputKeyboardContainer = mSkbRoot.findViewById(R.id.ll_input_keyboard_container)
        mAddPhrasesLayout = EditPhrasesView(context)
        KeyboardManager.instance.setData(mSkbRoot.findViewById(R.id.skb_input_keyboard_view), this)
        mLlKeyboardBottomHolder =  mSkbRoot.findViewById(R.id.iv_keyboard_holder)
        val root = PopupComponent.get().root
        val viewParent = root.parent
        if (viewParent != null) {
            (viewParent as ViewGroup).removeView(root)
        }
        addView(root, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            addRule(ALIGN_BOTTOM, mSkbRoot.id)
            addRule(ALIGN_LEFT, mSkbRoot.id)
        })
        initView(context)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun initView(context: Context) {
        if(isAddPhrases){
            if(mAddPhrasesLayout.parent == null) {
                addView(mAddPhrasesLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    addRule(ABOVE, mSkbRoot.id)
                    addRule(ALIGN_LEFT, mSkbRoot.id)
                })
                mAddPhrasesLayout.handleAddPhrasesView()
            }
        } else {
            removeView(mAddPhrasesLayout)
        }
        mSkbCandidatesBarView.initialize(mChoiceNotifier)
        val oneHandedModSwitch = getInstance().keyboardSetting.oneHandedModSwitch.getValue()
        val oneHandedMod = getInstance().keyboardSetting.oneHandedMod.getValue()
        if(::mOnehandHoderLayout.isInitialized)mOnehandHoderLayout.visibility = GONE
        if (oneHandedModSwitch) {
            mOnehandHoderLayout = when(oneHandedMod){
                KeyboardOneHandedMod.LEFT ->  mHoderLayoutRight
                else -> mHoderLayoutLeft
            }
            mOnehandHoderLayout.visibility = VISIBLE
            mOnehandHoderLayout[0].setOnClickListener { view: View -> onClick(view) }
            mOnehandHoderLayout[1].setOnClickListener { view: View -> onClick(view) }
            (mOnehandHoderLayout[1] as ImageButton).setImageResource(if (oneHandedMod == KeyboardOneHandedMod.LEFT) R.drawable.ic_menu_one_hand_right else R.drawable.ic_menu_one_hand)
            val layoutParamsHoder = mOnehandHoderLayout.layoutParams
            layoutParamsHoder.width = EnvironmentSingleton.instance.holderWidth
            layoutParamsHoder.height = EnvironmentSingleton.instance.skbHeight
        }
        mLlKeyboardBottomHolder.removeAllViews()
        mLlKeyboardBottomHolder.layoutParams.width = EnvironmentSingleton.instance.skbWidth
        mInputKeyboardContainer.layoutParams.width = EnvironmentSingleton.instance.inputAreaWidth
        if(EnvironmentSingleton.instance.keyboardModeFloat){
            mBottomPaddingKey = (if(EnvironmentSingleton.instance.isLandscape) getInstance().internal.keyboardBottomPaddingLandscapeFloat
                else getInstance().internal.keyboardBottomPaddingFloat)
            mRightPaddingKey = (if(EnvironmentSingleton.instance.isLandscape) getInstance().internal.keyboardRightPaddingLandscapeFloat
            else getInstance().internal.keyboardRightPaddingFloat)
            bottomPadding = mBottomPaddingKey.getValue()
            rightPadding = mRightPaddingKey.getValue()
            mSkbRoot.bottomPadding = 0
            mSkbRoot.rightPadding = 0
            mLlKeyboardBottomHolder.minimumHeight = EnvironmentSingleton.instance.heightForKeyboardMove
            val mIvKeyboardMove = ImageView(context).apply {
                setImageResource(R.drawable.ic_horizontal_line)
                isClickable = true
                isEnabled = true
            }
            mLlKeyboardBottomHolder.addView(mIvKeyboardMove)
            mIvKeyboardMove.setOnTouchListener { _, event -> onMoveKeyboardEvent(event) }
        } else {
            val fullDisplayKeyboardEnable = getInstance().internal.fullDisplayKeyboardEnable.getValue()
            if(fullDisplayKeyboardEnable && !EnvironmentSingleton.instance.isLandscape){
                mFullDisplayKeyboardBar = FullDisplayKeyboardBar(context, this)
                mLlKeyboardBottomHolder.addView(mFullDisplayKeyboardBar)
                mLlKeyboardBottomHolder.minimumHeight = EnvironmentSingleton.instance.heightForFullDisplayBar + EnvironmentSingleton.instance.systemNavbarWindowsBottom
            } else {
                mLlKeyboardBottomHolder.minimumHeight = EnvironmentSingleton.instance.systemNavbarWindowsBottom
            }
            bottomPadding = 0
            rightPadding = 0
            mBottomPaddingKey =  getInstance().internal.keyboardBottomPadding
            mRightPaddingKey =  getInstance().internal.keyboardRightPadding
            mSkbRoot.bottomPadding = mBottomPaddingKey.getValue()
            mSkbRoot.rightPadding = mRightPaddingKey.getValue()
        }
        updateTheme()
        DecodingInfo.candidatesLiveData.observe( this){ _ ->
            updateCandidateBar()
            (KeyboardManager.instance.currentContainer as? CandidatesContainer)?.showCandidatesView()
        }
    }

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var rightPaddingValue = 0
    private var bottomPaddingValue = 0
    private fun onMoveKeyboardEvent(event: MotionEvent?): Boolean {
        when (event?.action) {
            MotionEvent.ACTION_DOWN -> {
                bottomPaddingValue = mBottomPaddingKey.getValue()
                rightPaddingValue = mRightPaddingKey.getValue()
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx: Float = event.rawX - initialTouchX
                val dy: Float = event.rawY - initialTouchY
                if(dx.absoluteValue > 10) {
                    rightPaddingValue -= dx.toInt()
                    rightPaddingValue = if(rightPaddingValue < 0) 0
                    else if(rightPaddingValue > EnvironmentSingleton.instance.mScreenWidth - mSkbRoot.width) {
                        EnvironmentSingleton.instance.mScreenWidth - mSkbRoot.width
                    } else rightPaddingValue
                    initialTouchX = event.rawX
                    if(EnvironmentSingleton.instance.keyboardModeFloat) {
                        rightPadding = rightPaddingValue
                    } else {
                        mSkbRoot.rightPadding = rightPaddingValue
                    }
                }
                if(dy.absoluteValue > 10 ) {
                    bottomPaddingValue -= dy.toInt()
                    bottomPaddingValue = if(bottomPaddingValue < 0) 0
                    else if(bottomPaddingValue > EnvironmentSingleton.instance.mScreenHeight - mSkbRoot.height) {
                        EnvironmentSingleton.instance.mScreenHeight - mSkbRoot.height
                    } else bottomPaddingValue
                    initialTouchY = event.rawY
                    if(EnvironmentSingleton.instance.keyboardModeFloat) {
                        bottomPadding = bottomPaddingValue
                    } else {
                        mSkbRoot.bottomPadding = bottomPaddingValue
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mRightPaddingKey.setValue(rightPaddingValue)
                mBottomPaddingKey.setValue(bottomPaddingValue)
            }
        }
        return false
    }

    fun updateTheme() {
        setBackgroundResource(android.R.color.transparent)
        val keyTextColor = ThemeManager.activeTheme.keyTextColor
        val backgrounde = ThemeManager.activeTheme.backgroundDrawable(ThemeManager.prefs.keyBorder.getValue())
        mSkbRoot.background = if(backgrounde is BitmapDrawable) backgrounde.bitmap.scale(EnvironmentSingleton.instance.skbWidth, EnvironmentSingleton.instance.inputAreaHeight).toDrawable(context.resources) else backgrounde
        mSkbCandidatesBarView.updateTheme(keyTextColor)
        if(::mOnehandHoderLayout.isInitialized) {
            (mOnehandHoderLayout[0] as ImageButton).drawable?.setTint(keyTextColor)
            (mOnehandHoderLayout[1] as ImageButton).drawable?.setTint(keyTextColor)
        }
        mFullDisplayKeyboardBar?.updateTheme(keyTextColor)
        mAddPhrasesLayout.updateTheme(ThemeManager.activeTheme)
    }

    private fun onClick(view: View) {
        if (view.id == R.id.ib_holder_one_hand_none) {
            getInstance().keyboardSetting.oneHandedModSwitch.setValue(!getInstance().keyboardSetting.oneHandedModSwitch.getValue())
        } else {
            val oneHandedMod = getInstance().keyboardSetting.oneHandedMod.getValue()
            getInstance().keyboardSetting.oneHandedMod.setValue(if (oneHandedMod == KeyboardOneHandedMod.LEFT) KeyboardOneHandedMod.RIGHT else KeyboardOneHandedMod.LEFT)
        }
        EnvironmentSingleton.instance.initData()
        KeyboardLoaderUtil.instance.clearKeyboardMap()
        KeyboardManager.instance.clearKeyboard()
        KeyboardManager.instance.switchKeyboard()
    }

    
    override fun responseKeyEvent(sKey: SoftKey) {
        val keyCode = sKey.code
        if (sKey.isKeyCodeKey) {
            mImeState = ImeState.STATE_INPUT
            val rimeSchema = Kernel.getCurrentRimeSchema()
            val metaState = if(rimeSchema in  listOf(CustomConstant.SCHEMA_ZH_T9, CustomConstant.SCHEMA_ZH_STROKE,
                    CustomConstant.SCHEMA_ZH_DOUBLE_LX17))KeyEvent.META_CAPS_LOCK_ON else 0
            val keyEvent = KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, metaState, 0, 0, KeyEvent.FLAG_SOFT_KEYBOARD)
            processKey(keyEvent)
        } else if (sKey.isUserDefKey || sKey.isUniStrKey) {
            if (!DecodingInfo.isAssociate && !DecodingInfo.isCandidatesListEmpty) {
                if(InputModeSwitcherManager.isChinese)   chooseAndUpdate()
                else if(InputModeSwitcherManager.isEnglish)  commitDecInfoText(DecodingInfo.composingStrForCommit)
            }
            if (InputModeSwitcherManager.USER_DEF_KEYCODE_SYMBOL_3 == keyCode) {
                KeyboardManager.instance.switchKeyboard(KeyboardManager.KeyboardType.SYMBOL)
                (KeyboardManager.instance.currentContainer as? SymbolContainer)?.setSymbolsView()
            } else  if (InputModeSwitcherManager.USER_DEF_KEYCODE_EMOJI_4 == keyCode) {
                onSettingsMenuClick(SkbMenuMode.Emojicon)
            } else  if (InputModeSwitcherManager.USER_DEF_KEYCODE_SHIFT_1 == keyCode) {
                if(InputModeSwitcherManager.isChineseT9){
                    InputModeSwitcherManager.switchModeForUserKey(InputModeSwitcherManager.USER_DEF_KEYCODE_NUMBER_5)
                } else  if(InputModeSwitcherManager.isNumberSkb){
                    InputModeSwitcherManager.switchModeForUserKey(InputModeSwitcherManager.USER_DEF_KEYCODE_RETURN_6)
                } else {
                    InputModeSwitcherManager.switchModeForUserKey(keyCode)
                }
            } else if ( keyCode in InputModeSwitcherManager.USER_DEF_KEYCODE_RETURN_6 .. InputModeSwitcherManager.USER_DEF_KEYCODE_SHIFT_1) {
                InputModeSwitcherManager.switchModeForUserKey(keyCode)
            } else if ( keyCode in InputModeSwitcherManager.USER_DEF_KEYCODE_PASTE .. InputModeSwitcherManager.USER_DEF_KEYCODE_CUT) {
                commitTextEditMenu(KeyPreset.textEditMenuPreset[keyCode])
            } else if ( keyCode == InputModeSwitcherManager.USER_DEF_KEYCODE_MOVE_START) {
                service.setSelection(0, if(hasSelection) selEnd else 0)
            } else if ( keyCode == InputModeSwitcherManager.USER_DEF_KEYCODE_MOVE_END) {
                if(hasSelection) {
                    val start =  selStart
                    commitTextEditMenu(KeyPreset.textEditMenuPreset[InputModeSwitcherManager.USER_DEF_KEYCODE_SELECT_ALL])
                    this.postDelayed(50) { service.setSelection(start, selEnd) }
                } else {
                    commitTextEditMenu(KeyPreset.textEditMenuPreset[InputModeSwitcherManager.USER_DEF_KEYCODE_SELECT_ALL])
                    service.sendCombinationKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
                }
            } else if ( keyCode == InputModeSwitcherManager.USER_DEF_KEYCODE_SELECT_MODE) {
                hasSelection = !hasSelection
                if(!hasSelection)service.sendCombinationKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
            } else if ( keyCode == InputModeSwitcherManager.USER_DEF_KEYCODE_SELECT_ALL) {
                hasSelectionAll = !hasSelectionAll
                if(!hasSelectionAll) service.sendCombinationKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
                else commitTextEditMenu(KeyPreset.textEditMenuPreset[keyCode])
            }else if(sKey.keyLabel.isNotBlank()){
                if(SymbolPreset.containsKey(sKey.keyLabel))commitPairSymbol(sKey.keyLabel)
                else commitText(sKey.keyLabel)
            }
            if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
        }
    }

    private var textBeforeCursor:String = ""

    
    override fun responseLongKeyEvent(result:Pair<PopupMenuMode, String>) {
        if (!DecodingInfo.isAssociate && !DecodingInfo.isCandidatesListEmpty) {
            if(InputModeSwitcherManager.isChinese) {
                chooseAndUpdate()
            } else if(InputModeSwitcherManager.isEnglish){
                commitDecInfoText(DecodingInfo.composingStrForCommit)
            }
        }
        when(result.first){
            PopupMenuMode.Text -> {
                if(SymbolPreset.containsKey(result.second))commitPairSymbol(result.second)
                else commitText(result.second)
            }
            PopupMenuMode.SwitchIME -> InputMethodUtil.showPicker()
            PopupMenuMode.EMOJI -> {
                onSettingsMenuClick(SkbMenuMode.Emojicon)
            }
            PopupMenuMode.EnglishCell -> {
                getInstance().input.abcSearchEnglishCell.setValue(!getInstance().input.abcSearchEnglishCell.getValue())
                KeyboardManager.instance.switchKeyboard()
            }
            PopupMenuMode.Clear -> {
                if(isAddPhrases) mAddPhrasesLayout.clearPhrasesContent()
                else {
                    val clearText = service.getTextBeforeCursor(1).toString()
                    if(clearText.isNotEmpty()){
                        textBeforeCursor = clearText
                        service.deleteSurroundingText(1000)
                    }
                }
            }
            PopupMenuMode.Revertl -> {
                commitText(textBeforeCursor)
                textBeforeCursor = ""
            }
            PopupMenuMode.Enter ->  commitText("\n")
            else -> {}
        }
        if(result.first == PopupMenuMode.Text && mImeState != ImeState.STATE_PREDICT) resetToPredictState()
        else if(result.first != PopupMenuMode.None && mImeState != ImeState.STATE_IDLE) resetToIdleState()
    }

    override fun responseHandwritingResultEvent(words: Array<CandidateListItem>) {
        DecodingInfo.isAssociate = false
        DecodingInfo.cacheCandidates(words)
        mImeState = ImeState.STATE_INPUT
        updateCandidateBar()
    }

    
    fun processKey(event: KeyEvent): Boolean {
        if (processFunctionKeys(event)) return true
        val englishCellDisable = InputModeSwitcherManager.isEnglish && !getInstance().input.abcSearchEnglishCell.getValue()
        val result = if(englishCellDisable){
            processEnglishKey(event)
        } else if (InputModeSwitcherManager.isEnglish || InputModeSwitcherManager.isChinese) {
            processInput(event)
        } else {
            processEnglishKey(event)
        }
        return result
    }

    
    private fun processEnglishKey(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        var keyChar = event.unicodeChar
        val lable = keyChar.toChar().toString()
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            sendKeyEvent(keyCode)
            if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
            return true
        } else if(keyCode in (KeyEvent.KEYCODE_A .. KeyEvent.KEYCODE_Z) ){
            if (!InputModeSwitcherManager.isEnglishLower) keyChar = keyChar - 'a'.code + 'A'.code
            commitText(keyChar.toChar().toString())
            return true
        } else if (keyCode != 0) {
            sendKeyEvent(keyCode)
            if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
            return true
        } else if (lable.isNotEmpty()) {
            if(SymbolPreset.containsKey(lable))commitPairSymbol(lable)
            else commitText(lable)
            return true
        }
        return false
    }

    
    private fun processFunctionKeys(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (service.isInputViewShown) {
                requestHideSelf()
                return true
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_SPACE) {
            if (DecodingInfo.isFinish || (DecodingInfo.isAssociate && !mSkbCandidatesBarView.isActiveCand())) {
                sendKeyEvent(keyCode)
                if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
            } else {
                chooseAndUpdate()
            }
            return true
        } else if (keyCode == KeyEvent.KEYCODE_CLEAR) {
            if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
            return true
        }  else if (keyCode == KeyEvent.KEYCODE_ENTER) {
            if (DecodingInfo.isFinish || DecodingInfo.isAssociate) {
                sendKeyEvent(keyCode)
            } else {
                commitDecInfoText(DecodingInfo.composingStrForCommit)
            }
            if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if(event.flags != KeyEvent.FLAG_SOFT_KEYBOARD && !DecodingInfo.isCandidatesListEmpty) {
                mSkbCandidatesBarView.updateActiveCandidateNo(keyCode)
            } else if (DecodingInfo.isFinish || DecodingInfo.isAssociate) {
                sendKeyEvent(keyCode)
            } else {
                chooseAndUpdate()
            }
            return  true
        }
        return false
    }

    
    private fun processInput(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val keyChar = event.unicodeChar
        val lable = keyChar.toChar().toString()
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            if (DecodingInfo.isFinish || DecodingInfo.isAssociate) {
                sendKeyEvent(keyCode)
                if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
            } else {
                DecodingInfo.deleteAction()
                updateCandidate()
            }
            return true
        } else if ((Character.isLetterOrDigit(keyChar) && keyCode != KeyEvent.KEYCODE_0) || keyCode == KeyEvent.KEYCODE_APOSTROPHE || keyCode == KeyEvent.KEYCODE_SEMICOLON){
            DecodingInfo.inputAction(event)
            updateCandidate()
            return true
        } else if (keyCode != 0) {
            if (!DecodingInfo.isCandidatesListEmpty && !DecodingInfo.isAssociate) {
                chooseAndUpdate()
            }
            sendKeyEvent(keyCode)
            if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
            return true
        } else if(lable.isNotEmpty()) {
            if (!DecodingInfo.isCandidatesListEmpty && !DecodingInfo.isAssociate) {
                chooseAndUpdate()
            }
            if(SymbolPreset.containsKey(lable))commitPairSymbol(lable)
            else commitText(lable)
            return true
        }
        return false
    }

    
    fun resetToIdleState() {
        resetCandidateWindow()
        if(hasSelectionAll) hasSelectionAll = false
        mImeState = ImeState.STATE_IDLE
    }

    
    private fun resetToPredictState() {
        resetCandidateWindow()
        mImeState = ImeState.STATE_PREDICT
    }

    
    fun chooseAndUpdate(candId: Int = mSkbCandidatesBarView.getActiveCandNo()) {
        val candidate = DecodingInfo.getCandidate(candId)
        if(candidate?.comment == "📋"){
            commitDecInfoText(candidate.text)
            if(mImeState != ImeState.STATE_PREDICT)resetToPredictState()
        } else {
            val choice = DecodingInfo.chooseDecodingCandidate(candId)
            if (DecodingInfo.isEngineFinish || DecodingInfo.isAssociate) {
                commitDecInfoText(choice)
                KeyboardManager.instance.switchKeyboard(InputModeSwitcherManager.skbLayout)
                (KeyboardManager.instance.currentContainer as? T9TextContainer)?.updateSymbolListView()
                if(mImeState != ImeState.STATE_PREDICT)resetToPredictState()
            } else {
                if (!DecodingInfo.isFinish) {
                    if (InputModeSwitcherManager.isEnglish) setComposingText(DecodingInfo.composingStrForCommit)
                    updateCandidateBar()
                    (KeyboardManager.instance.currentContainer as? T9TextContainer)?.updateSymbolListView()
                } else {
                    if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
                }
            }
        }
    }

    
    private fun updateCandidate() {
        DecodingInfo.updateDecodingCandidate()
        if (!DecodingInfo.isFinish) {
            updateCandidateBar()
            (KeyboardManager.instance.currentContainer as? T9TextContainer)?.updateSymbolListView()
        } else {
            if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
        }
        if (InputModeSwitcherManager.isEnglish)setComposingText(DecodingInfo.composingStrForCommit)
    }

    
    fun updateCandidateBar() {
        mSkbCandidatesBarView.showCandidates()
    }

    
    private fun resetCandidateWindow() {
        DecodingInfo.reset()
        updateCandidateBar()
        (KeyboardManager.instance.currentContainer as? T9TextContainer)?.updateSymbolListView()
    }

    
    inner class ChoiceNotifier internal constructor() : CandidateViewListener {
        override fun onClickChoice(choiceId: Int) {
            DevicesUtils.tryPlayKeyDown()
            DevicesUtils.tryVibrate(KeyboardManager.instance.currentContainer)
            chooseAndUpdate(choiceId)
        }

        override fun onClickMore(level: Int) {
            if (level == 0) {
                onSettingsMenuClick(SkbMenuMode.CandidatesMore)
            } else {
                KeyboardManager.instance.switchKeyboard()
                (KeyboardManager.instance.currentContainer as? T9TextContainer)?.updateSymbolListView()
            }
        }

        override fun onClickMenu(skbMenuMode: SkbMenuMode) {
            onSettingsMenuClick(skbMenuMode)
        }

        override fun onClickClearCandidate() {
            if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
            KeyboardManager.instance.switchKeyboard()
        }

        override fun onClickClearClipBoard() {
            DataBaseKT.instance.clipboardDao().deleteAll()
            (KeyboardManager.instance.currentContainer as? ClipBoardContainer)?.showClipBoardView(SkbMenuMode.ClipBoard)
        }
    }

    fun onSettingsMenuClick(skbMenuMode: SkbMenuMode, extra:String = "") {
        when (skbMenuMode) {
            SkbMenuMode.AddPhrases -> {
                isAddPhrases = true
                DataBaseKT.instance.phraseDao().deleteByContent(extra)
                KeyboardManager.instance.switchKeyboard(InputModeSwitcherManager.skbImeLayout)
                initView(context)
                mAddPhrasesLayout.setExtraData(extra)
            }
            else ->onSettingsMenuClick(this, skbMenuMode)
        }
        mSkbCandidatesBarView.initMenuView()
    }

    
    enum class ImeState {
        STATE_IDLE, STATE_INPUT, STATE_PREDICT
    }

    
    fun selectPrefix(position: Int) {
        DevicesUtils.tryPlayKeyDown()
        DevicesUtils.tryVibrate(this)
        DecodingInfo.selectPrefix(position)
        updateCandidate()
    }

    fun showSymbols(symbols: Array<String>) {
        mImeState = ImeState.STATE_INPUT
        val list = symbols.map { symbol-> CandidateListItem("📋", symbol) }.toTypedArray()
        DecodingInfo.cacheCandidates(list)
        DecodingInfo.isAssociate = true
        updateCandidateBar()
    }

    fun requestHideSelf() {
        service.requestHideSelf(0)
    }

    
    private fun sendKeyEvent(keyCode: Int) {
        if(isAddPhrases){
            mAddPhrasesLayout.sendKeyEvent(keyCode)
            when(keyCode){
                KeyEvent.KEYCODE_ENTER ->{
                    isAddPhrases = false
                    initView(context)
                    onSettingsMenuClick(SkbMenuMode.Phrases)
                }
            }
        } else if(keyCode == KeyEvent.KEYCODE_ENTER) {
            service.sendEnterKeyEvent()
        } else if(keyCode in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT) {
            service.sendCombinationKeyEvents(keyCode, shift = hasSelection)
            if(hasSelectionAll) hasSelectionAll = false
        } else {
            service.sendCombinationKeyEvents(keyCode)
        }
    }

    
    private fun setComposingText(text: CharSequence) {
        if(!isAddPhrases)service.setComposingText(text)
    }

    
    private fun commitText(text: String) {
        if(isAddPhrases) mAddPhrasesLayout.commitText(text)
        else service.commitText(StringUtils.converted2FlowerTypeface(text))
    }

    
    private fun commitPairSymbol(text: String) {
        if(isAddPhrases) {
            mAddPhrasesLayout.commitText(text)
        } else {
            if(getInstance().input.symbolPairInput.getValue()) {
                service.commitText(text + SymbolPreset[text]!!)
                service.sendCombinationKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
            } else service.commitText(text)
        }
    }

    
    private fun commitTextEditMenu(id:Int?) {
        if(id != null)service.commitTextEditMenu(id)
    }

    
    fun directCommitText(text: String) {
        if (!DecodingInfo.isAssociate && !DecodingInfo.isCandidatesListEmpty) {
            if(InputModeSwitcherManager.isChinese) {
                chooseAndUpdate()
            } else if(InputModeSwitcherManager.isEnglish){
                commitDecInfoText(DecodingInfo.composingStrForCommit)
            }
        }
        service.commitText(text)
        resetToIdleState()
    }
    
    
    fun setPartialText(text: String) {
        val ic = service.currentInputConnection ?: return
        ic.setComposingText(text, 1)
    }
    
    
    fun finishPartialText() {
        val ic = service.currentInputConnection ?: return
        ic.finishComposingText()
    }
    
    
    fun clearPartialText() {
        val ic = service.currentInputConnection ?: return
        ic.setComposingText("", 1)
    }
    
    
    fun getInputConnection(): android.view.inputmethod.InputConnection? {
        return service.currentInputConnection
    }

    
    fun performEditorAction(editorAction:Int) {
        service.performEditorAction(editorAction)
    }

    
    private fun commitDecInfoText(resultText: String?) {
        if(resultText == null) return
        if(isAddPhrases){
            mAddPhrasesLayout.commitText(resultText)
        } else {
            service.commitText(StringUtils.converted2FlowerTypeface(resultText))
            if (InputModeSwitcherManager.isEnglish && DecodingInfo.isEngineFinish && getInstance().input.abcSpaceAuto.getValue() && StringUtils.isEnglishWord(resultText)) {
                service.commitText(" ")
            }
        }
    }

    private fun initNavbarBackground(service: ImeService) {
        service.window.window!!.also {
            WindowCompat.setDecorFitsSystemWindows(it, false)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R){
                @Suppress("DEPRECATION")
                it.navigationBarColor = Color.TRANSPARENT
            } else {
                it.insetsController?.hide(WindowInsets.Type.navigationBars())
                it.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) it.isNavigationBarContrastEnforced = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            EnvironmentSingleton.instance.systemNavbarWindowsBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val fullDisplayKeyboardEnable = getInstance().internal.fullDisplayKeyboardEnable.getValue()
            mLlKeyboardBottomHolder.minimumHeight = if(EnvironmentSingleton.instance.keyboardModeFloat)  0
            else if(fullDisplayKeyboardEnable) EnvironmentSingleton.instance.heightForFullDisplayBar + EnvironmentSingleton.instance.systemNavbarWindowsBottom
            else  EnvironmentSingleton.instance.systemNavbarWindowsBottom
            insets
        }
    }

    @SuppressLint("SimpleDateFormat")
    fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
        InputModeSwitcherManager.requestInputWithSkb(editorInfo)
        KeyboardManager.instance.switchKeyboard()
        resetToIdleState()
        if(!restarting) {
            if (getInstance().clipboard.clipboardSuggestion.getValue()) {
                val lastClipboardTime = getInstance().internal.clipboardUpdateTime.getValue()
                if (System.currentTimeMillis() - lastClipboardTime <= clipboardItemTimeout * 1000) {
                    val lastClipboardContent = getInstance().internal.clipboardUpdateContent.getValue()
                    if (lastClipboardContent.isNotBlank()) {
                        showSymbols(arrayOf(lastClipboardContent))
                        getInstance().internal.clipboardUpdateTime.setValue(0L)
                    }
                }
            }
        }
    }

    fun onWindowShown() {
        chinesePrediction = getInstance().input.chinesePrediction.getValue()
    }

    fun onWindowHidden() {
        if(isAddPhrases){
            isAddPhrases = false
            mAddPhrasesLayout.addPhrasesHandle()
            initView(context)
        }
        if(mImeState != ImeState.STATE_IDLE) resetToIdleState()
    }

    private var selStart = 0
    private var selEnd = 0
    private var oldCandidatesEnd = 0
    fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesEnd: Int) {
        selStart = newSelStart; selEnd = newSelEnd
        if(oldCandidatesEnd == candidatesEnd && InputModeSwitcherManager.isEnglish && !DecodingInfo.isCandidatesListEmpty && !DecodingInfo.isAssociate){
            service.finishComposingText()
            resetToPredictState()
        }
        if(oldSelStart != oldSelEnd || newSelStart != newSelEnd)return
        oldCandidatesEnd = candidatesEnd
        if ((chinesePrediction && InputModeSwitcherManager.isChinese && mImeState != ImeState.STATE_IDLE) || InputModeSwitcherManager.isNumberSkb) {
            val textBeforeCursor = service.getTextBeforeCursor(100)
            if (textBeforeCursor.isNotBlank()) {
                val expressionEnd = CustomEngine.parseExpressionAtEnd(textBeforeCursor)
                if(!expressionEnd.isNullOrBlank()) {
                    if(expressionEnd.length < 100) {
                        val result = CustomEngine.expressionCalculator(textBeforeCursor, expressionEnd)
                        if (result.isNotEmpty()) showSymbols(result)
                    }
                } else if (StringUtils.isChineseEnd(textBeforeCursor)) {
                    DecodingInfo.isAssociate = true
                    DecodingInfo.getAssociateWord(if (textBeforeCursor.length > 10)textBeforeCursor.substring(textBeforeCursor.length - 10) else textBeforeCursor)
                    updateCandidate()
                    updateCandidateBar()
                }
            }
        }
    }
}