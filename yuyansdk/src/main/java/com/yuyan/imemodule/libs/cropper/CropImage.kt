package com.yuyan.imemodule.libs.cropper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import com.yuyan.imemodule.libs.cropper.CropImageView.CropResult

object CropImage {

  
  const val CROP_IMAGE_EXTRA_SOURCE = "CROP_IMAGE_EXTRA_SOURCE"

  
  const val CROP_IMAGE_EXTRA_OPTIONS = "CROP_IMAGE_EXTRA_OPTIONS"

  
  const val CROP_IMAGE_EXTRA_BUNDLE = "CROP_IMAGE_EXTRA_BUNDLE"

  
  const val CROP_IMAGE_EXTRA_RESULT = "CROP_IMAGE_EXTRA_RESULT"

  
  const val CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE = 204

  
  open class ActivityResult : CropResult, Parcelable {

    constructor(
      originalUri: Uri?,
      uriContent: Uri?,
      error: Exception?,
      cropPoints: FloatArray?,
      cropRect: Rect?,
      rotation: Int,
      wholeImageRect: Rect?,
      sampleSize: Int,
    ) : super(
      originalUri = originalUri,
      bitmap = null,
      uriContent = uriContent,
      error = error,
      cropPoints = cropPoints!!,
      cropRect = cropRect,
      wholeImageRect = wholeImageRect,
      rotation = rotation,
      sampleSize = sampleSize,
    )

    @Suppress("DEPRECATION")
    protected constructor(`in`: Parcel) : super(
      originalUri = `in`.readParcelable<Parcelable>(Uri::class.java.classLoader) as Uri?,
      bitmap = null,
      uriContent = `in`.readParcelable<Parcelable>(Uri::class.java.classLoader) as Uri?,
      error = `in`.readSerializable() as Exception?,
      cropPoints = `in`.createFloatArray()!!,
      cropRect = `in`.readParcelable<Parcelable>(Rect::class.java.classLoader) as Rect?,
      wholeImageRect = `in`.readParcelable<Parcelable>(Rect::class.java.classLoader) as Rect?,
      rotation = `in`.readInt(),
      sampleSize = `in`.readInt(),
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
      dest.writeParcelable(originalUri, flags)
      dest.writeParcelable(uriContent, flags)
      dest.writeSerializable(error)
      dest.writeFloatArray(cropPoints)
      dest.writeParcelable(cropRect, flags)
      dest.writeParcelable(wholeImageRect, flags)
      dest.writeInt(rotation)
      dest.writeInt(sampleSize)
    }

    override fun describeContents(): Int = 0

    companion object {

      @JvmField
      val CREATOR: Parcelable.Creator<ActivityResult?> =
        object : Parcelable.Creator<ActivityResult?> {
          override fun createFromParcel(`in`: Parcel): ActivityResult =
            ActivityResult(`in`)

          override fun newArray(size: Int): Array<ActivityResult?> = arrayOfNulls(size)
        }
    }
  }

  object CancelledResult : CropResult(
    originalUri = null,
    bitmap = null,
    uriContent = null,
    error = CropException.Cancellation(),
    cropPoints = floatArrayOf(),
    cropRect = null,
    wholeImageRect = null,
    rotation = 0,
    sampleSize = 0,
  )
}
