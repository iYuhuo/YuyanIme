package com.yuyan.imemodule.libs.cropper

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Pair
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.util.component1
import androidx.core.util.component2
import com.yuyan.imemodule.libs.cropper.CropOverlayView.CropWindowChangeListener
import com.yuyan.imemodule.libs.cropper.utils.getFilePathFromUri
import com.yuyan.imemodule.R
import java.lang.ref.WeakReference
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

class CropImageView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
) : FrameLayout(context, attrs),
  CropWindowChangeListener {

  
  private val imageView: ImageView

  
  private val mCropOverlayView: CropOverlayView?

  
  private val mImageMatrix = Matrix()

  
  private val mImageInverseMatrix = Matrix()

  
  private val mProgressBar: ProgressBar

  
  private val mImagePoints = FloatArray(8)

  
  private val mScaleImagePoints = FloatArray(8)

  
  private var mAnimation: CropImageAnimation? = null
  private var originalBitmap: Bitmap? = null

  
  private var mInitialDegreesRotated = 0

  
  private var mDegreesRotated = 0

  
  private var mFlipHorizontally: Boolean

  
  private var mFlipVertically: Boolean
  private var mLayoutWidth = 0
  private var mLayoutHeight = 0
  private var mImageResource = 0

  
  private var mScaleType: ScaleType

  
  @Deprecated("This functionality is deprecated, please remove it altogether or create an issue and explain WHY you need this.")
  var isSaveBitmapToInstanceState = false

  
  private var mShowCropOverlay = true

  
  private var mShowCropLabel = false

  
  private var mCropLabelTextSize = 20f

  
  private var mShowProgressBar = true

  
  private var mAutoZoomEnabled = true

  
  private var mMaxZoom: Int

  
  private var mOnCropOverlayReleasedListener: OnSetCropOverlayReleasedListener? = null

  
  private var mOnSetCropOverlayMovedListener: OnSetCropOverlayMovedListener? = null

  
  private var mOnSetCropWindowChangeListener: OnSetCropWindowChangeListener? = null

  
  private var mOnSetImageUriCompleteListener: OnSetImageUriCompleteListener? = null

  
  private var mOnCropImageCompleteListener: OnCropImageCompleteListener? = null
  
  
  var imageUri: Uri? = null
    private set

  
  private var loadedSampleSize = 1

  
  private var mZoom = 1f

  
  private var mZoomOffsetX = 0f

  
  private var mZoomOffsetY = 0f

  
  private var mRestoreCropWindowRect: RectF? = null

  
  private var mRestoreDegreesRotated = 0

  
  private var mSizeChanged = false

  
  private var bitmapLoadingWorkerJob: WeakReference<BitmapLoadingWorkerJob>? = null

  
  private var bitmapCroppingWorkerJob: WeakReference<BitmapCroppingWorkerJob>? = null

  
  var rotatedDegrees: Int
    get() = mDegreesRotated
    set(degrees) {
      if (mDegreesRotated != degrees) {
        rotateImage(degrees - mDegreesRotated)
      }
    }

  
  private var customOutputUri: Uri? = null

  
  private var imageResource: Int
    get() = mImageResource
    set(resId) {
      if (resId != 0) {
        mCropOverlayView!!.initialCropWindowRect = null
        val bitmap = BitmapFactory.decodeResource(resources, resId)
        setBitmap(
          bitmap = bitmap,
          imageResource = resId,
          imageUri = null,
          loadSampleSize = 1,
          degreesRotated = 0,
        )
      }
    }

  val wholeImageRect: Rect?
    get() {
      val loadedSampleSize = loadedSampleSize
      val bitmap = originalBitmap ?: return null
      val orgWidth = bitmap.width * loadedSampleSize
      val orgHeight = bitmap.height * loadedSampleSize
      return Rect(0, 0, orgWidth, orgHeight)
    }

  
  var cropRect: Rect?
    get() {
      val loadedSampleSize = loadedSampleSize
      val bitmap = originalBitmap ?: return null
      val points = cropPoints
      val orgWidth = bitmap.width * loadedSampleSize
      val orgHeight = bitmap.height * loadedSampleSize
      return BitmapUtils.getRectFromPoints(
          cropPoints = points,
          imageWidth = orgWidth,
          imageHeight = orgHeight,
          fixAspectRatio = mCropOverlayView!!.isFixAspectRatio,
          aspectRatioX = mCropOverlayView.aspectRatioX,
          aspectRatioY = mCropOverlayView.aspectRatioY,
      )
    }
    set(rect) {
      mCropOverlayView!!.initialCropWindowRect = rect
    }

  
  val cropPoints: FloatArray
    get() {
      val cropWindowRect = mCropOverlayView!!.cropWindowRect
      val points = floatArrayOf(
        cropWindowRect.left,
        cropWindowRect.top,
        cropWindowRect.right,
        cropWindowRect.top,
        cropWindowRect.right,
        cropWindowRect.bottom,
        cropWindowRect.left,
        cropWindowRect.bottom,
      )
      mImageMatrix.invert(mImageInverseMatrix)
      mImageInverseMatrix.mapPoints(points)
      val resultPoints = FloatArray(points.size)
      for (i in points.indices) {
        resultPoints[i] = points[i] * loadedSampleSize
      }
      return resultPoints
    }

  
  @Deprecated("Please use getCroppedImage", replaceWith = ReplaceWith("getCroppedImage()"))
  @get:JvmName("-croppedImage")
  val croppedImage: Bitmap?
    get() = getCroppedImage(0, 0, RequestSizeOptions.NONE)

  
  @JvmOverloads
  fun getCroppedImage(
      reqWidth: Int = 0,
      reqHeight: Int = 0,
      options: RequestSizeOptions = RequestSizeOptions.RESIZE_INSIDE,
  ): Bitmap? {
    if (originalBitmap != null) {
      val newReqWidth = if (options != RequestSizeOptions.NONE) reqWidth else 0
      val newReqHeight = if (options != RequestSizeOptions.NONE) reqHeight else 0
      val croppedBitmap = if (imageUri != null && (loadedSampleSize > 1 || options == RequestSizeOptions.SAMPLING)) {
        BitmapUtils.cropBitmap(
            context = context,
            loadedImageUri = imageUri,
            cropPoints = cropPoints,
            degreesRotated = mDegreesRotated,
            orgWidth = originalBitmap!!.width * loadedSampleSize,
            orgHeight = originalBitmap!!.height * loadedSampleSize,
            fixAspectRatio = mCropOverlayView!!.isFixAspectRatio,
            aspectRatioX = mCropOverlayView.aspectRatioX,
            aspectRatioY = mCropOverlayView.aspectRatioY,
            reqWidth = newReqWidth,
            reqHeight = newReqHeight,
            flipHorizontally = mFlipHorizontally,
            flipVertically = mFlipVertically,
        ).bitmap
      } else {
        BitmapUtils.cropBitmapObjectHandleOOM(
            bitmap = originalBitmap,
            cropPoints = cropPoints,
            degreesRotated = mDegreesRotated,
            fixAspectRatio = mCropOverlayView!!.isFixAspectRatio,
            aspectRatioX = mCropOverlayView.aspectRatioX,
            aspectRatioY = mCropOverlayView.aspectRatioY,
            flipHorizontally = mFlipHorizontally,
            flipVertically = mFlipVertically,
        ).bitmap
      }

      return BitmapUtils.resizeBitmap(
          bitmap = croppedBitmap,
          reqWidth = newReqWidth,
          reqHeight = newReqHeight,
          options = options,
      )
    }

    return null
  }

  
  fun croppedImageAsync(
      saveCompressFormat: CompressFormat = CompressFormat.JPEG,
      saveCompressQuality: Int = 90,
      reqWidth: Int = 0,
      reqHeight: Int = 0,
      options: RequestSizeOptions = RequestSizeOptions.RESIZE_INSIDE,
      customOutputUri: Uri? = null,
  ) {
    requireNotNull(mOnCropImageCompleteListener) { "mOnCropImageCompleteListener is not set" }
    startCropWorkerTask(
      reqWidth = reqWidth,
      reqHeight = reqHeight,
      options = options,
      saveCompressFormat = saveCompressFormat,
      saveCompressQuality = saveCompressQuality,
      customOutputUri = customOutputUri,
    )
  }

  
  fun setOnSetImageUriCompleteListener(listener: OnSetImageUriCompleteListener?) {
    mOnSetImageUriCompleteListener = listener
  }

  
  fun setOnCropImageCompleteListener(listener: OnCropImageCompleteListener?) {
    mOnCropImageCompleteListener = listener
  }

  
  fun setImageUriAsync(uri: Uri?) {
    if (uri != null) {
      bitmapLoadingWorkerJob?.get()?.cancel()
      clearImageInt()
      mCropOverlayView!!.initialCropWindowRect = null
      bitmapLoadingWorkerJob = WeakReference(BitmapLoadingWorkerJob(context, this, uri))
      bitmapLoadingWorkerJob?.get()?.start()
      setProgressBarVisibility()
    }
  }

  
  fun rotateImage(degrees: Int) {
    if (originalBitmap != null) {
      val newDegrees =
        if (degrees < 0) {
          degrees % 360 + 360
        } else {
          degrees % 360
        }
      val flipAxes = (
        !mCropOverlayView!!.isFixAspectRatio &&
          (newDegrees in 46..134 || newDegrees in 216..304)
        )

      BitmapUtils.RECT.set(mCropOverlayView.cropWindowRect)
      var halfWidth =
        (if (flipAxes) BitmapUtils.RECT.height() else BitmapUtils.RECT.width()) / 2f
      var halfHeight =
        (if (flipAxes) BitmapUtils.RECT.width() else BitmapUtils.RECT.height()) / 2f

      if (flipAxes) {
        val isFlippedHorizontally = mFlipHorizontally
        mFlipHorizontally = mFlipVertically
        mFlipVertically = isFlippedHorizontally
      }
      mImageMatrix.invert(mImageInverseMatrix)
      BitmapUtils.POINTS[0] = BitmapUtils.RECT.centerX()
      BitmapUtils.POINTS[1] = BitmapUtils.RECT.centerY()
      BitmapUtils.POINTS[2] = 0f
      BitmapUtils.POINTS[3] = 0f
      BitmapUtils.POINTS[4] = 1f
      BitmapUtils.POINTS[5] = 0f
      mImageInverseMatrix.mapPoints(BitmapUtils.POINTS)
      mDegreesRotated = (mDegreesRotated + newDegrees) % 360
      applyImageMatrix(
        width = width.toFloat(),
        height = height.toFloat(),
        center = true,
        animate = false,
      )
      mImageMatrix.mapPoints(BitmapUtils.POINTS2, BitmapUtils.POINTS)
      mZoom /= sqrt(
        (BitmapUtils.POINTS2[4] - BitmapUtils.POINTS2[2]).toDouble().pow(2.0) +
          (BitmapUtils.POINTS2[5] - BitmapUtils.POINTS2[3]).toDouble().pow(2.0),
      ).toFloat()
      mZoom = max(mZoom, 1f)
      applyImageMatrix(
        width = width.toFloat(),
        height = height.toFloat(),
        center = true,
        animate = false,
      )
      mImageMatrix.mapPoints(BitmapUtils.POINTS2, BitmapUtils.POINTS)
      val change = sqrt(
        (BitmapUtils.POINTS2[4] - BitmapUtils.POINTS2[2]).toDouble().pow(2.0) +
          (BitmapUtils.POINTS2[5] - BitmapUtils.POINTS2[3]).toDouble().pow(2.0),
      )
      halfWidth *= change.toFloat()
      halfHeight *= change.toFloat()
      BitmapUtils.RECT[BitmapUtils.POINTS2[0] - halfWidth, BitmapUtils.POINTS2[1] - halfHeight, BitmapUtils.POINTS2[0] + halfWidth] =
        BitmapUtils.POINTS2[1] + halfHeight
      mCropOverlayView.resetCropOverlayView()
      mCropOverlayView.cropWindowRect = BitmapUtils.RECT
      applyImageMatrix(
        width = width.toFloat(),
        height = height.toFloat(),
        center = true,
        animate = false,
      )
      handleCropWindowChanged(inProgress = false, animate = false)
      mCropOverlayView.fixCurrentCropWindowRect()
    }
  }

  
  fun flipImageHorizontally() {
    mFlipHorizontally = !mFlipHorizontally
    applyImageMatrix(
      width = width.toFloat(),
      height = height.toFloat(),
      center = true,
      animate = false,
    )
  }

  
  fun flipImageVertically() {
    mFlipVertically = !mFlipVertically
    applyImageMatrix(
      width = width.toFloat(),
      height = height.toFloat(),
      center = true,
      animate = false,
    )
  }

  
  internal fun onSetImageUriAsyncComplete(result: BitmapLoadingWorkerJob.Result) {
    bitmapLoadingWorkerJob = null
    setProgressBarVisibility()
    if (result.error == null) {
      mInitialDegreesRotated = result.degreesRotated
      mFlipHorizontally = result.flipHorizontally
      mFlipVertically = result.flipVertically
      setBitmap(
        bitmap = result.bitmap,
        imageResource = 0,
        imageUri = result.uri,
        loadSampleSize = result.loadSampleSize,
        degreesRotated = result.degreesRotated,
      )
    }
    mOnSetImageUriCompleteListener?.onSetImageUriComplete(
      view = this,
      uri = result.uri,
      error = result.error,
    )
  }

  
  internal fun onImageCroppingAsyncComplete(result: BitmapCroppingWorkerJob.Result) {
    bitmapCroppingWorkerJob = null
    setProgressBarVisibility()
    val listener = mOnCropImageCompleteListener
    if (listener != null) {
      val cropResult = CropResult(
        originalUri = imageUri,
        bitmap = result.bitmap,
        uriContent = result.uri,
        error = result.error,
        cropPoints = cropPoints,
        cropRect = cropRect,
        wholeImageRect = wholeImageRect,
        rotation = rotatedDegrees,
        sampleSize = result.sampleSize,
      )
      listener.onCropImageComplete(this, cropResult)
    }
  }

  
  private fun setBitmap(
    bitmap: Bitmap?,
    imageResource: Int,
    imageUri: Uri?,
    loadSampleSize: Int,
    degreesRotated: Int,
  ) {
    if (originalBitmap == null || originalBitmap != bitmap) {
      clearImageInt()
      originalBitmap = bitmap
      imageView.setImageBitmap(originalBitmap)
      this.imageUri = imageUri
      mImageResource = imageResource
      loadedSampleSize = loadSampleSize
      mDegreesRotated = degreesRotated
      applyImageMatrix(
        width = width.toFloat(),
        height = height.toFloat(),
        center = true,
        animate = false,
      )
      if (mCropOverlayView != null) {
        mCropOverlayView.resetCropOverlayView()
        setCropOverlayVisibility()
      }
    }
  }

  
  private fun clearImageInt() {
    if (originalBitmap != null && (mImageResource > 0 || imageUri != null)) {
      originalBitmap!!.recycle()
    }
    originalBitmap = null
    mImageResource = 0
    imageUri = null
    loadedSampleSize = 1
    mDegreesRotated = 0
    mZoom = 1f
    mZoomOffsetX = 0f
    mZoomOffsetY = 0f
    mImageMatrix.reset()
    mRestoreCropWindowRect = null
    mRestoreDegreesRotated = 0
    imageView.setImageBitmap(null)
    setCropOverlayVisibility()
  }

  
  private fun startCropWorkerTask(
      reqWidth: Int,
      reqHeight: Int,
      options: RequestSizeOptions,
      saveCompressFormat: CompressFormat,
      saveCompressQuality: Int,
      customOutputUri: Uri?,
  ) {
    val bitmap = originalBitmap
    if (bitmap != null) {
      val currentTask =
        if (bitmapCroppingWorkerJob != null) bitmapCroppingWorkerJob!!.get() else null
      currentTask?.cancel()

      val (orgWidth, orgHeight) =
        if (loadedSampleSize > 1 || options == RequestSizeOptions.SAMPLING) {
          Pair((bitmap.width * loadedSampleSize), (bitmap.height * loadedSampleSize))
        } else {
          Pair(0, 0)
        }

      bitmapCroppingWorkerJob = WeakReference(
        BitmapCroppingWorkerJob(
          context = context,
          cropImageViewReference = WeakReference(this),
          uri = imageUri,
          bitmap = bitmap,
          cropPoints = cropPoints,
          degreesRotated = mDegreesRotated,
          orgWidth = orgWidth,
          orgHeight = orgHeight,
          fixAspectRatio = mCropOverlayView!!.isFixAspectRatio,
          aspectRatioX = mCropOverlayView.aspectRatioX,
          aspectRatioY = mCropOverlayView.aspectRatioY,
          reqWidth = if (options != RequestSizeOptions.NONE) reqWidth else 0,
          reqHeight = if (options != RequestSizeOptions.NONE) reqHeight else 0,
          flipHorizontally = mFlipHorizontally,
          flipVertically = mFlipVertically,
          options = options,
          saveCompressFormat = saveCompressFormat,
          saveCompressQuality = saveCompressQuality,
          customOutputUri = customOutputUri ?: this.customOutputUri,
        ),
      )

      bitmapCroppingWorkerJob!!.get()!!.start()
      setProgressBarVisibility()
    }
  }

  public override fun onSaveInstanceState(): Parcelable? {
    if (imageUri == null && originalBitmap == null && mImageResource < 1) {
      return super.onSaveInstanceState()
    }

    val bundle = Bundle()
    @Suppress("DEPRECATION") val loadedImageUri =
      if (isSaveBitmapToInstanceState && imageUri == null && mImageResource < 1) {
          BitmapUtils.writeTempStateStoreBitmap(
              context = context,
              bitmap = originalBitmap,
              customOutputUri = customOutputUri,
          )
      } else {
        imageUri
      }

    if (loadedImageUri != null && originalBitmap != null) {
      val key = UUID.randomUUID().toString()
      BitmapUtils.mStateBitmap = Pair(key, WeakReference(originalBitmap))
      bundle.putString("LOADED_IMAGE_STATE_BITMAP_KEY", key)
    }

    val task = bitmapLoadingWorkerJob?.get()
    if (task != null) {
      bundle.putParcelable("LOADING_IMAGE_URI", task.uri)
    }

    bundle.putParcelable("instanceState", super.onSaveInstanceState())
    bundle.putParcelable("LOADED_IMAGE_URI", loadedImageUri)
    bundle.putInt("LOADED_IMAGE_RESOURCE", mImageResource)
    bundle.putInt("LOADED_SAMPLE_SIZE", loadedSampleSize)
    bundle.putInt("DEGREES_ROTATED", mDegreesRotated)
    bundle.putParcelable("INITIAL_CROP_RECT", mCropOverlayView!!.initialCropWindowRect)
    BitmapUtils.RECT.set(mCropOverlayView.cropWindowRect)
    mImageMatrix.invert(mImageInverseMatrix)
    mImageInverseMatrix.mapRect(BitmapUtils.RECT)
    bundle.putParcelable("CROP_WINDOW_RECT", BitmapUtils.RECT)
    bundle.putString("CROP_SHAPE", mCropOverlayView.cropShape!!.name)
    bundle.putBoolean("CROP_AUTO_ZOOM_ENABLED", mAutoZoomEnabled)
    bundle.putInt("CROP_MAX_ZOOM", mMaxZoom)
    bundle.putBoolean("CROP_FLIP_HORIZONTALLY", mFlipHorizontally)
    bundle.putBoolean("CROP_FLIP_VERTICALLY", mFlipVertically)
    bundle.putBoolean("SHOW_CROP_LABEL", mShowCropLabel)
    return bundle
  }

  public override fun onRestoreInstanceState(state: Parcelable) {
    if (state is Bundle) {
      if (bitmapLoadingWorkerJob == null && imageUri == null && originalBitmap == null && mImageResource == 0) {
        var uri = state.parcelable<Uri>("LOADED_IMAGE_URI")
        if (uri != null) {
          val key = state.getString("LOADED_IMAGE_STATE_BITMAP_KEY")
          key?.run {
            val stateBitmap = BitmapUtils.mStateBitmap?.let {
              if (it.first == key) it.second.get() else null
            }
            BitmapUtils.mStateBitmap = null
            if (stateBitmap != null && !stateBitmap.isRecycled) {
              setBitmap(
                bitmap = stateBitmap,
                imageResource = 0,
                imageUri = uri,
                loadSampleSize = state.getInt("LOADED_SAMPLE_SIZE"),
                degreesRotated = 0,
              )
            }
          }
          imageUri ?: setImageUriAsync(uri)
        } else {
          val resId = state.getInt("LOADED_IMAGE_RESOURCE")

          if (resId > 0) {
            imageResource = resId
          } else {
            uri = state.parcelable("LOADING_IMAGE_URI")
            uri?.let { setImageUriAsync(it) }
          }
        }
        mRestoreDegreesRotated = state.getInt("DEGREES_ROTATED")
        mDegreesRotated = mRestoreDegreesRotated
        val initialCropRect = state.parcelable<Rect>("INITIAL_CROP_RECT")
        if (initialCropRect != null &&
          (initialCropRect.width() > 0 || initialCropRect.height() > 0)
        ) {
          mCropOverlayView!!.initialCropWindowRect = initialCropRect
        }
        val cropWindowRect = state.parcelable<RectF>("CROP_WINDOW_RECT")
        if (cropWindowRect != null && (cropWindowRect.width() > 0 || cropWindowRect.height() > 0)) {
          mRestoreCropWindowRect = cropWindowRect
        }
        mCropOverlayView!!.setCropShape(
          CropShape.valueOf(
            state.getString("CROP_SHAPE")!!,
          ),
        )
        mAutoZoomEnabled = state.getBoolean("CROP_AUTO_ZOOM_ENABLED")
        mMaxZoom = state.getInt("CROP_MAX_ZOOM")
        mFlipHorizontally = state.getBoolean("CROP_FLIP_HORIZONTALLY")
        mFlipVertically = state.getBoolean("CROP_FLIP_VERTICALLY")
        mShowCropLabel = state.getBoolean("SHOW_CROP_LABEL")
        mCropOverlayView.setCropperTextLabelVisibility(mShowCropLabel)
      }
      super.onRestoreInstanceState(state.parcelable("instanceState"))
    } else {
      super.onRestoreInstanceState(state)
    }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    val widthMode = MeasureSpec.getMode(widthMeasureSpec)
    val widthSize = MeasureSpec.getSize(widthMeasureSpec)
    val heightMode = MeasureSpec.getMode(heightMeasureSpec)
    var heightSize = MeasureSpec.getSize(heightMeasureSpec)
    val bitmap = originalBitmap
    if (bitmap != null) {
      if (heightSize == 0) heightSize = bitmap.height
      val desiredWidth: Int
      val desiredHeight: Int
      var viewToBitmapWidthRatio = Double.POSITIVE_INFINITY
      var viewToBitmapHeightRatio = Double.POSITIVE_INFINITY
      if (widthSize < bitmap.width) {
        viewToBitmapWidthRatio = widthSize.toDouble() / bitmap.width.toDouble()
      }
      if (heightSize < bitmap.height) {
        viewToBitmapHeightRatio = heightSize.toDouble() / bitmap.height.toDouble()
      }
      if (viewToBitmapWidthRatio != Double.POSITIVE_INFINITY ||
        viewToBitmapHeightRatio != Double.POSITIVE_INFINITY
      ) {
        if (viewToBitmapWidthRatio <= viewToBitmapHeightRatio) {
          desiredWidth = widthSize
          desiredHeight = (bitmap.height * viewToBitmapWidthRatio).toInt()
        } else {
          desiredHeight = heightSize
          desiredWidth = (bitmap.width * viewToBitmapHeightRatio).toInt()
        }
      } else {
        desiredWidth = bitmap.width
        desiredHeight = bitmap.height
      }
      val width = getOnMeasureSpec(widthMode, widthSize, desiredWidth)
      val height = getOnMeasureSpec(heightMode, heightSize, desiredHeight)
      mLayoutWidth = width
      mLayoutHeight = height
      setMeasuredDimension(mLayoutWidth, mLayoutHeight)
    } else {
      setMeasuredDimension(widthSize, heightSize)
    }
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    super.onLayout(changed, l, t, r, b)
    if (mLayoutWidth > 0 && mLayoutHeight > 0) {
      val origParams = this.layoutParams
      origParams.width = mLayoutWidth
      origParams.height = mLayoutHeight
      layoutParams = origParams
      if (originalBitmap != null) {
        applyImageMatrix(
          (r - l).toFloat(),
          (b - t).toFloat(),
          center = true,
          animate = false,
        )
        val restoreCropWindowRect = mRestoreCropWindowRect
        if (restoreCropWindowRect != null) {
          if (mRestoreDegreesRotated != mInitialDegreesRotated) {
            mDegreesRotated = mRestoreDegreesRotated
            applyImageMatrix(
              width = (r - l).toFloat(),
              height = (b - t).toFloat(),
              center = true,
              animate = false,
            )
            mRestoreDegreesRotated = 0
          }
          mImageMatrix.mapRect(mRestoreCropWindowRect)
          mCropOverlayView?.cropWindowRect = restoreCropWindowRect
          handleCropWindowChanged(inProgress = false, animate = false)
          mCropOverlayView?.fixCurrentCropWindowRect()
          mRestoreCropWindowRect = null
        } else if (mSizeChanged) {
          mSizeChanged = false
          handleCropWindowChanged(inProgress = false, animate = false)
        }
      } else {
        updateImageBounds(true)
      }
    } else {
      updateImageBounds(true)
    }
  }

  
  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    mSizeChanged = oldw > 0 && oldh > 0
  }

  
  private fun handleCropWindowChanged(inProgress: Boolean, animate: Boolean) {
    val width = width
    val height = height
    if (originalBitmap != null && width > 0 && height > 0) {
      val cropRect = mCropOverlayView!!.cropWindowRect
      if (inProgress) {
        if (cropRect.left < 0 || cropRect.top < 0 || cropRect.right > width || cropRect.bottom > height) {
          applyImageMatrix(
            width = width.toFloat(),
            height = height.toFloat(),
            center = false,
            animate = false,
          )
        }
      } else if (mAutoZoomEnabled || mZoom > 1) {
        var newZoom = 0f
        if (mZoom < mMaxZoom && cropRect.width() < width * 0.5f && cropRect.height() < height * 0.5f) {
          newZoom = min(
            mMaxZoom.toFloat(),
            min(
              width / (cropRect.width() / mZoom / 0.64f),
              height / (cropRect.height() / mZoom / 0.64f),
            ),
          )
        }
        if (mZoom > 1 && (cropRect.width() > width * 0.65f || cropRect.height() > height * 0.65f)) {
          newZoom = max(
            1f,
            min(
              width / (cropRect.width() / mZoom / 0.51f),
              height / (cropRect.height() / mZoom / 0.51f),
            ),
          )
        }
        if (!mAutoZoomEnabled) newZoom = 1f

        if (newZoom > 0 && newZoom != mZoom) {
          if (animate) {
            if (mAnimation == null) {
              mAnimation = CropImageAnimation(imageView, mCropOverlayView)
            }
            mAnimation!!.setStartState(mImagePoints, mImageMatrix)
          }
          mZoom = newZoom
          applyImageMatrix(width.toFloat(), height.toFloat(), true, animate)
        }
      }
      if (mOnSetCropWindowChangeListener != null && !inProgress) {
        mOnSetCropWindowChangeListener!!.onCropWindowChanged()
      }
    }
  }

  
  private fun applyImageMatrix(width: Float, height: Float, center: Boolean, animate: Boolean) {
    val bitmap = originalBitmap
    if (bitmap != null && width > 0 && height > 0) {
      mImageMatrix.invert(mImageInverseMatrix)
      val cropRect = mCropOverlayView!!.cropWindowRect
      mImageInverseMatrix.mapRect(cropRect)
      mImageMatrix.reset()
      mImageMatrix.postTranslate(
        (width - bitmap.width) / 2,
        (height - bitmap.height) / 2,
      )
      mapImagePointsByImageMatrix()
      if (mDegreesRotated > 0) {
        mImageMatrix.postRotate(
          mDegreesRotated.toFloat(),
            BitmapUtils.getRectCenterX(mImagePoints),
            BitmapUtils.getRectCenterY(mImagePoints),
        )
        mapImagePointsByImageMatrix()
      }
      val scale = min(
        width / BitmapUtils.getRectWidth(mImagePoints),
        height / BitmapUtils.getRectHeight(mImagePoints),
      )
      if (mScaleType == ScaleType.FIT_CENTER || mScaleType == ScaleType.CENTER_INSIDE && scale < 1 ||
        scale > 1 && mAutoZoomEnabled
      ) {
        mImageMatrix.postScale(
          scale,
          scale,
            BitmapUtils.getRectCenterX(mImagePoints),
            BitmapUtils.getRectCenterY(mImagePoints),
        )
        mapImagePointsByImageMatrix()
      } else if (mScaleType == ScaleType.CENTER_CROP) {
        mZoom = max(
          getWidth() / BitmapUtils.getRectWidth(mImagePoints),
          getHeight() / BitmapUtils.getRectHeight(mImagePoints),
        )
      }
      val scaleX = if (mFlipHorizontally) -mZoom else mZoom
      val scaleY = if (mFlipVertically) -mZoom else mZoom
      mImageMatrix.postScale(
        scaleX,
        scaleY,
          BitmapUtils.getRectCenterX(mImagePoints),
          BitmapUtils.getRectCenterY(mImagePoints),
      )
      mapImagePointsByImageMatrix()
      mImageMatrix.mapRect(cropRect)

      if (mScaleType == ScaleType.CENTER_CROP && center && !animate) {
        mZoomOffsetX = 0f
        mZoomOffsetY = 0f
      } else if (center) {
        mZoomOffsetX =
          if (width > BitmapUtils.getRectWidth(mImagePoints)) {
            0f
          } else {
            max(
              min(
                width / 2 - cropRect.centerX(),
                -BitmapUtils.getRectLeft(mImagePoints),
              ),
              getWidth() - BitmapUtils.getRectRight(mImagePoints),
            ) / scaleX
          }

        mZoomOffsetY =
          if (height > BitmapUtils.getRectHeight(mImagePoints)) {
            0f
          } else {
            max(
              min(
                height / 2 - cropRect.centerY(),
                -BitmapUtils.getRectTop(mImagePoints),
              ),
              getHeight() - BitmapUtils.getRectBottom(mImagePoints),
            ) / scaleY
          }
      } else {
        mZoomOffsetX = (
          min(
            max(mZoomOffsetX * scaleX, -cropRect.left),
            -cropRect.right + width,
          ) / scaleX
          )

        mZoomOffsetY = (
          min(
            max(mZoomOffsetY * scaleY, -cropRect.top),
            -cropRect.bottom + height,
          ) / scaleY
          )
      }
      mImageMatrix.postTranslate(mZoomOffsetX * scaleX, mZoomOffsetY * scaleY)
      cropRect.offset(mZoomOffsetX * scaleX, mZoomOffsetY * scaleY)
      mCropOverlayView.cropWindowRect = cropRect
      mapImagePointsByImageMatrix()
      mCropOverlayView.invalidate()
      if (animate) {
        mAnimation!!.setEndState(mImagePoints, mImageMatrix)
        imageView.startAnimation(mAnimation)
      } else {
        imageView.imageMatrix = mImageMatrix
      }
      updateImageBounds(false)
    }
  }

  
  private fun mapImagePointsByImageMatrix() {
    mImagePoints[0] = 0f
    mImagePoints[1] = 0f
    mImagePoints[2] = originalBitmap!!.width.toFloat()
    mImagePoints[3] = 0f
    mImagePoints[4] = originalBitmap!!.width.toFloat()
    mImagePoints[5] = originalBitmap!!.height.toFloat()
    mImagePoints[6] = 0f
    mImagePoints[7] = originalBitmap!!.height.toFloat()
    mImageMatrix.mapPoints(mImagePoints)
    mScaleImagePoints[0] = 0f
    mScaleImagePoints[1] = 0f
    mScaleImagePoints[2] = 100f
    mScaleImagePoints[3] = 0f
    mScaleImagePoints[4] = 100f
    mScaleImagePoints[5] = 100f
    mScaleImagePoints[6] = 0f
    mScaleImagePoints[7] = 100f
    mImageMatrix.mapPoints(mScaleImagePoints)
  }

  
  private fun setCropOverlayVisibility() {
    if (mCropOverlayView != null) {
      mCropOverlayView.visibility =
        if (mShowCropOverlay && originalBitmap != null) VISIBLE else INVISIBLE
    }
  }

  
  private fun setProgressBarVisibility() {
    val visible = (
      mShowProgressBar &&
        (
          originalBitmap == null && bitmapLoadingWorkerJob != null ||
            bitmapCroppingWorkerJob != null
          )
      )
    mProgressBar.visibility =
      if (visible) VISIBLE else INVISIBLE
  }

  
  private fun updateImageBounds(clear: Boolean) {
    if (originalBitmap != null && !clear) {
      val scaleFactorWidth =
        100f * loadedSampleSize / BitmapUtils.getRectWidth(mScaleImagePoints)
      val scaleFactorHeight =
        100f * loadedSampleSize / BitmapUtils.getRectHeight(mScaleImagePoints)
      mCropOverlayView!!.setCropWindowLimits(
        width.toFloat(),
        height.toFloat(),
        scaleFactorWidth,
        scaleFactorHeight,
      )
    }
    mCropOverlayView!!.setBounds(if (clear) null else mImagePoints, width, height)
  }

  
  enum class CropShape {
    RECTANGLE,
    OVAL,
    RECTANGLE_VERTICAL_ONLY,
    RECTANGLE_HORIZONTAL_ONLY,
  }

  
  enum class CropCornerShape {
    RECTANGLE,
    OVAL,
  }

  
  enum class ScaleType {
    
    FIT_CENTER,

    
    CENTER,

    
    CENTER_CROP,

    
    CENTER_INSIDE,
  }

  enum class Guidelines {
    
    OFF,

    
    ON_TOUCH,

    
    ON,
  }

  
  enum class RequestSizeOptions {
    
    NONE,

    
    SAMPLING,

    
    RESIZE_INSIDE,

    
    RESIZE_FIT,

    
    RESIZE_EXACT,
  }

  
  fun interface OnSetCropOverlayReleasedListener {
    
    fun onCropOverlayReleased(rect: Rect?)
  }

  
  fun interface OnSetCropOverlayMovedListener {
    
    fun onCropOverlayMoved(rect: Rect?)
  }

  
  fun interface OnSetCropWindowChangeListener {
    
    fun onCropWindowChanged()
  }

  
  fun interface OnSetImageUriCompleteListener {
    
    fun onSetImageUriComplete(view: CropImageView, uri: Uri, error: Exception?)
  }

  
  fun interface OnCropImageCompleteListener {
    
    fun onCropImageComplete(view: CropImageView, result: CropResult)
  }

  
  open class CropResult internal constructor(

    
    val originalUri: Uri?,
    
    val bitmap: Bitmap?,
    
    val uriContent: Uri?,
    
    val error: Exception?,
    
    val cropPoints: FloatArray,
    
    val cropRect: Rect?,
    
    val wholeImageRect: Rect?,
    
    val rotation: Int,
    
    val sampleSize: Int,
  ) {
    val isSuccessful: Boolean
      get() = error == null

    
    fun getBitmap(context: Context): Bitmap? = bitmap ?: try {
      when {
        SDK_INT >= 28 -> ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uriContent!!))
        else -> @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(context.contentResolver, uriContent)
      }
    } catch (e: Exception) {
      null
    }

    
    private fun getUriFilePath(context: Context, uniqueName: Boolean = false): String? =
      uriContent?.let { getFilePathFromUri(context, it, uniqueName) }
  }

  internal companion object {
    
    internal fun getOnMeasureSpec(
      measureSpecMode: Int,
      measureSpecSize: Int,
      desiredSize: Int,
    ): Int {
      return when (measureSpecMode) {
        MeasureSpec.EXACTLY -> measureSpecSize
        MeasureSpec.AT_MOST -> min(desiredSize, measureSpecSize,)
        else -> desiredSize
      }
    }
  }

  init {
    val options = (context as? Activity)?.intent?.getBundleExtra(CropImage.CROP_IMAGE_EXTRA_BUNDLE)?.parcelable(
        CropImage.CROP_IMAGE_EXTRA_OPTIONS
    ) ?: CropImageOptions()
    mScaleType = options.scaleType
    mAutoZoomEnabled = options.autoZoomEnabled
    mMaxZoom = options.maxZoom
    mCropLabelTextSize = options.cropperLabelTextSize
    mShowCropLabel = options.showCropLabel
    mShowCropOverlay = options.showCropOverlay
    mShowProgressBar = options.showProgressBar
    mFlipHorizontally = options.flipHorizontally
    mFlipVertically = options.flipVertically
    val inflater = LayoutInflater.from(context)
    val v = inflater.inflate(R.layout.crop_image_view, this, true)
    imageView = v.findViewById(R.id.ImageView_image)
    imageView.scaleType = ImageView.ScaleType.MATRIX
    mCropOverlayView = v.findViewById(R.id.CropOverlayView)
    mCropOverlayView.setCropWindowChangeListener(this)
    mCropOverlayView.setInitialAttributeValues(options)
    mProgressBar = v.findViewById(R.id.CropProgressBar)
    mProgressBar.indeterminateTintList = ColorStateList.valueOf(options.progressBarColor)
    setProgressBarVisibility()
  }

  override fun onCropWindowChanged(inProgress: Boolean) {
    handleCropWindowChanged(inProgress, true)
    if (inProgress) {
      mOnSetCropOverlayMovedListener?.onCropOverlayMoved(cropRect)
    } else {
      mOnCropOverlayReleasedListener?.onCropOverlayReleased(cropRect)
    }
  }
}