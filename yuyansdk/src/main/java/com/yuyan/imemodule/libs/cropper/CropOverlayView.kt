package com.yuyan.imemodule.libs.cropper

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import androidx.annotation.RequiresApi
import com.yuyan.imemodule.libs.cropper.CropImageView.CropShape
import com.yuyan.imemodule.libs.cropper.CropImageView.Guidelines
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal class CropOverlayView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
) : View(context, attrs) {
  internal companion object {
    
    internal fun getTextPaint(options: CropImageOptions): Paint =
      Paint().apply {
        strokeWidth = 1f
        textSize = options.cropperLabelTextSize
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        this.color = options.cropperLabelTextColor
      }

    
    internal fun getNewPaint(color: Int): Paint =
      Paint().apply {
        this.color = color
      }

    
    internal fun getNewPaintOrNull(thickness: Float, color: Int): Paint? =
      if (thickness > 0) {
        val borderPaint = Paint()
        borderPaint.color = color
        borderPaint.strokeWidth = thickness
        borderPaint.style = Paint.Style.STROKE
        borderPaint.isAntiAlias = true
        borderPaint
      } else {
        null
      }

    internal fun getNewPaintWithFill(color: Int): Paint {
      val borderPaint = Paint()
      borderPaint.color = color
      borderPaint.style = Paint.Style.FILL
      borderPaint.isAntiAlias = true
      return borderPaint
    }
  }

  private var mCropCornerRadius: Float = 0f
  private var mCircleCornerFillColor: Int? = null
  private var mOptions: CropImageOptions? = null

  
  private var mScaleDetector: ScaleGestureDetector? = null

  
  private var mMultiTouchEnabled = false

  
  private var mCenterMoveEnabled = true

  
  private val mCropWindowHandler = CropWindowHandler()

  
  private var mCropWindowChangeListener: CropWindowChangeListener? = null

  
  private val mDrawRect = RectF()

  
  private var mBorderPaint: Paint? = null

  
  private var mBorderCornerPaint: Paint? = null

  
  private var mGuidelinePaint: Paint? = null

  
  private var mBackgroundPaint: Paint? = null

  private var textLabelPaint: Paint? = null

  
  private var currentPointerId: Int? = null

  
  private val mPath = Path()

  
  private val mBoundsPoints = FloatArray(8)

  
  private val mCalcBounds = RectF()

  
  private var mViewWidth = 0

  
  private var mViewHeight = 0

  
  private var mBorderCornerOffset = 0f

  
  private var mBorderCornerLength = 0f

  
  private var mInitialCropWindowPaddingRatio = 0f

  
  private var mTouchRadius = 0f

  
  private var mSnapRadius = 0f

  
  private var mMoveHandler: CropWindowMoveHandler? = null
  
  
  var isFixAspectRatio = false
    private set

  
  private var mAspectRatioX = 0

  
  private var mAspectRatioY = 0

  
  private var mTargetAspectRatio = mAspectRatioX.toFloat() / mAspectRatioY
  
  
  private var guidelines: Guidelines? = null
  
  
  var cropShape: CropShape? = null
    private set

  
  private var cornerShape: CropImageView.CropCornerShape? = null

  
  private var isCropLabelEnabled: Boolean = false

  
  private var cropLabelText: String = ""

  
  private var cropLabelTextSize: Float = 20f

  
  private var cropLabelTextColor = Color.WHITE

  
  private val mInitialCropWindowRect = Rect()

  
  private var initializedCropWindow = false

  
  private val maxVerticalGestureExclusion = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200f, Resources.getSystem().displayMetrics)

  
  fun setCropWindowChangeListener(listener: CropWindowChangeListener?) {
    mCropWindowChangeListener = listener
  }

  
  
  var cropWindowRect: RectF
    get() = mCropWindowHandler.getRect()
    set(rect) {
      mCropWindowHandler.setRect(rect)
    }

  
  fun fixCurrentCropWindowRect() {
    val rect = cropWindowRect
    fixCropWindowRectByRules(rect)
    mCropWindowHandler.setRect(rect)
  }

  
  fun setBounds(boundsPoints: FloatArray?, viewWidth: Int, viewHeight: Int) {
    if (boundsPoints == null || !mBoundsPoints.contentEquals(boundsPoints)) {
      if (boundsPoints == null) {
        Arrays.fill(mBoundsPoints, 0f)
      } else {
        System.arraycopy(boundsPoints, 0, mBoundsPoints, 0, boundsPoints.size)
      }

      mViewWidth = viewWidth
      mViewHeight = viewHeight
      val cropRect = mCropWindowHandler.getRect()
      if (cropRect.width() == 0f || cropRect.height() == 0f) initCropWindow()
    }
  }

  
  fun resetCropOverlayView() {
    if (initializedCropWindow) {
      cropWindowRect = BitmapUtils.EMPTY_RECT_F
      initCropWindow()
      invalidate()
    }
  }

  
  fun setCropShape(cropShape: CropShape) {
    if (this.cropShape != cropShape) {
      this.cropShape = cropShape
      invalidate()
    }
  }

  
  fun setCropperTextLabelVisibility(isEnabled: Boolean) {
    this.isCropLabelEnabled = isEnabled
    invalidate()
  }

  
  
  var aspectRatioX: Int
    get() = mAspectRatioX
    set(aspectRatioX) {
      require(aspectRatioX > 0) { "Cannot set aspect ratio value to a number less than or equal to 0." }
      if (mAspectRatioX != aspectRatioX) {
        mAspectRatioX = aspectRatioX
        mTargetAspectRatio = mAspectRatioX.toFloat() / mAspectRatioY
        if (initializedCropWindow) {
          initCropWindow()
          invalidate()
        }
      }
    }
  
  
  var aspectRatioY: Int
    get() = mAspectRatioY
    set(aspectRatioY) {
      require(aspectRatioY > 0) { "Cannot set aspect ratio value to a number less than or equal to 0." }
      if (mAspectRatioY != aspectRatioY) {
        mAspectRatioY = aspectRatioY
        mTargetAspectRatio = mAspectRatioX.toFloat() / mAspectRatioY
        if (initializedCropWindow) {
          initCropWindow()
          invalidate()
        }
      }
    }

  
  fun setCropWindowLimits(
    maxWidth: Float,
    maxHeight: Float,
    scaleFactorWidth: Float,
    scaleFactorHeight: Float,
  ) {
    mCropWindowHandler
      .setCropWindowLimits(maxWidth, maxHeight, scaleFactorWidth, scaleFactorHeight)
  }
  
  
  var initialCropWindowRect: Rect?
    get() = mInitialCropWindowRect
    set(rect) {
      mInitialCropWindowRect.set(rect ?: BitmapUtils.EMPTY_RECT)
      if (initializedCropWindow) {
        initCropWindow()
        invalidate()
        mCropWindowChangeListener?.onCropWindowChanged(false)
      }
    }

  
  fun setInitialAttributeValues(options: CropImageOptions) {
    val isDifferent = mOptions != options
    val cropWindowChanged = options.fixAspectRatio != mOptions?.fixAspectRatio ||
      options.aspectRatioX != mOptions?.aspectRatioX ||
      options.aspectRatioY != mOptions?.aspectRatioY

    mOptions = options

    mCropWindowHandler.setMinCropResultSize(options.minCropResultWidth, options.minCropResultHeight)
    mCropWindowHandler.setMaxCropResultSize(options.maxCropResultWidth, options.maxCropResultHeight)

    if (!isDifferent) {
      return
    }

    mCropWindowHandler.setInitialAttributeValues(options)
    cropLabelTextColor = options.cropperLabelTextColor
    cropLabelTextSize = options.cropperLabelTextSize
    cropLabelText = options.cropperLabelText.orEmpty()
    isCropLabelEnabled = options.showCropLabel
    mCropCornerRadius = options.cropCornerRadius
    cornerShape = options.cornerShape
    cropShape = options.cropShape
    mSnapRadius = options.snapRadius
    isEnabled = options.canChangeCropWindow
    guidelines = options.guidelines
    isFixAspectRatio = options.fixAspectRatio
    aspectRatioX = options.aspectRatioX
    aspectRatioY = options.aspectRatioY
    mMultiTouchEnabled = options.multiTouchEnabled
    if (mMultiTouchEnabled && mScaleDetector == null) {
      mScaleDetector = ScaleGestureDetector(context, ScaleListener())
    }
    mCenterMoveEnabled = options.centerMoveEnabled
    mTouchRadius = options.touchRadius
    mInitialCropWindowPaddingRatio = options.initialCropWindowPaddingRatio
    mBorderPaint = getNewPaintOrNull(options.borderLineThickness, options.borderLineColor)
    mBorderCornerOffset = options.borderCornerOffset
    mBorderCornerLength = options.borderCornerLength
    mCircleCornerFillColor = options.circleCornerFillColorHexValue
    mBorderCornerPaint = getNewPaintOrNull(options.borderCornerThickness, options.borderCornerColor)
    mGuidelinePaint = getNewPaintOrNull(options.guidelinesThickness, options.guidelinesColor)
    mBackgroundPaint = getNewPaint(options.backgroundColor)
    textLabelPaint = getTextPaint(options)

    if (cropWindowChanged) {
      initCropWindow()
    }

    invalidate()

    if (cropWindowChanged) {
      mCropWindowChangeListener?.onCropWindowChanged(false)
    }
  }

  
  private fun initCropWindow() {
    val leftLimit = max(BitmapUtils.getRectLeft(mBoundsPoints), 0f)
    val topLimit = max(BitmapUtils.getRectTop(mBoundsPoints), 0f)
    val rightLimit = min(BitmapUtils.getRectRight(mBoundsPoints), width.toFloat())
    val bottomLimit = min(BitmapUtils.getRectBottom(mBoundsPoints), height.toFloat())
    if (rightLimit <= leftLimit || bottomLimit <= topLimit) return
    val rect = RectF()
    initializedCropWindow = true
    val horizontalPadding = mInitialCropWindowPaddingRatio * (rightLimit - leftLimit)
    val verticalPadding = mInitialCropWindowPaddingRatio * (bottomLimit - topLimit)
    if (mInitialCropWindowRect.width() > 0 && mInitialCropWindowRect.height() > 0) {
      rect.left = leftLimit + mInitialCropWindowRect.left / mCropWindowHandler.getScaleFactorWidth()
      rect.top = topLimit + mInitialCropWindowRect.top / mCropWindowHandler.getScaleFactorHeight()
      rect.right = rect.left + mInitialCropWindowRect.width() / mCropWindowHandler.getScaleFactorWidth()
      rect.bottom = rect.top + mInitialCropWindowRect.height() / mCropWindowHandler.getScaleFactorHeight()
      rect.left = max(leftLimit, rect.left)
      rect.top = max(topLimit, rect.top)
      rect.right = min(rightLimit, rect.right)
      rect.bottom = min(bottomLimit, rect.bottom)
    } else if (isFixAspectRatio) {
      val bitmapAspectRatio = (rightLimit - leftLimit) / (bottomLimit - topLimit)
      if (bitmapAspectRatio > mTargetAspectRatio) {
        rect.top = topLimit + verticalPadding
        rect.bottom = bottomLimit - verticalPadding
        val centerX = width / 2f
        mTargetAspectRatio = mAspectRatioX.toFloat() / mAspectRatioY
        val cropWidth = max(
          mCropWindowHandler.getMinCropWidth(),
          rect.height() * mTargetAspectRatio,
        )
        val halfCropWidth = cropWidth / 2f
        rect.left = centerX - halfCropWidth
        rect.right = centerX + halfCropWidth
      } else {
        rect.left = leftLimit + horizontalPadding
        rect.right = rightLimit - horizontalPadding
        val centerY = height / 2f
        val cropHeight = max(
          mCropWindowHandler.getMinCropHeight(),
          rect.width() / mTargetAspectRatio,
        )
        val halfCropHeight = cropHeight / 2f
        rect.top = centerY - halfCropHeight
        rect.bottom = centerY + halfCropHeight
      }
    } else {
      rect.left = leftLimit + horizontalPadding
      rect.top = topLimit + verticalPadding
      rect.right = rightLimit - horizontalPadding
      rect.bottom = bottomLimit - verticalPadding
    }
    fixCropWindowRectByRules(rect)
    mCropWindowHandler.setRect(rect)
  }

  
  private fun fixCropWindowRectByRules(rect: RectF) {
    if (rect.width() < mCropWindowHandler.getMinCropWidth()) {
      val adj = (mCropWindowHandler.getMinCropWidth() - rect.width()) / 2
      rect.left -= adj
      rect.right += adj
    }

    if (rect.height() < mCropWindowHandler.getMinCropHeight()) {
      val adj = (mCropWindowHandler.getMinCropHeight() - rect.height()) / 2
      rect.top -= adj
      rect.bottom += adj
    }

    if (rect.width() > mCropWindowHandler.getMaxCropWidth()) {
      val adj = (rect.width() - mCropWindowHandler.getMaxCropWidth()) / 2
      rect.left += adj
      rect.right -= adj
    }

    if (rect.height() > mCropWindowHandler.getMaxCropHeight()) {
      val adj = (rect.height() - mCropWindowHandler.getMaxCropHeight()) / 2
      rect.top += adj
      rect.bottom -= adj
    }

    calculateBounds(rect)

    if (mCalcBounds.width() > 0 && mCalcBounds.height() > 0) {
      val leftLimit = max(mCalcBounds.left, 0f)
      val topLimit = max(mCalcBounds.top, 0f)
      val rightLimit = min(mCalcBounds.right, width.toFloat())
      val bottomLimit = min(mCalcBounds.bottom, height.toFloat())

      if (rect.left < leftLimit) rect.left = leftLimit
      if (rect.top < topLimit) rect.top = topLimit
      if (rect.right > rightLimit) rect.right = rightLimit
      if (rect.bottom > bottomLimit) rect.bottom = bottomLimit
    }

    if (isFixAspectRatio && abs(rect.width() - rect.height() * mTargetAspectRatio) > 0.1) {
      if (rect.width() > rect.height() * mTargetAspectRatio) {
        val adj = abs(rect.height() * mTargetAspectRatio - rect.width()) / 2
        rect.left += adj
        rect.right -= adj
      } else {
        val adj = abs(rect.width() / mTargetAspectRatio - rect.height()) / 2
        rect.top += adj
        rect.bottom -= adj
      }
    }
  }

  
  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    drawBackground(canvas)
    if (mCropWindowHandler.showGuidelines()) {
      if (guidelines == Guidelines.ON) {
        drawGuidelines(canvas)
      } else if (guidelines == Guidelines.ON_TOUCH && mMoveHandler != null) {
        drawGuidelines(
          canvas,
        )
      }
    }
    mBorderCornerPaint = getNewPaintOrNull(mOptions?.borderCornerThickness ?: 0.0f, mOptions?.borderCornerColor ?: Color.WHITE)
    drawCropLabelText(canvas)
    drawBorders(canvas)
    drawCorners(canvas)

    if (SDK_INT >= Build.VERSION_CODES.Q) {
      setSystemGestureExclusionRects()
    }
  }

  
  @RequiresApi(Build.VERSION_CODES.Q)
  private fun setSystemGestureExclusionRects() {
    val cropWindowRect = mCropWindowHandler.getRect()
    val rectTop = systemGestureExclusionRects.getOrElse(0) { Rect() }
    val rectMiddle = systemGestureExclusionRects.getOrElse(1) { Rect() }
    val rectBottom = systemGestureExclusionRects.getOrElse(2) { Rect() }

    rectTop.left = (cropWindowRect.left - mTouchRadius).toInt()
    rectTop.right = (cropWindowRect.right + mTouchRadius).toInt()
    rectTop.top = (cropWindowRect.top - mTouchRadius).toInt()
    rectTop.bottom = (rectTop.top + (maxVerticalGestureExclusion * 0.3f)).toInt()

    rectMiddle.left = rectTop.left
    rectMiddle.right = rectTop.right
    rectMiddle.top = ((cropWindowRect.top + cropWindowRect.bottom) / 2.0f - (maxVerticalGestureExclusion * 0.2f)).toInt()
    rectMiddle.bottom = (rectMiddle.top + (maxVerticalGestureExclusion * 0.4f)).toInt()

    rectBottom.left = rectTop.left
    rectBottom.right = rectTop.right
    rectBottom.bottom = (cropWindowRect.bottom + mTouchRadius).toInt()
    rectBottom.top = (rectBottom.bottom - (maxVerticalGestureExclusion * 0.3f)).toInt()

    systemGestureExclusionRects = listOf(rectTop, rectMiddle, rectBottom)
  }

  
  private fun drawCropLabelText(canvas: Canvas) {
    if (isCropLabelEnabled) {
      val rect = mCropWindowHandler.getRect()
      val xCoordinate = (rect.left + rect.right) / 2
      val yCoordinate = rect.top - 50
      textLabelPaint?.apply {
        textSize = cropLabelTextSize
        color = cropLabelTextColor
      }
      canvas.drawText(cropLabelText, xCoordinate, yCoordinate, textLabelPaint!!)
      canvas.save()
    }
  }

  
  private fun drawBackground(canvas: Canvas) {
    val rect = mCropWindowHandler.getRect()
    val left = max(BitmapUtils.getRectLeft(mBoundsPoints), 0f)
    val top = max(BitmapUtils.getRectTop(mBoundsPoints), 0f)
    val right = min(BitmapUtils.getRectRight(mBoundsPoints), width.toFloat())
    val bottom = min(BitmapUtils.getRectBottom(mBoundsPoints), height.toFloat())
    when (cropShape) {
      CropShape.RECTANGLE,
      CropShape.RECTANGLE_VERTICAL_ONLY,
      CropShape.RECTANGLE_HORIZONTAL_ONLY,
      ->
        if (!isNonStraightAngleRotated) {
          canvas.drawRect(left, top, right, rect.top, mBackgroundPaint!!)
          canvas.drawRect(left, rect.bottom, right, bottom, mBackgroundPaint!!)
          canvas.drawRect(left, rect.top, rect.left, rect.bottom, mBackgroundPaint!!)
          canvas.drawRect(rect.right, rect.top, right, rect.bottom, mBackgroundPaint!!)
        } else {
          mPath.reset()
          mPath.moveTo(mBoundsPoints[0], mBoundsPoints[1])
          mPath.lineTo(mBoundsPoints[2], mBoundsPoints[3])
          mPath.lineTo(mBoundsPoints[4], mBoundsPoints[5])
          mPath.lineTo(mBoundsPoints[6], mBoundsPoints[7])
          mPath.close()
          canvas.save()

          if (SDK_INT >= 26) {
            canvas.clipOutPath(mPath)
          } else {
            @Suppress("DEPRECATION") canvas.clipPath(mPath, Region.Op.INTERSECT)
          }

          canvas.drawRect(left, top, right, bottom, mBackgroundPaint!!)
          canvas.restore()
        }
      CropShape.OVAL -> {
        mPath.reset()
        mDrawRect[rect.left, rect.top, rect.right] = rect.bottom

        mPath.addOval(mDrawRect, Path.Direction.CW)
        canvas.save()

        if (SDK_INT >= 26) {
          canvas.clipOutPath(mPath)
        } else {
          @Suppress("DEPRECATION")
          canvas.clipPath(mPath, Region.Op.XOR)
        }

        canvas.drawRect(left, top, right, bottom, mBackgroundPaint!!)
        canvas.restore()
      }
      else -> throw IllegalStateException("Unrecognized crop shape")
    }
  }

  
  private fun drawGuidelines(canvas: Canvas) {
    if (mGuidelinePaint != null) {
      val sw: Float = if (mBorderPaint != null) mBorderPaint!!.strokeWidth else 0f
      val rect = mCropWindowHandler.getRect()
      rect.inset(sw, sw)
      val oneThirdCropWidth = rect.width() / 3
      val oneThirdCropHeight = rect.height() / 3
      val x1: Float
      val x2: Float
      val y1: Float
      val y2: Float
      when (cropShape) {
        CropShape.OVAL -> {
          val w = rect.width() / 2 - sw
          val h = rect.height() / 2 - sw
          x1 = rect.left + oneThirdCropWidth
          x2 = rect.right - oneThirdCropWidth
          val yv = (h * sin(acos(((w - oneThirdCropWidth) / w).toDouble()))).toFloat()

          canvas
            .drawLine(
              x1,
              rect.top + h - yv,
              x1,
              rect.bottom - h + yv,
              mGuidelinePaint!!,
            )
          canvas
            .drawLine(
              x2,
              rect.top + h - yv,
              x2,
              rect.bottom - h + yv,
              mGuidelinePaint!!,
            )
          y1 = rect.top + oneThirdCropHeight
          y2 = rect.bottom - oneThirdCropHeight
          val xv = (w * cos(asin(((h - oneThirdCropHeight) / h).toDouble()))).toFloat()
          canvas
            .drawLine(
              rect.left + w - xv,
              y1,
              rect.right - w + xv,
              y1,
              mGuidelinePaint!!,
            )
          canvas
            .drawLine(
              rect.left + w - xv,
              y2,
              rect.right - w + xv,
              y2,
              mGuidelinePaint!!,
            )
        }
        CropShape.RECTANGLE,
        CropShape.RECTANGLE_VERTICAL_ONLY,
        CropShape.RECTANGLE_HORIZONTAL_ONLY,
        -> {
          x1 = rect.left + oneThirdCropWidth
          x2 = rect.right - oneThirdCropWidth
          canvas.drawLine(x1, rect.top, x1, rect.bottom, mGuidelinePaint!!)
          canvas.drawLine(x2, rect.top, x2, rect.bottom, mGuidelinePaint!!)
          y1 = rect.top + oneThirdCropHeight
          y2 = rect.bottom - oneThirdCropHeight
          canvas.drawLine(rect.left, y1, rect.right, y1, mGuidelinePaint!!)
          canvas.drawLine(rect.left, y2, rect.right, y2, mGuidelinePaint!!)
        }
        else -> throw IllegalStateException("Unrecognized crop shape")
      }
    }
  }

  
  private fun drawBorders(canvas: Canvas) {
    if (mBorderPaint != null) {
      val w = mBorderPaint!!.strokeWidth
      val rect = mCropWindowHandler.getRect()
      rect.inset(w / 2, w / 2)

      when (cropShape) {
        CropShape.RECTANGLE_VERTICAL_ONLY,
        CropShape.RECTANGLE_HORIZONTAL_ONLY,
        CropShape.RECTANGLE,
        -> canvas.drawRect(rect, mBorderPaint!!)
        CropShape.OVAL -> canvas.drawOval(rect, mBorderPaint!!)
        else -> throw IllegalStateException("Unrecognized crop shape")
      }
    }
  }

  
  private fun drawCorners(canvas: Canvas) {
    if (mBorderCornerPaint != null) {
      val lineWidth: Float = if (mBorderPaint != null) mBorderPaint!!.strokeWidth else 0f
      val cornerWidth = mBorderCornerPaint!!.strokeWidth
      val cornerOffset = (cornerWidth - lineWidth) / 2
      val cornerExtension = cornerWidth / 2 + cornerOffset
      val w: Float = when (cropShape) {
        CropShape.RECTANGLE_VERTICAL_ONLY,
        CropShape.RECTANGLE_HORIZONTAL_ONLY,
        CropShape.RECTANGLE,
        -> cornerWidth / 2 + mBorderCornerOffset
        CropShape.OVAL -> cornerWidth / 2
        else -> throw IllegalStateException("Unrecognized crop shape")
      }
      val rect = mCropWindowHandler.getRect()
      rect.inset(w, w)
      drawCornerBasedOnShape(canvas, rect, cornerOffset, cornerExtension)
      if (cornerShape == CropImageView.CropCornerShape.OVAL) {
        mBorderCornerPaint = mCircleCornerFillColor?.let { getNewPaintWithFill(it) }
        drawCornerBasedOnShape(canvas, rect, cornerOffset, cornerExtension)
      }
    }
  }

  
  private fun drawCornerBasedOnShape(
    canvas: Canvas,
    rect: RectF,
    cornerOffset: Float,
    cornerExtension: Float,
  ) {
    when (cropShape) {
      CropShape.RECTANGLE -> {
        drawCornerShape(canvas, rect, cornerOffset, cornerExtension, mCropCornerRadius)
      }
      CropShape.OVAL -> {
        drawLineShape(canvas, rect, cornerOffset, cornerExtension)
      }
      CropShape.RECTANGLE_VERTICAL_ONLY -> {
        canvas.drawLine(
          rect.centerX() - mBorderCornerLength,
          rect.top - cornerOffset,
          rect.centerX() + mBorderCornerLength,
          rect.top - cornerOffset,
          mBorderCornerPaint!!,
        )
        canvas.drawLine(
          rect.centerX() - mBorderCornerLength,
          rect.bottom + cornerOffset,
          rect.centerX() + mBorderCornerLength,
          rect.bottom + cornerOffset,
          mBorderCornerPaint!!,
        )
      }
      CropShape.RECTANGLE_HORIZONTAL_ONLY -> {
        canvas.drawLine(
          rect.left - cornerOffset,
          rect.centerY() - mBorderCornerLength,
          rect.left - cornerOffset,
          rect.centerY() + mBorderCornerLength,
          mBorderCornerPaint!!,
        )
        canvas.drawLine(
          rect.right + cornerOffset,
          rect.centerY() - mBorderCornerLength,
          rect.right + cornerOffset,
          rect.centerY() + mBorderCornerLength,
          mBorderCornerPaint!!,
        )
      }
      else -> throw IllegalStateException("Unrecognized crop shape")
    }
  }

  
  private fun drawLineShape(
    canvas: Canvas,
    rect: RectF,
    cornerOffset: Float,
    cornerExtension: Float,
  ) {
    canvas.drawLine(
      rect.left - cornerOffset,
      rect.top - cornerExtension,
      rect.left - cornerOffset,
      rect.top + mBorderCornerLength,
      mBorderCornerPaint!!,
    )
    canvas.drawLine(
      rect.left - cornerExtension,
      rect.top - cornerOffset,
      rect.left + mBorderCornerLength,
      rect.top - cornerOffset,
      mBorderCornerPaint!!,
    )
    canvas.drawLine(
      rect.right + cornerOffset,
      rect.top - cornerExtension,
      rect.right + cornerOffset,
      rect.top + mBorderCornerLength,
      mBorderCornerPaint!!,
    )
    canvas.drawLine(
      rect.right + cornerExtension,
      rect.top - cornerOffset,
      rect.right - mBorderCornerLength,
      rect.top - cornerOffset,
      mBorderCornerPaint!!,
    )
    canvas.drawLine(
      rect.left - cornerOffset,
      rect.bottom + cornerExtension,
      rect.left - cornerOffset,
      rect.bottom - mBorderCornerLength,
      mBorderCornerPaint!!,
    )
    canvas.drawLine(
      rect.left - cornerExtension,
      rect.bottom + cornerOffset,
      rect.left + mBorderCornerLength,
      rect.bottom + cornerOffset,
      mBorderCornerPaint!!,
    )
    canvas.drawLine(
      rect.right + cornerOffset,
      rect.bottom + cornerExtension,
      rect.right + cornerOffset,
      rect.bottom - mBorderCornerLength,
      mBorderCornerPaint!!,
    )
    canvas.drawLine(
      rect.right + cornerExtension,
      rect.bottom + cornerOffset,
      rect.right - mBorderCornerLength,
      rect.bottom + cornerOffset,
      mBorderCornerPaint!!,
    )
  }

  
  private fun drawCornerShape(
    canvas: Canvas,
    rect: RectF,
    cornerOffset: Float,
    cornerExtension: Float,
    radius: Float,
  ) {
    when (cornerShape) {
      CropImageView.CropCornerShape.OVAL -> {
        drawCircleShape(canvas, rect, cornerOffset, radius)
      }
      CropImageView.CropCornerShape.RECTANGLE -> drawLineShape(canvas, rect, cornerOffset, cornerExtension)
      null -> Unit
    }
  }

  
  private fun drawCircleShape(
    canvas: Canvas,
    rect: RectF,
    cornerExtension: Float,
    radius: Float,
  ) {
    canvas.drawCircle(
      rect.left - cornerExtension,
      (rect.top - cornerExtension),
      radius,
      mBorderCornerPaint!!,
    )
    canvas.drawCircle(
      rect.right + cornerExtension,
      rect.top - cornerExtension,
      radius,
      mBorderCornerPaint!!,
    )
    canvas.drawCircle(
      rect.left - cornerExtension,
      rect.bottom + cornerExtension,
      radius,
      mBorderCornerPaint!!,
    )
    canvas.drawCircle(
      rect.right + cornerExtension,
      rect.bottom + cornerExtension,
      radius,
      mBorderCornerPaint!!,
    )
  }

  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    return if (isEnabled) {
      if (mMultiTouchEnabled) mScaleDetector?.onTouchEvent(event)

      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          currentPointerId = event.getPointerId(0)
          onActionDown(event.x, event.y)
          true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          currentPointerId = event.getPointerId(0)
          parent.requestDisallowInterceptTouchEvent(false)
          onActionUp()
          true
        }
        MotionEvent.ACTION_MOVE -> when {
          currentPointerId != event.getPointerId(0) -> false
          else -> {
            onActionMove(event.x, event.y)
            parent.requestDisallowInterceptTouchEvent(true)
            true
          }
        }
        else -> false
      }
    } else {
      false
    }
  }

  
  private fun onActionDown(x: Float, y: Float) {
    mMoveHandler =
      mCropWindowHandler.getMoveHandler(x, y, mTouchRadius, cropShape!!, mCenterMoveEnabled)

    if (mMoveHandler != null) invalidate()
  }

  
  private fun onActionUp() {
    if (mMoveHandler != null) {
      mMoveHandler = null
      mCropWindowChangeListener?.onCropWindowChanged(false)
      invalidate()
    }
  }

  
  private fun onActionMove(x: Float, y: Float) {
    if (mMoveHandler != null) {
      var snapRadius = mSnapRadius
      val rect = mCropWindowHandler.getRect()
      if (calculateBounds(rect)) {
        snapRadius = 0f
      }
      mMoveHandler!!.move(
        rect,
        x,
        y,
        mCalcBounds,
        mViewWidth,
        mViewHeight,
        snapRadius,
        isFixAspectRatio,
        mTargetAspectRatio,
      )
      mCropWindowHandler.setRect(rect)
      mCropWindowChangeListener?.onCropWindowChanged(true)
      invalidate()
    }
  }

  
  private fun calculateBounds(rect: RectF): Boolean {
    var left = BitmapUtils.getRectLeft(mBoundsPoints)
    var top = BitmapUtils.getRectTop(mBoundsPoints)
    var right = BitmapUtils.getRectRight(mBoundsPoints)
    var bottom = BitmapUtils.getRectBottom(mBoundsPoints)

    return if (!isNonStraightAngleRotated) {
      mCalcBounds[left, top, right] = bottom
      false
    } else {
      var x0 = mBoundsPoints[0]
      var y0 = mBoundsPoints[1]
      var x2 = mBoundsPoints[4]
      var y2 = mBoundsPoints[5]
      var x3 = mBoundsPoints[6]
      var y3 = mBoundsPoints[7]
      if (mBoundsPoints[7] < mBoundsPoints[1]) {
        if (mBoundsPoints[1] < mBoundsPoints[3]) {
          x0 = mBoundsPoints[6]
          y0 = mBoundsPoints[7]
          x2 = mBoundsPoints[2]
          y2 = mBoundsPoints[3]
          x3 = mBoundsPoints[4]
          y3 = mBoundsPoints[5]
        } else {
          x0 = mBoundsPoints[4]
          y0 = mBoundsPoints[5]
          x2 = mBoundsPoints[0]
          y2 = mBoundsPoints[1]
          x3 = mBoundsPoints[2]
          y3 = mBoundsPoints[3]
        }
      } else if (mBoundsPoints[1] > mBoundsPoints[3]) {
        x0 = mBoundsPoints[2]
        y0 = mBoundsPoints[3]
        x2 = mBoundsPoints[6]
        y2 = mBoundsPoints[7]
        x3 = mBoundsPoints[0]
        y3 = mBoundsPoints[1]
      }
      val a0 = (y3 - y0) / (x3 - x0)
      val a1 = -1f / a0
      val b0 = y0 - a0 * x0
      val b1 = y0 - a1 * x0
      val b2 = y2 - a0 * x2
      val b3 = y2 - a1 * x2
      val c0 = (rect.centerY() - rect.top) / (rect.centerX() - rect.left)
      val c1 = -c0
      val d0 = rect.top - c0 * rect.left
      val d1 = rect.top - c1 * rect.right
      left = max(
        left,
        if ((d0 - b0) / (a0 - c0) < rect.right) (d0 - b0) / (a0 - c0) else left,
      )
      left = max(
        left,
        if ((d0 - b1) / (a1 - c0) < rect.right) (d0 - b1) / (a1 - c0) else left,
      )
      left = max(
        left,
        if ((d1 - b3) / (a1 - c1) < rect.right) (d1 - b3) / (a1 - c1) else left,
      )
      right = min(
        right,
        if ((d1 - b1) / (a1 - c1) > rect.left) (d1 - b1) / (a1 - c1) else right,
      )
      right = min(
        right,
        if ((d1 - b2) / (a0 - c1) > rect.left) (d1 - b2) / (a0 - c1) else right,
      )
      right = min(
        right,
        if ((d0 - b2) / (a0 - c0) > rect.left) (d0 - b2) / (a0 - c0) else right,
      )
      top = max(top, max(a0 * left + b0, a1 * right + b1))
      bottom = min(bottom, min(a1 * left + b3, a0 * right + b2))
      mCalcBounds.left = left
      mCalcBounds.top = top
      mCalcBounds.right = right
      mCalcBounds.bottom = bottom
      true
    }
  }

  
  private val isNonStraightAngleRotated: Boolean
    get() = mBoundsPoints[0] != mBoundsPoints[6] && mBoundsPoints[1] != mBoundsPoints[7]

  
  internal fun interface CropWindowChangeListener {
    
    fun onCropWindowChanged(inProgress: Boolean)
  }

  
  private inner class ScaleListener : SimpleOnScaleGestureListener() {
    override fun onScale(detector: ScaleGestureDetector): Boolean {
      val rect = mCropWindowHandler.getRect()
      val x = detector.focusX
      val y = detector.focusY
      val dY = detector.currentSpanY / 2
      val dX = detector.currentSpanX / 2
      val newTop = y - dY
      val newLeft = x - dX
      val newRight = x + dX
      val newBottom = y + dY

      if (newLeft < newRight &&
        newTop <= newBottom &&
        newLeft >= 0 &&
        newRight <= mCropWindowHandler.getMaxCropWidth() &&
        newTop >= 0 &&
        newBottom <= mCropWindowHandler.getMaxCropHeight()
      ) {
        rect[newLeft, newTop, newRight] = newBottom
        mCropWindowHandler.setRect(rect)
        invalidate()
      }
      return true
    }
  }
}