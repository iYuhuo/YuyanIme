package com.yuyan.imemodule.entity.keyboard

import com.yuyan.imemodule.singleton.EnvironmentSingleton

class SoftKeyboard(var mKeyRows: List<List<SoftKey>>) {
    val keyXMargin = EnvironmentSingleton.instance.keyXMargin
    val keyYMargin = EnvironmentSingleton.instance.keyYMargin
    
    fun mapToKey(x: Int, y: Int): SoftKey? {
        for (element in mKeyRows) {
            for (sKey in element) {
                if (sKey.mLeft <= x && sKey.mTop <= y && sKey.mRight > x && sKey.mBottom > y) return sKey
            }
        }
        return null
    }

    
    fun getKeyByCode(code: Int): SoftKey? {
        for (keyRow in mKeyRows) {
            for (sKey in keyRow) {
                if (sKey.code == code) return sKey
            }
        }
        return null
    }
}