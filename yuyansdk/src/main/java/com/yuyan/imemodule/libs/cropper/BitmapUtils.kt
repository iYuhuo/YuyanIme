package com.yuyan.imemodule.libs.cropper

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.util.Log
import android.util.Pair
import androidx.exifinterface.media.ExifInterface
import com.yuyan.imemodule.libs.cropper.CropImageView.RequestSizeOptions
import com.yuyan.imemodule.libs.cropper.utils.getUriForFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.ref.WeakReference
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal object BitmapUtils {

  val EMPTY_RECT = Rect()
  val EMPTY_RECT_F = RectF()
  private const val IMAGE_MAX_BITMAP_DIMENSION = 2048
  private const val WRITE_AND_TRUNCATE = "wt"

  
  val RECT = RectF()

  
  val POINTS = FloatArray(6)

  
  val POINTS2 = FloatArray(6)

  
  private var mMaxTextureSize = 0

  
  var mStateBitmap: Pair<String, WeakReference<Bitmap>>? = null

  
  fun orientateBitmapByExif(bitmap: Bitmap?, context: Context, uri: Uri): RotateBitmapResult {
    val exifInterface = try {
      context.contentResolver.openInputStream(uri)?.use {
        ExifInterface(it)
      }
    } catch (ignored: Throwable) {
      null
    }

    return when {
      exifInterface != null -> orientateBitmapByExif(bitmap, exifInterface)
      else -> RotateBitmapResult(
        bitmap = bitmap,
        degrees = 0,
        flipHorizontally = false,
        flipVertically = false,
      )
    }
  }

  
  fun orientateBitmapByExif(bitmap: Bitmap?, exif: ExifInterface): RotateBitmapResult {
    val orientationAttributeInt =
      exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    val degrees: Int = when (orientationAttributeInt) {
      ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSVERSE,
      ExifInterface.ORIENTATION_TRANSPOSE,
      -> 90
      ExifInterface.ORIENTATION_ROTATE_180 -> 180
      ExifInterface.ORIENTATION_ROTATE_270 -> 270
      else -> 0
    }

    val flipHorizontally = orientationAttributeInt == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
      orientationAttributeInt == ExifInterface.ORIENTATION_TRANSPOSE
    val flipVertically = orientationAttributeInt == ExifInterface.ORIENTATION_FLIP_VERTICAL ||
      orientationAttributeInt == ExifInterface.ORIENTATION_TRANSVERSE
    return RotateBitmapResult(
      bitmap = bitmap,
      degrees = degrees,
      flipHorizontally = flipHorizontally,
      flipVertically = flipVertically,
    )
  }

  
  fun decodeSampledBitmap(
    context: Context,
    uri: Uri,
    reqWidth: Int,
    reqHeight: Int,
  ): BitmapSampled = try {
    val resolver = context.contentResolver
    val options = decodeImageForOption(resolver, uri)
    if (options.outWidth == -1 && options.outHeight == -1) throw RuntimeException("File is not a picture")
    options.inSampleSize = max(
      calculateInSampleSizeByRequestedSize(
        width = options.outWidth,
        height = options.outHeight,
        reqWidth = reqWidth,
        reqHeight = reqHeight,
      ),
      calculateInSampleSizeByMaxTextureSize(
        width = options.outWidth,
        height = options.outHeight,
      ),
    )
    val bitmap = decodeImage(
      resolver = resolver,
      uri = uri,
      options = options,
    )
    BitmapSampled(bitmap, options.inSampleSize)
  } catch (e: Exception) {
    throw CropException.FailedToLoadBitmap(uri, e.message)
  }

  
  fun cropBitmapObjectHandleOOM(
    bitmap: Bitmap?,
    cropPoints: FloatArray,
    degreesRotated: Int,
    fixAspectRatio: Boolean,
    aspectRatioX: Int,
    aspectRatioY: Int,
    flipHorizontally: Boolean,
    flipVertically: Boolean,
  ): BitmapSampled {
    var scale = 1
    while (true) {
      try {
        val cropBitmap = cropBitmapObjectWithScale(
          bitmap = bitmap!!,
          cropPoints = cropPoints,
          degreesRotated = degreesRotated,
          fixAspectRatio = fixAspectRatio,
          aspectRatioX = aspectRatioX,
          aspectRatioY = aspectRatioY,
          scale = 1 / scale.toFloat(),
          flipHorizontally = flipHorizontally,
          flipVertically = flipVertically,
        )
        return BitmapSampled(cropBitmap, scale)
      } catch (e: OutOfMemoryError) {
        scale *= 2
        if (scale > 8) {
          throw e
        }
      }
    }
  }

  
  private fun cropBitmapObjectWithScale(
    bitmap: Bitmap,
    cropPoints: FloatArray,
    degreesRotated: Int,
    fixAspectRatio: Boolean,
    aspectRatioX: Int,
    aspectRatioY: Int,
    scale: Float,
    flipHorizontally: Boolean,
    flipVertically: Boolean,
  ): Bitmap {
    val rect = getRectFromPoints(
      cropPoints,
      bitmap.width,
      bitmap.height,
      fixAspectRatio,
      aspectRatioX,
      aspectRatioY,
    )
    val matrix = Matrix()
    matrix.setRotate(degreesRotated.toFloat(), bitmap.width / 2.0f, bitmap.height / 2.0f)
    matrix.postScale(
      if (flipHorizontally) -scale else scale,
      if (flipVertically) -scale else scale,
    )
    var result = Bitmap.createBitmap(
      bitmap,
      rect.left,
      rect.top,
      rect.width(),
      rect.height(),
      matrix,
      true,
    )
    if (result == bitmap && bitmap.config != null) {
      result = bitmap.copy(bitmap.config!!, false)
    }
    if (degreesRotated % 90 != 0) {
      result = cropForRotatedImage(
        result,
        cropPoints,
        rect,
        degreesRotated,
        fixAspectRatio,
        aspectRatioX,
        aspectRatioY,
      )
    }
    return result
  }

  
  fun cropBitmap(
    context: Context,
    loadedImageUri: Uri?,
    cropPoints: FloatArray,
    degreesRotated: Int,
    orgWidth: Int,
    orgHeight: Int,
    fixAspectRatio: Boolean,
    aspectRatioX: Int,
    aspectRatioY: Int,
    reqWidth: Int,
    reqHeight: Int,
    flipHorizontally: Boolean,
    flipVertically: Boolean,
  ): BitmapSampled {
    var sampleMulti = 1

    while (true) {
      try {
        return cropBitmap(
          context = context,
          loadedImageUri = loadedImageUri!!,
          cropPoints = cropPoints,
          degreesRotated = degreesRotated,
          orgWidth = orgWidth,
          orgHeight = orgHeight,
          fixAspectRatio = fixAspectRatio,
          aspectRatioX = aspectRatioX,
          aspectRatioY = aspectRatioY,
          reqWidth = reqWidth,
          reqHeight = reqHeight,
          flipHorizontally = flipHorizontally,
          flipVertically = flipVertically,
          sampleMulti = sampleMulti,
        )
      } catch (e: OutOfMemoryError) {
        sampleMulti *= 2
        if (sampleMulti > 16) {
          throw RuntimeException(
            "Failed to handle OOM by sampling ($sampleMulti): $loadedImageUri\r\n${e.message}",
            e,
          )
        }
      }
    }
  }

  
  fun getRectLeft(points: FloatArray): Float = min(min(min(points[0], points[2]), points[4]), points[6])

  
  fun getRectTop(points: FloatArray): Float = min(min(min(points[1], points[3]), points[5]), points[7])

  
  fun getRectRight(points: FloatArray): Float = max(max(max(points[0], points[2]), points[4]), points[6])

  
  fun getRectBottom(points: FloatArray): Float = max(max(max(points[1], points[3]), points[5]), points[7])

  
  fun getRectWidth(points: FloatArray): Float = getRectRight(points) - getRectLeft(points)

  
  fun getRectHeight(points: FloatArray): Float = getRectBottom(points) - getRectTop(points)

  
  fun getRectCenterX(points: FloatArray): Float = (getRectRight(points) + getRectLeft(points)) / 2f

  
  fun getRectCenterY(points: FloatArray): Float = (getRectBottom(points) + getRectTop(points)) / 2f

  
  fun getRectFromPoints(
    cropPoints: FloatArray,
    imageWidth: Int,
    imageHeight: Int,
    fixAspectRatio: Boolean,
    aspectRatioX: Int,
    aspectRatioY: Int,
  ): Rect {
    val left = max(0f, getRectLeft(cropPoints)).roundToInt()
    val top = max(0f, getRectTop(cropPoints)).roundToInt()
    val right = min(imageWidth.toFloat(), getRectRight(cropPoints)).roundToInt()
    val bottom = min(imageHeight.toFloat(), getRectBottom(cropPoints)).roundToInt()
    val rect = Rect(left, top, right, bottom)
    if (fixAspectRatio) {
      fixRectForAspectRatio(rect, aspectRatioX, aspectRatioY)
    }
    return rect
  }

  
  private fun fixRectForAspectRatio(rect: Rect, aspectRatioX: Int, aspectRatioY: Int) {
    if (aspectRatioX == aspectRatioY && rect.width() != rect.height()) {
      if (rect.height() > rect.width()) {
        rect.bottom -= rect.height() - rect.width()
      } else {
        rect.right -= rect.width() - rect.height()
      }
    }
  }

  
  fun writeTempStateStoreBitmap(
    context: Context,
    bitmap: Bitmap?,
    customOutputUri: Uri?,
  ): Uri? =
    try {
      writeBitmapToUri(
        context = context,
        bitmap = bitmap!!,
        compressFormat = CompressFormat.JPEG,
        compressQuality = 95,
        customOutputUri = customOutputUri,
      )
    } catch (e: Exception) {
      Log.w(
        "AIC",
        "Failed to write bitmap to temp file for image-cropper save instance state",
        e,
      )
      null
    }

  
  @Throws(FileNotFoundException::class)
  fun writeBitmapToUri(
    context: Context,
    bitmap: Bitmap,
    compressFormat: CompressFormat,
    compressQuality: Int,
    customOutputUri: Uri?,
  ): Uri {
    val newUri = customOutputUri ?: buildUri(context, compressFormat)

    return context.contentResolver.openOutputStream(newUri, WRITE_AND_TRUNCATE)!!.use {
      bitmap.compress(compressFormat, compressQuality, it)
      newUri
    }
  }

  private fun buildUri(
    context: Context,
    compressFormat: CompressFormat,
  ): Uri =
    try {
      val ext = when (compressFormat) {
        CompressFormat.JPEG -> ".jpg"
        CompressFormat.PNG -> ".png"
        else -> ".webp"
      }
      if (SDK_INT >= 29) {
        val file = File.createTempFile("cropped", ext, context.cacheDir)
        getUriForFile(context, file)
      } else {
        Uri.fromFile(File.createTempFile("cropped", ext, context.cacheDir))
      }
    } catch (e: IOException) {
      throw RuntimeException("Failed to create temp file for output image", e)
    }

  
  fun resizeBitmap(
    bitmap: Bitmap?,
    reqWidth: Int,
    reqHeight: Int,
    options: RequestSizeOptions,
  ): Bitmap {
    try {
      if (reqWidth > 0 && reqHeight > 0 && (options === RequestSizeOptions.RESIZE_FIT || options === RequestSizeOptions.RESIZE_INSIDE || options === RequestSizeOptions.RESIZE_EXACT)) {
        var resized: Bitmap? = null
        if (options === RequestSizeOptions.RESIZE_EXACT) {
          resized = Bitmap.createScaledBitmap(bitmap!!, reqWidth, reqHeight, false)
        } else {
          val width = bitmap!!.width
          val height = bitmap.height
          val scale = max(width / reqWidth.toFloat(), height / reqHeight.toFloat())
          if (scale > 1 || options === RequestSizeOptions.RESIZE_FIT) {
            resized = Bitmap.createScaledBitmap(
              bitmap,
              (width / scale).toInt(),
              (height / scale).toInt(),
              false,
            )
          }
        }
        if (resized != null) {
          if (resized != bitmap) {
            bitmap.recycle()
          }
          return resized
        }
      }
    } catch (e: Exception) {
      Log.w("AIC", "Failed to resize cropped image, return bitmap before resize", e)
    }
    return bitmap!!
  }

  
  private fun cropBitmap(
    context: Context,
    loadedImageUri: Uri,
    cropPoints: FloatArray,
    degreesRotated: Int,
    orgWidth: Int,
    orgHeight: Int,
    fixAspectRatio: Boolean,
    aspectRatioX: Int,
    aspectRatioY: Int,
    reqWidth: Int,
    reqHeight: Int,
    flipHorizontally: Boolean,
    flipVertically: Boolean,
    sampleMulti: Int,
  ): BitmapSampled {
    val rect = getRectFromPoints(
      cropPoints,
      orgWidth,
      orgHeight,
      fixAspectRatio,
      aspectRatioX,
      aspectRatioY,
    )
    val width = if (reqWidth > 0) reqWidth else rect.width()
    val height = if (reqHeight > 0) reqHeight else rect.height()
    var result: Bitmap? = null
    var sampleSize = 1
    try {
      val bitmapSampled =
        decodeSampledBitmapRegion(context, loadedImageUri, rect, width, height, sampleMulti)
      result = bitmapSampled.bitmap
      sampleSize = bitmapSampled.sampleSize
    } catch (ignored: Exception) {
    }
    return if (result != null) {
      try {
        result =
          rotateAndFlipBitmapInt(result, degreesRotated, flipHorizontally, flipVertically)
        if (degreesRotated % 90 != 0) {
          result = cropForRotatedImage(
            result,
            cropPoints,
            rect,
            degreesRotated,
            fixAspectRatio,
            aspectRatioX,
            aspectRatioY,
          )
        }
      } catch (e: OutOfMemoryError) {
        result.recycle()
        throw e
      }
      BitmapSampled(result, sampleSize)
    } else {
      cropBitmap(
        context,
        loadedImageUri,
        cropPoints,
        degreesRotated,
        fixAspectRatio,
        aspectRatioX,
        aspectRatioY,
        sampleMulti,
        rect,
        width,
        height,
        flipHorizontally,
        flipVertically,
      )
    }
  }

  
  private fun cropBitmap(
    context: Context,
    loadedImageUri: Uri,
    cropPoints: FloatArray,
    degreesRotated: Int,
    fixAspectRatio: Boolean,
    aspectRatioX: Int,
    aspectRatioY: Int,
    sampleMulti: Int,
    rect: Rect,
    width: Int,
    height: Int,
    flipHorizontally: Boolean,
    flipVertically: Boolean,
  ): BitmapSampled {
    var result: Bitmap? = null
    val sampleSize: Int
    try {
      val options = BitmapFactory.Options()
      sampleSize = (
        sampleMulti *
          calculateInSampleSizeByRequestedSize(
            width = rect.width(),
            height = rect.height(),
            reqWidth = width,
            reqHeight = height,
          )
        )
      options.inSampleSize = sampleSize
      val fullBitmap = decodeImage(
        resolver = context.contentResolver,
        uri = loadedImageUri,
        options = options,
      )
      if (fullBitmap != null) {
        try {
          val points2 = FloatArray(cropPoints.size)
          System.arraycopy(cropPoints, 0, points2, 0, cropPoints.size)
          for (i in points2.indices) {
            points2[i] = points2[i] / options.inSampleSize
          }

          result = cropBitmapObjectWithScale(
            bitmap = fullBitmap,
            cropPoints = points2,
            degreesRotated = degreesRotated,
            fixAspectRatio = fixAspectRatio,
            aspectRatioX = aspectRatioX,
            aspectRatioY = aspectRatioY, scale = 1f,
            flipHorizontally = flipHorizontally,
            flipVertically = flipVertically,
          )
        } finally {
          if (result != fullBitmap) {
            fullBitmap.recycle()
          }
        }
      }
    } catch (e: OutOfMemoryError) {
      result?.recycle()
      throw e
    } catch (e: Exception) {
      throw CropException.FailedToLoadBitmap(loadedImageUri, e.message)
    }
    return BitmapSampled(result, sampleSize)
  }

  
  @Throws(FileNotFoundException::class)
  private fun decodeImageForOption(resolver: ContentResolver, uri: Uri): BitmapFactory.Options = resolver.openInputStream(uri).use {
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = true
    BitmapFactory.decodeStream(it, EMPTY_RECT, options)
    options.inJustDecodeBounds = false
    options
  }

  
  @Throws(FileNotFoundException::class)
  private fun decodeImage(
    resolver: ContentResolver,
    uri: Uri,
    options: BitmapFactory.Options,
  ): Bitmap? {
    do {
      resolver.openInputStream(uri).use {
        try {
          return BitmapFactory.decodeStream(it, EMPTY_RECT, options)
        } catch (e: OutOfMemoryError) {
          options.inSampleSize *= 2
        }
      }
    } while (options.inSampleSize <= 512)
    throw CropException.FailedToDecodeImage(uri)
  }

  
  private fun decodeSampledBitmapRegion(
    context: Context,
    uri: Uri,
    rect: Rect,
    reqWidth: Int,
    reqHeight: Int,
    sampleMulti: Int,
  ): BitmapSampled {
    try {
      val options = BitmapFactory.Options()
      options.inSampleSize = sampleMulti * calculateInSampleSizeByRequestedSize(
        width = rect.width(),
        height = rect.height(),
        reqWidth = reqWidth,
        reqHeight = reqHeight,
      )

      context.contentResolver.openInputStream(uri).use {
        val decoder = when {
          SDK_INT >= 31 -> BitmapRegionDecoder.newInstance(it!!)
          else -> @Suppress("DEPRECATION") BitmapRegionDecoder.newInstance(it!!, false)
        }

        try {
          do {
            try {
              return BitmapSampled(
                decoder!!.decodeRegion(rect, options),
                options.inSampleSize,
              )
            } catch (e: OutOfMemoryError) {
              options.inSampleSize *= 2
            }
          } while (options.inSampleSize <= 512)
        } finally {
          decoder?.recycle()
        }
      }
    } catch (e: Exception) {
      throw CropException.FailedToLoadBitmap(uri, e.message)
    }
    return BitmapSampled(null, 1)
  }

  
  private fun cropForRotatedImage(
    bitmap: Bitmap,
    cropPoints: FloatArray,
    rect: Rect,
    degreesRotated: Int,
    fixAspectRatio: Boolean,
    aspectRatioX: Int,
    aspectRatioY: Int,
  ): Bitmap {
    var tempBitmap = bitmap
    if (degreesRotated % 90 != 0) {
      var adjLeft = 0
      var adjTop = 0
      var width = 0
      var height = 0
      val rads = Math.toRadians(degreesRotated.toDouble())
      val compareTo = if (degreesRotated < 90 || degreesRotated in 181..269) rect.left else rect.right
      var i = 0
      while (i < cropPoints.size) {
        if (cropPoints[i] >= compareTo - 1 && cropPoints[i] <= compareTo + 1) {
          adjLeft = abs(sin(rads) * (rect.bottom - cropPoints[i + 1])).toInt()
          adjTop = abs(cos(rads) * (cropPoints[i + 1] - rect.top)).toInt()
          width = abs((cropPoints[i + 1] - rect.top) / sin(rads)).toInt()
          height = abs((rect.bottom - cropPoints[i + 1]) / cos(rads)).toInt()
          break
        }
        i += 2
      }
      rect[adjLeft, adjTop, adjLeft + width] = adjTop + height
      if (fixAspectRatio) {
        fixRectForAspectRatio(rect, aspectRatioX, aspectRatioY)
      }
      val bitmapTmp = tempBitmap
      tempBitmap = Bitmap.createBitmap(
        bitmap,
        rect.left,
        rect.top,
        rect.width(),
        rect.height(),
      )
      if (bitmapTmp != tempBitmap) {
        bitmapTmp.recycle()
      }
    }
    return tempBitmap
  }

  
  private fun calculateInSampleSizeByRequestedSize(
    width: Int,
    height: Int,
    reqWidth: Int,
    reqHeight: Int,
  ): Int {
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
      while (height / 2 / inSampleSize > reqHeight && width / 2 / inSampleSize > reqWidth) {
        inSampleSize *= 2
      }
    }
    return inSampleSize
  }

  
  private fun calculateInSampleSizeByMaxTextureSize(
    width: Int,
    height: Int,
  ): Int {
    var inSampleSize = 1
    if (mMaxTextureSize == 0) {
      mMaxTextureSize = maxTextureSize
    }

    if (mMaxTextureSize > 0) {
      while (
        height / inSampleSize > mMaxTextureSize ||
        width / inSampleSize > mMaxTextureSize
      ) {
        inSampleSize *= 2
      }
    }
    return inSampleSize
  }

  
  private fun rotateAndFlipBitmapInt(
    bitmap: Bitmap,
    degrees: Int,
    flipHorizontally: Boolean,
    flipVertically: Boolean,
  ): Bitmap = if (degrees > 0 || flipHorizontally || flipVertically) {
    val matrix = Matrix()
    matrix.setRotate(degrees.toFloat())
    matrix.postScale(
      (if (flipHorizontally) -1 else 1).toFloat(),
      (if (flipVertically) -1 else 1).toFloat(),
    )
    val newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
    if (newBitmap != bitmap) {
      bitmap.recycle()
    }
    newBitmap
  } else {
    bitmap
  }
  
  private val maxTextureSize: Int
    get() {
      return try {
        val egl = EGLContext.getEGL() as EGL10
        val display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        egl.eglInitialize(display, version)
        val totalConfigurations = IntArray(1)
        egl.eglGetConfigs(display, null, 0, totalConfigurations)
        val configurationsList = arrayOfNulls<EGLConfig>(
          totalConfigurations[0],
        )
        egl.eglGetConfigs(
          display,
          configurationsList,
          totalConfigurations[0],
          totalConfigurations,
        )
        val textureSize = IntArray(1)
        var maximumTextureSize = 0
        for (i in 0 until totalConfigurations[0]) {
          egl.eglGetConfigAttrib(
            display,
            configurationsList[i],
            EGL10.EGL_MAX_PBUFFER_WIDTH,
            textureSize,
          )
          if (maximumTextureSize < textureSize[0]) {
            maximumTextureSize = textureSize[0]
          }
        }
        egl.eglTerminate(display)
        max(maximumTextureSize, IMAGE_MAX_BITMAP_DIMENSION)
      } catch (e: Exception) {
        IMAGE_MAX_BITMAP_DIMENSION
      }
    }

  internal class BitmapSampled(
    val bitmap: Bitmap?,
    val sampleSize: Int,
  )

  internal class RotateBitmapResult(
    val bitmap: Bitmap?,
    val degrees: Int,
    val flipHorizontally: Boolean,
    val flipVertically: Boolean,
  )
}