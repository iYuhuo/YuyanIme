package com.yuyan.imemodule.libs.cropper

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable

inline fun <reified T : Parcelable> Bundle.parcelable(key: String): T? = when {
  else -> @Suppress("DEPRECATION") getParcelable(key) as? T
}

inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? = when {
  else -> @Suppress("DEPRECATION") getParcelableExtra(key) as? T
}