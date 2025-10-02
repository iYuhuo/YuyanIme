package com.yuyan.imemodule.application

import com.yuyan.imemodule.data.flower.FlowerTypefaceMode

object CustomConstant {
    var RIME_DICT_PATH = Launcher.instance.context.getExternalFilesDir("rime").toString()
    const val SCHEMA_ZH_T9 = "t9_pinyin"
    const val SCHEMA_ZH_QWERTY = "pinyin"
    const val SCHEMA_EN = "english"
    const val SCHEMA_ZH_HANDWRITING = "handwriting"
    const val SCHEMA_ZH_DOUBLE_FLYPY = "double_pinyin_"
    const val SCHEMA_ZH_DOUBLE_LX17 = "double_pinyin_ls17"
    const val SCHEMA_ZH_STROKE = "stroke"
    const val CURRENT_RIME_DICT_DATA_VERSIOM = 20251001
    const val YUYAN_IME_REPO = "https://github.com/gurecn/YuyanIme"
    const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
    const val FEEDBACK_TXC_REPO = "https://github.com/gurecn/YuyanIme/issues"

    var flowerTypeface = FlowerTypefaceMode.Disabled
    var lockClipBoardEnable = false
}