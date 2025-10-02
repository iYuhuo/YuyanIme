package com.yuyan.imemodule.utils

import com.yuyan.imemodule.application.CustomConstant
import com.yuyan.imemodule.data.flower.FlowerTypefaceMode
import com.yuyan.imemodule.data.flower.simplified2HotPreset
import java.util.regex.Pattern

object StringUtils {
    
    @JvmStatic
    fun isLetter(str: String?): Boolean {
        val pattern = Pattern.compile("[a-zA-Z]*")
        return pattern.matcher(str.toString()).matches()
    }

    
    @JvmStatic
    fun isEnglishWord(str: String?): Boolean {
        val pattern = Pattern.compile("[a-zA-Z ]*")
        return pattern.matcher(str.toString()).matches()
    }

    @JvmStatic
    fun isNumber(str: String?): Boolean {
        if(str.isNullOrBlank())return false
        val pattern = Pattern.compile("^[+-]?\\d*(\\.\\d*)?\$")
        return pattern.matcher(str).matches()
    }

    fun isChineseEnd(input: String): Boolean {
        val chineseEndPattern = "[\\u4e00-\\u9fff]\$".toRegex()
        return chineseEndPattern.find(input) != null
    }

    const val SBC_SPACE = 12288
        .toChar()
    const val DBC_SPACE = 32
        .toChar()
    const val SBC_PERIOD = 12290
        .toChar()
    const val DBC_PERIOD = 65377
        .toChar()
    const val ASCII_START = 30.toChar()
    const val ASCII_END = 126.toChar()
    const val UNICODE_START = 65278.toChar()
    const val UNICODE_END = 65374.toChar()
    const val DBC_SBC_STEP = 65248
        .toChar()

    private fun sbc2dbc(src: Char): Char {
        return if (src == SBC_SPACE) {
            DBC_SPACE
        } else if (src == SBC_PERIOD) {
            DBC_PERIOD
        } else {
            if (src in UNICODE_START..UNICODE_END) {
                (src.code - DBC_SBC_STEP.code).toChar()
            } else src
        }
    }

    @JvmStatic
    fun sbc2dbcCase(src: String?): String? {
        if (src == null) {
            return null
        }
        val c = src.toCharArray()
        for (i in c.indices) {
            c[i] = sbc2dbc(c[i])
        }
        return String(c)
    }

    
    fun isDBCSymbol(src: String?): Boolean {
        if (src == null || src.length > 1) {
            return false
        }
        val c = src[0]
        return c.code in 32..47 || c.code in 58..64 || c.code in 91..96 || c.code in 123..126
    }

    
    fun converted2FlowerTypeface(src: String): String {
         return  when(CustomConstant.flowerTypeface) {
             FlowerTypefaceMode.Disabled -> {
                 src
             }
            FlowerTypefaceMode.Mars -> {
                 src.map { simplified2HotPreset[it]?:it }.joinToString("")
            }
            FlowerTypefaceMode.FlowerVine -> {
                "ζั͡" + src.map { it }.joinToString("ั͡").plus("ั͡✾")
            }
            FlowerTypefaceMode.Messy -> {
                "҉҉҉" + src.map { it }.joinToString("҉҉҉").plus("҉҉҉")
            }
            FlowerTypefaceMode.Germinate -> {
                "ོ" + src.map { it }.joinToString("ོ").plus("ོ")
            }
            FlowerTypefaceMode.Fog -> {
                "҈҈҈҈" + src.map { it }.joinToString( "҈҈҈҈").plus("҈҈҈҈")
            }
            FlowerTypefaceMode.ProhibitAccess -> {
               src.map { it }.joinToString( "⃠").plus("⃠")
            }
            FlowerTypefaceMode.Grass -> {
                "҈҈҈" + src.map { it }.joinToString( "҈҈҈").plus("҈҈҈")
            }
            FlowerTypefaceMode.Wind -> {
             "=͟͟͞͞" + src.map { it }.joinToString( "=͟͟͞͞")
            }
        }
    }
}