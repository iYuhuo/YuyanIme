package com.yuyan.imemodule.libs.cropper

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

internal class CropWindowMoveHandler(
    
  private val type: Type,
    
  cropWindowHandler: CropWindowHandler,
    
  touchX: Float,
    
  touchY: Float,
) {

  
  internal enum class Type {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    LEFT,
    TOP,
    RIGHT,
    BOTTOM,
    CENTER,
  }

  internal companion object {
    
    internal fun calculateAspectRatio(left: Float, top: Float, right: Float, bottom: Float) =
      (right - left) / (bottom - top)
  }

  
  private val mMinCropWidth: Float = cropWindowHandler.getMinCropWidth()

  
  private val mMinCropHeight: Float = cropWindowHandler.getMinCropHeight()

  
  private val mMaxCropWidth: Float = cropWindowHandler.getMaxCropWidth()

  
  private val mMaxCropHeight: Float = cropWindowHandler.getMaxCropHeight()

  
  private val mTouchOffset = PointF(0f, 0f)

  init {
    calculateTouchOffset(cropWindowHandler.getRect(), touchX, touchY)
  }

  
  fun move(
    rect: RectF,
    x: Float,
    y: Float,
    bounds: RectF,
    viewWidth: Int,
    viewHeight: Int,
    snapMargin: Float,
    fixedAspectRatio: Boolean,
    aspectRatio: Float,
  ) {
    val adjX = x + mTouchOffset.x
    val adjY = y + mTouchOffset.y
    if (type == Type.CENTER) {
      moveCenter(
        rect = rect,
        x = adjX,
        y = adjY,
        bounds = bounds,
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        snapRadius = snapMargin,
      )
    } else {
      if (fixedAspectRatio) {
        moveSizeWithFixedAspectRatio(
          rect = rect,
          x = adjX,
          y = adjY,
          bounds = bounds,
          viewWidth = viewWidth,
          viewHeight = viewHeight,
          snapMargin = snapMargin,
          aspectRatio = aspectRatio,
        )
      } else {
        moveSizeWithFreeAspectRatio(
          rect = rect,
          x = adjX,
          y = adjY,
          bounds = bounds,
          viewWidth = viewWidth,
          viewHeight = viewHeight,
          snapMargin = snapMargin,
        )
      }
    }
  }

  
  private fun calculateTouchOffset(rect: RectF, touchX: Float, touchY: Float) {
    var touchOffsetX = 0f
    var touchOffsetY = 0f
    when (type) {
      Type.TOP_LEFT -> {
        touchOffsetX = rect.left - touchX
        touchOffsetY = rect.top - touchY
      }
      Type.TOP_RIGHT -> {
        touchOffsetX = rect.right - touchX
        touchOffsetY = rect.top - touchY
      }
      Type.BOTTOM_LEFT -> {
        touchOffsetX = rect.left - touchX
        touchOffsetY = rect.bottom - touchY
      }
      Type.BOTTOM_RIGHT -> {
        touchOffsetX = rect.right - touchX
        touchOffsetY = rect.bottom - touchY
      }
      Type.LEFT -> {
        touchOffsetX = rect.left - touchX
        touchOffsetY = 0f
      }
      Type.TOP -> {
        touchOffsetX = 0f
        touchOffsetY = rect.top - touchY
      }
      Type.RIGHT -> {
        touchOffsetX = rect.right - touchX
        touchOffsetY = 0f
      }
      Type.BOTTOM -> {
        touchOffsetX = 0f
        touchOffsetY = rect.bottom - touchY
      }
      Type.CENTER -> {
        touchOffsetX = rect.centerX() - touchX
        touchOffsetY = rect.centerY() - touchY
      }
    }
    mTouchOffset.x = touchOffsetX
    mTouchOffset.y = touchOffsetY
  }

  
  private fun moveCenter(
    rect: RectF,
    x: Float,
    y: Float,
    bounds: RectF,
    viewWidth: Int,
    viewHeight: Int,
    snapRadius: Float,
  ) {
    var dx = x - rect.centerX()
    var dy = y - rect.centerY()
    if (rect.left + dx < 0 || rect.right + dx > viewWidth || rect.left + dx < bounds.left || rect.right + dx > bounds.right) {
      dx /= 1.05f
      mTouchOffset.x -= dx / 2
    }

    if (rect.top + dy < 0 || rect.bottom + dy > viewHeight || rect.top + dy < bounds.top || rect.bottom + dy > bounds.bottom) {
      dy /= 1.05f
      mTouchOffset.y -= dy / 2
    }
    rect.offset(dx, dy)
    snapEdgesToBounds(edges = rect, bounds = bounds, margin = snapRadius)
  }

  
  private fun moveSizeWithFreeAspectRatio(
    rect: RectF,
    x: Float,
    y: Float,
    bounds: RectF,
    viewWidth: Int,
    viewHeight: Int,
    snapMargin: Float,
  ) {
    when (type) {
      Type.TOP_LEFT -> {
        adjustTop(
          rect = rect,
          top = y,
          bounds = bounds,
          snapMargin = snapMargin,
          aspectRatio = 0f,
          leftMoves = false,
          rightMoves = false,
        )
        adjustLeft(
          rect = rect,
          left = x,
          bounds = bounds,
          snapMargin = snapMargin,
          aspectRatio = 0f,
          topMoves = false,
          bottomMoves = false,
        )
      }
      Type.TOP_RIGHT -> {
        adjustTop(
          rect = rect,
          top = y,
          bounds = bounds,
          snapMargin = snapMargin,
          aspectRatio = 0f,
          leftMoves = false,
          rightMoves = false,
        )
        adjustRight(
          rect = rect,
          right = x,
          bounds = bounds,
          viewWidth = viewWidth,
          snapMargin = snapMargin,
          aspectRatio = 0f,
          topMoves = false,
          bottomMoves = false,
        )
      }
      Type.BOTTOM_LEFT -> {
        adjustBottom(
          rect = rect,
          bottom = y,
          bounds = bounds,
          viewHeight = viewHeight,
          snapMargin = snapMargin,
          aspectRatio = 0f,
          leftMoves = false,
          rightMoves = false,
        )
        adjustLeft(
          rect = rect,
          left = x,
          bounds = bounds,
          snapMargin = snapMargin,
          aspectRatio = 0f,
          topMoves = false,
          bottomMoves = false,
        )
      }
      Type.BOTTOM_RIGHT -> {
        adjustBottom(
          rect = rect,
          bottom = y,
          bounds = bounds,
          viewHeight = viewHeight,
          snapMargin = snapMargin,
          aspectRatio = 0f,
          leftMoves = false,
          rightMoves = false,
        )
        adjustRight(
          rect = rect,
          right = x,
          bounds = bounds,
          viewWidth = viewWidth,
          snapMargin = snapMargin,
          aspectRatio = 0f,
          topMoves = false,
          bottomMoves = false,
        )
      }
      Type.LEFT -> adjustLeft(
        rect = rect,
        left = x,
        bounds = bounds,
        snapMargin = snapMargin,
        aspectRatio = 0f,
        topMoves = false,
        bottomMoves = false,
      )
      Type.TOP -> adjustTop(
        rect = rect,
        top = y,
        bounds = bounds,
        snapMargin = snapMargin,
        aspectRatio = 0f,
        leftMoves = false,
        rightMoves = false,
      )
      Type.RIGHT -> adjustRight(
        rect = rect,
        right = x,
        bounds = bounds,
        viewWidth = viewWidth,
        snapMargin = snapMargin,
        aspectRatio = 0f,
        topMoves = false,
        bottomMoves = false,
      )
      Type.BOTTOM -> adjustBottom(
        rect = rect,
        bottom = y,
        bounds = bounds,
        viewHeight = viewHeight,
        snapMargin = snapMargin,
        aspectRatio = 0f,
        leftMoves = false,
        rightMoves = false,
      )
      Type.CENTER -> {
      }
    }
  }

  
  private fun moveSizeWithFixedAspectRatio(
    rect: RectF,
    x: Float,
    y: Float,
    bounds: RectF,
    viewWidth: Int,
    viewHeight: Int,
    snapMargin: Float,
    aspectRatio: Float,
  ) {
    when (type) {
      Type.TOP_LEFT ->
        if (calculateAspectRatio(x, y, rect.right, rect.bottom) < aspectRatio) {
          adjustTop(
            rect = rect,
            top = y,
            bounds = bounds,
            snapMargin = snapMargin,
            aspectRatio = aspectRatio,
            leftMoves = true,
            rightMoves = false,
          )
          adjustLeftByAspectRatio(rect = rect, aspectRatio = aspectRatio)
        } else {
          adjustLeft(
            rect = rect,
            left = x,
            bounds = bounds,
            snapMargin = snapMargin,
            aspectRatio = aspectRatio,
            topMoves = true,
            bottomMoves = false,
          )
          adjustTopByAspectRatio(rect = rect, aspectRatio = aspectRatio)
        }
      Type.TOP_RIGHT ->
        if (calculateAspectRatio(
            left = rect.left,
            top = y,
            right = x,
            bottom = rect.bottom,
          ) < aspectRatio
        ) {
          adjustTop(
            rect = rect,
            top = y,
            bounds = bounds,
            snapMargin = snapMargin,
            aspectRatio = aspectRatio,
            leftMoves = false,
            rightMoves = true,
          )
          adjustRightByAspectRatio(rect = rect, aspectRatio = aspectRatio)
        } else {
          adjustRight(
            rect = rect,
            right = x,
            bounds = bounds,
            viewWidth = viewWidth,
            snapMargin = snapMargin,
            aspectRatio = aspectRatio,
            topMoves = true,
            bottomMoves = false,
          )
          adjustTopByAspectRatio(rect = rect, aspectRatio = aspectRatio)
        }
      Type.BOTTOM_LEFT ->
        if (calculateAspectRatio(
            left = x,
            top = rect.top,
            right = rect.right,
            bottom = y,
          ) < aspectRatio
        ) {
          adjustBottom(
            rect = rect,
            bottom = y,
            bounds = bounds,
            viewHeight = viewHeight,
            snapMargin = snapMargin,
            aspectRatio = aspectRatio,
            leftMoves = true,
            rightMoves = false,
          )
          adjustLeftByAspectRatio(rect = rect, aspectRatio = aspectRatio)
        } else {
          adjustLeft(
            rect = rect,
            left = x,
            bounds = bounds,
            snapMargin = snapMargin,
            aspectRatio = aspectRatio,
            topMoves = false,
            bottomMoves = true,
          )
          adjustBottomByAspectRatio(rect = rect, aspectRatio = aspectRatio)
        }
      Type.BOTTOM_RIGHT ->
        if (calculateAspectRatio(
            left = rect.left,
            top = rect.top,
            right = x,
            bottom = y,
          ) < aspectRatio
        ) {
          adjustBottom(
            rect = rect,
            bottom = y,
            bounds = bounds,
            viewHeight = viewHeight,
            snapMargin = snapMargin,
            aspectRatio = aspectRatio,
            leftMoves = false,
            rightMoves = true,
          )
          adjustRightByAspectRatio(rect = rect, aspectRatio = aspectRatio)
        } else {
          adjustRight(
            rect = rect,
            right = x,
            bounds = bounds,
            viewWidth = viewWidth,
            snapMargin = snapMargin,
            aspectRatio = aspectRatio,
            topMoves = false,
            bottomMoves = true,
          )
          adjustBottomByAspectRatio(rect = rect, aspectRatio = aspectRatio)
        }
      Type.LEFT -> {
        adjustLeft(
          rect = rect,
          left = x,
          bounds = bounds,
          snapMargin = snapMargin,
          aspectRatio = aspectRatio,
          topMoves = true,
          bottomMoves = true,
        )
        adjustTopBottomByAspectRatio(
          rect = rect,
          bounds = bounds,
          aspectRatio = aspectRatio,
        )
      }
      Type.TOP -> {
        adjustTop(
          rect = rect,
          top = y,
          bounds = bounds,
          snapMargin = snapMargin,
          aspectRatio = aspectRatio,
          leftMoves = true,
          rightMoves = true,
        )
        adjustLeftRightByAspectRatio(
          rect = rect,
          bounds = bounds,
          aspectRatio = aspectRatio,
        )
      }
      Type.RIGHT -> {
        adjustRight(
          rect = rect,
          right = x,
          bounds = bounds,
          viewWidth = viewWidth,
          snapMargin = snapMargin,
          aspectRatio = aspectRatio,
          topMoves = true,
          bottomMoves = true,
        )
        adjustTopBottomByAspectRatio(
          rect = rect,
          bounds = bounds,
          aspectRatio = aspectRatio,
        )
      }
      Type.BOTTOM -> {
        adjustBottom(
          rect = rect,
          bottom = y,
          bounds = bounds,
          viewHeight = viewHeight,
          snapMargin = snapMargin,
          aspectRatio = aspectRatio,
          leftMoves = true,
          rightMoves = true,
        )
        adjustLeftRightByAspectRatio(
          rect = rect,
          bounds = bounds,
          aspectRatio = aspectRatio,
        )
      }
      Type.CENTER -> {
      }
    }
  }

  
  private fun snapEdgesToBounds(edges: RectF, bounds: RectF, margin: Float) {
    if (edges.left < bounds.left + margin) {
      edges.offset(bounds.left - edges.left, 0f)
    }

    if (edges.top < bounds.top + margin) {
      edges.offset(0f, bounds.top - edges.top)
    }

    if (edges.right > bounds.right - margin) {
      edges.offset(bounds.right - edges.right, 0f)
    }

    if (edges.bottom > bounds.bottom - margin) {
      edges.offset(0f, bounds.bottom - edges.bottom)
    }
  }

  
  private fun adjustLeft(
    rect: RectF,
    left: Float,
    bounds: RectF,
    snapMargin: Float,
    aspectRatio: Float,
    topMoves: Boolean,
    bottomMoves: Boolean,
  ) {
    var newLeft = left
    if (newLeft < 0) {
      newLeft /= 1.05f
      mTouchOffset.x -= newLeft / 1.1f
    }

    if (newLeft < bounds.left) mTouchOffset.x -= (newLeft - bounds.left) / 2f

    if (newLeft - bounds.left < snapMargin) newLeft = bounds.left
    if (rect.right - newLeft < mMinCropWidth) newLeft = rect.right - mMinCropWidth
    if (rect.right - newLeft > mMaxCropWidth) newLeft = rect.right - mMaxCropWidth

    if (newLeft - bounds.left < snapMargin) newLeft = bounds.left
    if (aspectRatio > 0) {
      var newHeight = (rect.right - newLeft) / aspectRatio
      if (newHeight < mMinCropHeight) {
        newLeft = max(bounds.left, rect.right - mMinCropHeight * aspectRatio)
        newHeight = (rect.right - newLeft) / aspectRatio
      }
      if (newHeight > mMaxCropHeight) {
        newLeft = max(bounds.left, rect.right - mMaxCropHeight * aspectRatio)
        newHeight = (rect.right - newLeft) / aspectRatio
      }
      if (topMoves && bottomMoves) {
        newLeft = max(
          newLeft,
          max(bounds.left, rect.right - bounds.height() * aspectRatio),
        )
      } else {
        if (topMoves && rect.bottom - newHeight < bounds.top) {
          newLeft =
            max(bounds.left, rect.right - (rect.bottom - bounds.top) * aspectRatio)
          newHeight = (rect.right - newLeft) / aspectRatio
        }
        if (bottomMoves && rect.top + newHeight > bounds.bottom) {
          newLeft = max(
            newLeft,
            max(bounds.left, rect.right - (bounds.bottom - rect.top) * aspectRatio),
          )
        }
      }
    }
    rect.left = newLeft
  }

  
  private fun adjustRight(
    rect: RectF,
    right: Float,
    bounds: RectF,
    viewWidth: Int,
    snapMargin: Float,
    aspectRatio: Float,
    topMoves: Boolean,
    bottomMoves: Boolean,
  ) {
    var newRight = right
    if (newRight > viewWidth) {
      newRight = viewWidth + (newRight - viewWidth) / 1.05f
      mTouchOffset.x -= (newRight - viewWidth) / 1.1f
    }

    if (newRight > bounds.right) mTouchOffset.x -= (newRight - bounds.right) / 2f
    if (bounds.right - newRight < snapMargin) newRight = bounds.right
    if (newRight - rect.left < mMinCropWidth) newRight = rect.left + mMinCropWidth
    if (newRight - rect.left > mMaxCropWidth) newRight = rect.left + mMaxCropWidth
    if (bounds.right - newRight < snapMargin) newRight = bounds.right
    if (aspectRatio > 0) {
      var newHeight = (newRight - rect.left) / aspectRatio
      if (newHeight < mMinCropHeight) {
        newRight = min(bounds.right, rect.left + mMinCropHeight * aspectRatio)
        newHeight = (newRight - rect.left) / aspectRatio
      }
      if (newHeight > mMaxCropHeight) {
        newRight = min(bounds.right, rect.left + mMaxCropHeight * aspectRatio)
        newHeight = (newRight - rect.left) / aspectRatio
      }
      if (topMoves && bottomMoves) {
        newRight =
          min(newRight, min(bounds.right, rect.left + bounds.height() * aspectRatio))
      } else {
        if (topMoves && rect.bottom - newHeight < bounds.top) {
          newRight =
            min(bounds.right, rect.left + (rect.bottom - bounds.top) * aspectRatio)
          newHeight = (newRight - rect.left) / aspectRatio
        }
        if (bottomMoves && rect.top + newHeight > bounds.bottom) {
          newRight = min(
            newRight,
            min(bounds.right, rect.left + (bounds.bottom - rect.top) * aspectRatio),
          )
        }
      }
    }
    rect.right = newRight
  }

  
  private fun adjustTop(
    rect: RectF,
    top: Float,
    bounds: RectF,
    snapMargin: Float,
    aspectRatio: Float,
    leftMoves: Boolean,
    rightMoves: Boolean,
  ) {
    var newTop = top
    if (newTop < 0) {
      newTop /= 1.05f
      mTouchOffset.y -= newTop / 1.1f
    }

    if (newTop < bounds.top) mTouchOffset.y -= (newTop - bounds.top) / 2f

    if (newTop - bounds.top < snapMargin) newTop = bounds.top
    if (rect.bottom - newTop < mMinCropHeight) newTop = rect.bottom - mMinCropHeight
    if (rect.bottom - newTop > mMaxCropHeight) newTop = rect.bottom - mMaxCropHeight

    if (newTop - bounds.top < snapMargin) newTop = bounds.top
    if (aspectRatio > 0) {
      var newWidth = (rect.bottom - newTop) * aspectRatio
      if (newWidth < mMinCropWidth) {
        newTop = max(bounds.top, rect.bottom - mMinCropWidth / aspectRatio)
        newWidth = (rect.bottom - newTop) * aspectRatio
      }
      if (newWidth > mMaxCropWidth) {
        newTop = max(bounds.top, rect.bottom - mMaxCropWidth / aspectRatio)
        newWidth = (rect.bottom - newTop) * aspectRatio
      }
      if (leftMoves && rightMoves) {
        newTop = max(newTop, max(bounds.top, rect.bottom - bounds.width() / aspectRatio))
      } else {
        if (leftMoves && rect.right - newWidth < bounds.left) {
          newTop = max(bounds.top, rect.bottom - (rect.right - bounds.left) / aspectRatio)
          newWidth = (rect.bottom - newTop) * aspectRatio
        }
        if (rightMoves && rect.left + newWidth > bounds.right) {
          newTop = max(
            newTop,
            max(bounds.top, rect.bottom - (bounds.right - rect.left) / aspectRatio),
          )
        }
      }
    }
    rect.top = newTop
  }

  
  private fun adjustBottom(
    rect: RectF,
    bottom: Float,
    bounds: RectF,
    viewHeight: Int,
    snapMargin: Float,
    aspectRatio: Float,
    leftMoves: Boolean,
    rightMoves: Boolean,
  ) {
    var newBottom = bottom
    if (newBottom > viewHeight) {
      newBottom = viewHeight + (newBottom - viewHeight) / 1.05f
      mTouchOffset.y -= (newBottom - viewHeight) / 1.1f
    }

    if (newBottom > bounds.bottom) mTouchOffset.y -= (newBottom - bounds.bottom) / 2f

    if (bounds.bottom - newBottom < snapMargin) newBottom = bounds.bottom
    if (newBottom - rect.top < mMinCropHeight) newBottom = rect.top + mMinCropHeight
    if (newBottom - rect.top > mMaxCropHeight) newBottom = rect.top + mMaxCropHeight
    if (bounds.bottom - newBottom < snapMargin) newBottom = bounds.bottom
    if (aspectRatio > 0) {
      var newWidth = (newBottom - rect.top) * aspectRatio
      if (newWidth < mMinCropWidth) {
        newBottom = min(bounds.bottom, rect.top + mMinCropWidth / aspectRatio)
        newWidth = (newBottom - rect.top) * aspectRatio
      }
      if (newWidth > mMaxCropWidth) {
        newBottom = min(bounds.bottom, rect.top + mMaxCropWidth / aspectRatio)
        newWidth = (newBottom - rect.top) * aspectRatio
      }
      if (leftMoves && rightMoves) {
        newBottom =
          min(newBottom, min(bounds.bottom, rect.top + bounds.width() / aspectRatio))
      } else {
        if (leftMoves && rect.right - newWidth < bounds.left) {
          newBottom =
            min(bounds.bottom, rect.top + (rect.right - bounds.left) / aspectRatio)
          newWidth = (newBottom - rect.top) * aspectRatio
        }
        if (rightMoves && rect.left + newWidth > bounds.right) {
          newBottom = min(
            newBottom,
            min(bounds.bottom, rect.top + (bounds.right - rect.left) / aspectRatio),
          )
        }
      }
    }
    rect.bottom = newBottom
  }

  
  private fun adjustLeftByAspectRatio(rect: RectF, aspectRatio: Float) {
    rect.left = rect.right - rect.height() * aspectRatio
  }

  
  private fun adjustTopByAspectRatio(rect: RectF, aspectRatio: Float) {
    rect.top = rect.bottom - rect.width() / aspectRatio
  }

  
  private fun adjustRightByAspectRatio(rect: RectF, aspectRatio: Float) {
    rect.right = rect.left + rect.height() * aspectRatio
  }

  
  private fun adjustBottomByAspectRatio(rect: RectF, aspectRatio: Float) {
    rect.bottom = rect.top + rect.width() / aspectRatio
  }

  
  private fun adjustLeftRightByAspectRatio(rect: RectF, bounds: RectF, aspectRatio: Float) {
    rect.inset((rect.width() - rect.height() * aspectRatio) / 2, 0f)
    if (rect.left < bounds.left) {
      rect.offset(bounds.left - rect.left, 0f)
    }

    if (rect.right > bounds.right) {
      rect.offset(bounds.right - rect.right, 0f)
    }
  }

  
  private fun adjustTopBottomByAspectRatio(rect: RectF, bounds: RectF, aspectRatio: Float) {
    rect.inset(0f, (rect.height() - rect.width() / aspectRatio) / 2)
    if (rect.top < bounds.top) {
      rect.offset(0f, bounds.top - rect.top)
    }

    if (rect.bottom > bounds.bottom) {
      rect.offset(0f, bounds.bottom - rect.bottom)
    }
  }
}