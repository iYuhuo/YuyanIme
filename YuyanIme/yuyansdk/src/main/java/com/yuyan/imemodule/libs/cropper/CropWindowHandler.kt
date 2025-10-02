package com.yuyan.imemodule.libs.cropper

import android.graphics.RectF
import com.yuyan.imemodule.libs.cropper.CropImageView.CropShape.OVAL
import com.yuyan.imemodule.libs.cropper.CropImageView.CropShape.RECTANGLE
import com.yuyan.imemodule.libs.cropper.CropImageView.CropShape.RECTANGLE_HORIZONTAL_ONLY
import com.yuyan.imemodule.libs.cropper.CropImageView.CropShape.RECTANGLE_VERTICAL_ONLY
import kotlin.math.abs
import kotlin.math.max

internal class CropWindowHandler {
  
  private val mEdges = RectF()

  
  private val mGetEdges = RectF()

  
  private var mMinCropWindowWidth = 0f

  
  private var mMinCropWindowHeight = 0f

  
  private var mMaxCropWindowWidth = 0f

  
  private var mMaxCropWindowHeight = 0f

  
  private var mMinCropResultWidth = 0f

  
  private var mMinCropResultHeight = 0f

  
  private var mMaxCropResultWidth = 0f

  
  private var mMaxCropResultHeight = 0f

  
  private var mScaleFactorWidth = 1f

  
  private var mScaleFactorHeight = 1f

  
  fun getRect(): RectF {
    mGetEdges.set(mEdges)
    return mGetEdges
  }

  
  fun getMinCropWidth() =
    mMinCropWindowWidth.coerceAtLeast(mMinCropResultWidth / mScaleFactorWidth)

  
  fun getMinCropHeight() =
    mMinCropWindowHeight.coerceAtLeast(mMinCropResultHeight / mScaleFactorHeight)

  
  fun getMaxCropWidth() =
    mMaxCropWindowWidth.coerceAtMost(mMaxCropResultWidth / mScaleFactorWidth)

  
  fun getMaxCropHeight() =
    mMaxCropWindowHeight.coerceAtMost(mMaxCropResultHeight / mScaleFactorHeight)

  
  fun getScaleFactorWidth() = mScaleFactorWidth

  
  fun getScaleFactorHeight() = mScaleFactorHeight

  
  fun setMinCropResultSize(minCropResultWidth: Int, minCropResultHeight: Int) {
    mMinCropResultWidth = minCropResultWidth.toFloat()
    mMinCropResultHeight = minCropResultHeight.toFloat()
  }

  
  fun setMaxCropResultSize(maxCropResultWidth: Int, maxCropResultHeight: Int) {
    mMaxCropResultWidth = maxCropResultWidth.toFloat()
    mMaxCropResultHeight = maxCropResultHeight.toFloat()
  }

  
  fun setCropWindowLimits(
    maxWidth: Float,
    maxHeight: Float,
    scaleFactorWidth: Float,
    scaleFactorHeight: Float,
  ) {
    mMaxCropWindowWidth = maxWidth
    mMaxCropWindowHeight = maxHeight
    mScaleFactorWidth = scaleFactorWidth
    mScaleFactorHeight = scaleFactorHeight
  }

  
  fun setInitialAttributeValues(options: CropImageOptions) {
    mMinCropWindowWidth = options.minCropWindowWidth.toFloat()
    mMinCropWindowHeight = options.minCropWindowHeight.toFloat()
    mMinCropResultWidth = options.minCropResultWidth.toFloat()
    mMinCropResultHeight = options.minCropResultHeight.toFloat()
    mMaxCropResultWidth = options.maxCropResultWidth.toFloat()
    mMaxCropResultHeight = options.maxCropResultHeight.toFloat()
  }

  
  fun setRect(rect: RectF) {
    mEdges.set(rect)
  }

  
  fun showGuidelines() = !(mEdges.width() < 100 || mEdges.height() < 100)

  
  fun getMoveHandler(
      x: Float,
      y: Float,
      targetRadius: Float,
      cropShape: CropImageView.CropShape,
      isCenterMoveEnabled: Boolean,
  ): CropWindowMoveHandler? {
    val type: CropWindowMoveHandler.Type? = when (cropShape) {
      RECTANGLE -> getRectanglePressedMoveType(x, y, targetRadius, isCenterMoveEnabled)
      OVAL -> getOvalPressedMoveType(x, y, isCenterMoveEnabled)
      RECTANGLE_VERTICAL_ONLY -> getRectangleVerticalOnlyPressedMoveType(x, y, targetRadius, isCenterMoveEnabled)
      RECTANGLE_HORIZONTAL_ONLY -> getRectangleHorizontalOnlyPressedMoveType(x, y, targetRadius, isCenterMoveEnabled)
    }

    return if (type != null) CropWindowMoveHandler(type, this, x, y) else null
  }

  
  private fun getRectanglePressedMoveType(
    x: Float,
    y: Float,
    targetRadius: Float,
    isCenterMoveEnabled: Boolean,
  ): CropWindowMoveHandler.Type? {
    return when {
      isInCornerTargetZone(x, y, mEdges.left, mEdges.top, targetRadius) -> {
          CropWindowMoveHandler.Type.TOP_LEFT
      }
      isInCornerTargetZone(x, y, mEdges.right, mEdges.top, targetRadius) -> {
          CropWindowMoveHandler.Type.TOP_RIGHT
      }
      isInCornerTargetZone(x, y, mEdges.left, mEdges.bottom, targetRadius) -> {
          CropWindowMoveHandler.Type.BOTTOM_LEFT
      }
      isInCornerTargetZone(x, y, mEdges.right, mEdges.bottom, targetRadius) -> {
          CropWindowMoveHandler.Type.BOTTOM_RIGHT
      }
      isCenterMoveEnabled &&
        isInCenterTargetZone(x, y, mEdges.left, mEdges.top, mEdges.right, mEdges.bottom) &&
        focusCenter() -> {
          CropWindowMoveHandler.Type.CENTER
      }
      isInHorizontalTargetZone(x, y, mEdges.left, mEdges.right, mEdges.top, targetRadius) -> {
          CropWindowMoveHandler.Type.TOP
      }
      isInHorizontalTargetZone(x, y, mEdges.left, mEdges.right, mEdges.bottom, targetRadius) -> {
          CropWindowMoveHandler.Type.BOTTOM
      }
      isInVerticalTargetZone(x, y, mEdges.left, mEdges.top, mEdges.bottom, targetRadius) -> {
          CropWindowMoveHandler.Type.LEFT
      }
      isInVerticalTargetZone(x, y, mEdges.right, mEdges.top, mEdges.bottom, targetRadius) -> {
          CropWindowMoveHandler.Type.RIGHT
      }
      isCenterMoveEnabled &&
        isInCenterTargetZone(x, y, mEdges.left, mEdges.top, mEdges.right, mEdges.bottom) &&
        !focusCenter() -> {
          CropWindowMoveHandler.Type.CENTER
      }
      else -> getOvalPressedMoveType(x, y, isCenterMoveEnabled)
    }
  }

  
  private fun getOvalPressedMoveType(
    x: Float,
    y: Float,
    isCenterMoveEnabled: Boolean,
  ): CropWindowMoveHandler.Type? {
        

    val cellLength = mEdges.width() / 6
    val leftCenter = mEdges.left + cellLength
    val rightCenter = mEdges.left + 5 * cellLength
    val cellHeight = mEdges.height() / 6
    val topCenter = mEdges.top + cellHeight
    val bottomCenter = mEdges.top + 5 * cellHeight
    return when {
      x < leftCenter -> {
        when {
          y < topCenter -> CropWindowMoveHandler.Type.TOP_LEFT
          y < bottomCenter -> CropWindowMoveHandler.Type.LEFT
          else -> CropWindowMoveHandler.Type.BOTTOM_LEFT
        }
      }
      x < rightCenter -> {
        when {
          y < topCenter -> CropWindowMoveHandler.Type.TOP
          y < bottomCenter -> if (isCenterMoveEnabled) {
              CropWindowMoveHandler.Type.CENTER
          } else {
            null
          }
          else -> CropWindowMoveHandler.Type.BOTTOM
        }
      }
      else -> {
        when {
          y < topCenter -> CropWindowMoveHandler.Type.TOP_RIGHT
          y < bottomCenter -> CropWindowMoveHandler.Type.RIGHT
          else -> CropWindowMoveHandler.Type.BOTTOM_RIGHT
        }
      }
    }
  }

  
  private fun getRectangleVerticalOnlyPressedMoveType(
    x: Float,
    y: Float,
    targetRadius: Float,
    isCenterMoveEnabled: Boolean,
  ): CropWindowMoveHandler.Type? {
    return when {
      distance(x, y, mEdges.centerX(), mEdges.top) <= targetRadius -> {
          CropWindowMoveHandler.Type.TOP
      }
      distance(x, y, mEdges.centerX(), mEdges.bottom) <= targetRadius -> {
          CropWindowMoveHandler.Type.BOTTOM
      }
      isCenterMoveEnabled &&
        isInCenterTargetZone(x, y, mEdges.left, mEdges.top, mEdges.right, mEdges.bottom) -> {
          CropWindowMoveHandler.Type.CENTER
      }
      else -> getOvalPressedMoveType(x, y, isCenterMoveEnabled)
    }
  }

  
  private fun getRectangleHorizontalOnlyPressedMoveType(
    x: Float,
    y: Float,
    targetRadius: Float,
    isCenterMoveEnabled: Boolean,
  ): CropWindowMoveHandler.Type? {
    return when {
      distance(x, y, mEdges.left, mEdges.centerY()) <= targetRadius -> {
          CropWindowMoveHandler.Type.LEFT
      }
      distance(x, y, mEdges.right, mEdges.centerY()) <= targetRadius -> {
          CropWindowMoveHandler.Type.RIGHT
      }
      isCenterMoveEnabled &&
        isInCenterTargetZone(x, y, mEdges.left, mEdges.top, mEdges.right, mEdges.bottom) -> {
          CropWindowMoveHandler.Type.CENTER
      }
      else -> getOvalPressedMoveType(x, y, isCenterMoveEnabled)
    }
  }

  
  private fun isInCornerTargetZone(
    x: Float,
    y: Float,
    handleX: Float,
    handleY: Float,
    targetRadius: Float,
  ) = distance(x, y, handleX, handleY) <= targetRadius

  
  private fun distance(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
  ) = max(abs(x1 - x2), abs(y1 - y2))

  
  private fun isInHorizontalTargetZone(
    x: Float,
    y: Float,
    handleXStart: Float,
    handleXEnd: Float,
    handleY: Float,
    targetRadius: Float,
  ) = x > handleXStart && x < handleXEnd && abs(y - handleY) <= targetRadius

  
  private fun isInVerticalTargetZone(
    x: Float,
    y: Float,
    handleX: Float,
    handleYStart: Float,
    handleYEnd: Float,
    targetRadius: Float,
  ) = abs(x - handleX) <= targetRadius && y > handleYStart && y < handleYEnd

  
  private fun isInCenterTargetZone(
    x: Float,
    y: Float,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
  ) = x > left && x < right && y > top && y < bottom

  
  private fun focusCenter() = !showGuidelines()
}