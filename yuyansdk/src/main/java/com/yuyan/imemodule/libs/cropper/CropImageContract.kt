@file:Suppress("DEPRECATION")

package com.yuyan.imemodule.libs.cropper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract

class CropImageContract : ActivityResultContract<CropImageContractOptions, CropImageView.CropResult>() {
  override fun createIntent(context: Context, input: CropImageContractOptions) = Intent(context, CropImageActivity::class.java).apply {
    putExtra(
        CropImage.CROP_IMAGE_EXTRA_BUNDLE,
      Bundle(2).apply {
        putParcelable(CropImage.CROP_IMAGE_EXTRA_SOURCE, input.uri)
        putParcelable(CropImage.CROP_IMAGE_EXTRA_OPTIONS, input.cropImageOptions)
      },
    )
  }

  override fun parseResult(
    resultCode: Int,
    intent: Intent?,
  ): CropImageView.CropResult {
    val result = intent?.parcelable<CropImage.ActivityResult>(CropImage.CROP_IMAGE_EXTRA_RESULT)

    return if (result == null || resultCode == Activity.RESULT_CANCELED) {
        CropImage.CancelledResult
    } else {
      result
    }
  }
}
