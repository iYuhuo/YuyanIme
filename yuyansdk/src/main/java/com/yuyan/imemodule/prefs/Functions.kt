package com.yuyan.imemodule.prefs

import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.updateLayoutParams
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.preference.SwitchPreference
import splitties.dimensions.dp
import splitties.views.dsl.core.verticalMargin

private val mDefault by lazy {
    Preference::class.java
        .getDeclaredField("mDefaultValue")
        .apply { isAccessible = true }
}

private fun <T : Preference> T.def() =
    mDefault.get(this)

fun <T : EditTextPreference> T.restore() {
    (def() as? String)?.let {
        if (callChangeListener(it)) {
            text = it
        }
    }
}

fun <T : ListPreference> T.restore() {
    (def() as? String)?.let {
        if (callChangeListener(it)) {
            value = it
        }
    }
}

fun <T : SwitchPreference> T.restore() {
    (def() as? Boolean)?.let {
        if (callChangeListener(it)) {
            isChecked = it
        }
    }
}

fun PreferenceDialogFragmentCompat.fixDialogMargin(contentView: View) {
    contentView.findViewById<View>(android.R.id.message)?.updateLayoutParams<MarginLayoutParams> {
        verticalMargin = requireContext().dp(8)
    }
}