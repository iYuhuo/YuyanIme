package com.yuyan.imemodule.data.flower

import com.yuyan.imemodule.view.preference.ManagedPreference

enum class FlowerTypefaceMode {
    Mars, FlowerVine, Messy, Germinate, Fog, ProhibitAccess, Grass, Wind, Disabled;
    companion object : ManagedPreference.StringLikeCodec<FlowerTypefaceMode> {
        override fun decode(raw: String) = FlowerTypefaceMode.valueOf(raw)
    }
}