package com.yuyan.imemodule.singleton

import com.yuyan.imemodule.application.Launcher
import com.yuyan.imemodule.data.theme.ThemeManager.prefs
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.utils.DevicesUtils
import kotlin.math.max
import kotlin.math.min

class EnvironmentSingleton private constructor() {
    var systemNavbarWindowsBottom = 0
    var mScreenWidth = 0
    var mScreenHeight = 0
    var inputAreaHeight = 0
    var inputAreaWidth = 0
    var skbWidth = 0
        private set
    var skbHeight = 0
        private set
    var holderWidth = 0
        private set
    var heightForCandidatesArea = 0
    var heightForcomposing = 0
    var heightForCandidates = 0
    var heightForFullDisplayBar = 0
    var heightForKeyboardMove = 0
    var keyTextSize = 0
    var keyTextSmallSize = 0
    var candidateTextSize = 0f
    var composingTextSize = 0f
    var isLandscape = false
    var keyXMargin = 0
    var keyYMargin = 0
    private var keyboardHeightRatio = 0f

    init {
        initData()
    }

    fun initData() {
        val resources = Launcher.instance.context.resources
        val dm = resources.displayMetrics
        mScreenWidth = dm.widthPixels
        mScreenHeight = dm.heightPixels
        isLandscape = mScreenHeight <= mScreenWidth
        var screenWidthVertical = mScreenWidth
        var screenHeightVertical = mScreenHeight
        if(keyboardModeFloat){
            screenWidthVertical = (min(dm.widthPixels, dm.heightPixels) * 3f / 4).toInt()
            screenHeightVertical = (max(dm.widthPixels, dm.heightPixels) * 3f / 4).toInt()
        }
        val oneHandedMod = AppPrefs.getInstance().keyboardSetting.oneHandedModSwitch.getValue()
        holderWidth = if (oneHandedMod) (screenWidthVertical * 0.2f).toInt() else 0
        skbWidth = screenWidthVertical - holderWidth
        inputAreaWidth = skbWidth
        var candidatesHeightRatio = AppPrefs.getInstance().internal.candidatesHeightRatio.getValue()
        if(isLandscape && !keyboardModeFloat){
            inputAreaWidth = mScreenWidth
            skbWidth = (skbWidth * 0.7).toInt()
            keyboardHeightRatio = AppPrefs.getInstance().internal.keyboardHeightRatioLandscape.getValue()
            candidatesHeightRatio = AppPrefs.getInstance().internal.candidatesHeightRatioLandscape.getValue()
        } else {
            keyboardHeightRatio = AppPrefs.getInstance().internal.keyboardHeightRatio.getValue()
        }
        skbHeight = (screenHeightVertical * keyboardHeightRatio).toInt()
        heightForcomposing = (screenHeightVertical * candidatesHeightRatio *
                AppPrefs.getInstance().keyboardSetting.candidateTextSize.getValue() / 100f).toInt()
        heightForCandidates = (heightForcomposing * 1.9).toInt()
        heightForCandidatesArea = (heightForcomposing * 2.9).toInt()
        composingTextSize = DevicesUtils.px2sp (heightForcomposing)
        candidateTextSize = DevicesUtils.px2sp (heightForCandidates)
        heightForFullDisplayBar = (heightForCandidatesArea * 0.7f).toInt()
        heightForKeyboardMove = (heightForCandidatesArea * 0.2f).toInt()

        val keyboardFontSizeRatio = prefs.keyboardFontSize.getValue()/100f
        keyTextSize = (skbHeight * 0.06f * keyboardFontSizeRatio).toInt()
        keyTextSmallSize = (skbHeight * 0.04f * keyboardFontSizeRatio).toInt()
        keyXMargin = (prefs.keyXMargin.getValue() / 1000f * skbWidth).toInt()
        keyYMargin = (prefs.keyYMargin.getValue() / 1000f * skbHeight).toInt()
        inputAreaHeight = skbHeight + heightForCandidatesArea
    }

    var keyBoardHeightRatio: Float
        
        get() = keyboardHeightRatio
        
        set(keyBoardHeightRatio) {
            keyboardHeightRatio = keyBoardHeightRatio
            if(isLandscape) AppPrefs.getInstance().internal.keyboardHeightRatioLandscape.setValue(keyBoardHeightRatio)
            else AppPrefs.getInstance().internal.keyboardHeightRatio.setValue(keyBoardHeightRatio)
        }

    var keyboardModeFloat:Boolean
        get() = if (isLandscape)AppPrefs.getInstance().internal.keyboardModeFloatLandscape.getValue() else AppPrefs.getInstance().internal.keyboardModeFloat.getValue()
        set(isFloatMode) {
            if(isLandscape) AppPrefs.getInstance().internal.keyboardModeFloatLandscape.setValue(isFloatMode)
            else AppPrefs.getInstance().internal.keyboardModeFloat.setValue(isFloatMode)
        }

    val skbAreaHeight:Int
        get() = instance.inputAreaHeight + if(!instance.keyboardModeFloat) {
            AppPrefs.getInstance().internal.keyboardBottomPadding.getValue() + instance.systemNavbarWindowsBottom +
            if(AppPrefs.getInstance().internal.fullDisplayKeyboardEnable.getValue()) instance.heightForFullDisplayBar else 0
        } else instance.heightForKeyboardMove

    val leftMarginWidth:Int
        get() = if(!instance.keyboardModeFloat) (instance.inputAreaWidth - instance.skbWidth)/2 else 0

    companion object {
        private var mInstance: EnvironmentSingleton? = null
        @JvmStatic
		val instance: EnvironmentSingleton
            get() {
                if (null == mInstance) {
                    mInstance = EnvironmentSingleton()
                }
                return mInstance!!
            }
    }
}