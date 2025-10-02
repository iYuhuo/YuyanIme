package com.yuyan.imemodule.view.widget.seekbar;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Html;
import android.text.Layout;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import androidx.annotation.IntDef;
import com.yuyan.imemodule.utils.DevicesUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;

public class SignSeekBar extends View {
    static final int NONE = -1;

    @IntDef({NONE, TextPosition.SIDES, TextPosition.BOTTOM_SIDES, TextPosition.BELOW_SECTION_MARK})
    @Retention(RetentionPolicy.SOURCE)
    public @interface TextPosition {
        int SIDES = 0, BOTTOM_SIDES = 1, BELOW_SECTION_MARK = 2;
    }

    private float mMin;
    private float mMax;
    private float mProgress;
    private boolean isFloatType;
    private int mTrackSize;
    private int mSecondTrackSize;
    private int mThumbRadius;
    private int mThumbRadiusOnDragging;
    private int mTrackColor;
    private int mSecondTrackColor;
    private int mThumbColor;
    private int mSectionCount;
    private boolean isShowSectionMark;
    private boolean isAutoAdjustSectionMark;
    private boolean isShowSectionText;
    private int mSectionTextSize;
    private int mSectionTextColor;
    @TextPosition
    private int mSectionTextPosition = NONE;
    private int mSectionTextInterval;
    private boolean isShowThumbText;
    private int mThumbTextSize;
    private int mThumbTextColor;
    private boolean isShowProgressInFloat;
    private boolean isTouchToSeek;
    private boolean isSeekBySection;
    private long mAnimDuration;

    private int mSignBorderSize;
    private boolean isShowSignBorder;
    private int mSignBorderColor;// color of border color
    private int mSignColor;// color of sign
    private int mSignTextSize;
    private int mSignTextColor;
    private int mSignHeight;
    private int mSignWidth;

    private float mDelta;
    private float mSectionValue;
    private float mThumbCenterX;
    private float mTrackLength;
    private float mSectionOffset;
    private boolean isThumbOnDragging;
    private boolean triggerSeekBySection;

    private OnProgressChangedListener mProgressListener;
    private float mLeft;
    private float mRight;
    private final Paint mPaint;
    private final Rect mRectText;

    private boolean isTouchToSeekAnimEnd = true;
    private float mPreSecValue;
    private SignConfigBuilder mConfigBuilder;
    private String[] mSidesLabels;
    private boolean isSidesLabels;
    private float mThumbBgAlpha;
    private float mThumbRatio;
    private boolean isShowThumbShadow;
    private boolean isShowSign;
    private boolean isSignArrowAutofloat;

    private final Rect valueSignBounds;
    private final RectF roundRectangleBounds;
    private int mSignArrowHeight;
    private int mSignArrowWidth;
    private int mSignRound;
    private final Point point1;
    private final Point point2;
    private final Point point3;
    private Paint signPaint;
    private Paint signborderPaint;
    private StaticLayout valueTextLayout;
    private final Path trianglePath;
    private final Path triangleboderPath;
    private String unit;
    private boolean mReverse;
    private TextPaint valueTextPaint;
    private NumberFormat mFormat;

    public SignSeekBar(Context context) {
        super(context);
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setTextAlign(Paint.Align.CENTER);

        mRectText = new Rect();
        isSidesLabels = mSidesLabels != null && mSidesLabels.length > 0;

        roundRectangleBounds = new RectF();
        valueSignBounds = new Rect();

        point1 = new Point();
        point2 = new Point();
        point3 = new Point();

        trianglePath = new Path();
        trianglePath.setFillType(Path.FillType.EVEN_ODD);

        triangleboderPath = new Path();

        init();

        initConfigByPriority();
    }

    private void init() {
        signPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        signPaint.setStyle(Paint.Style.FILL);
        signPaint.setAntiAlias(true);
        signPaint.setColor(mSignColor);

        signborderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        signborderPaint.setStyle(Paint.Style.STROKE);
        signborderPaint.setStrokeWidth(mSignBorderSize);
        signborderPaint.setColor(mSignBorderColor);
        signborderPaint.setAntiAlias(true);

        valueTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        valueTextPaint.setStyle(Paint.Style.FILL);
        valueTextPaint.setTextSize(mSignTextSize);
        valueTextPaint.setColor(mSignTextColor);
    }

    private void initConfigByPriority() {
        if (mMin == mMax) {
            mMin = 0.0f;
            mMax = 100.0f;
        }
        if (mMin > mMax) {
            float tmp = mMax;
            mMax = mMin;
            mMin = tmp;
        }
        if (mProgress < mMin) {
            mProgress = mMin;
        }
        if (mProgress > mMax) {
            mProgress = mMax;
        }
        if (mSecondTrackSize < mTrackSize) {
            mSecondTrackSize = mTrackSize + DevicesUtils.dip2px(2);
        }
        if (mThumbRadius <= mSecondTrackSize) {
            mThumbRadius = mSecondTrackSize + DevicesUtils.dip2px(2);
        }
        if (mThumbRadiusOnDragging <= mSecondTrackSize) {
            mThumbRadiusOnDragging = mSecondTrackSize * 2;
        }
        if (mSectionCount <= 0) {
            mSectionCount = 10;
        }
        mDelta = mMax - mMin;
        mSectionValue = mDelta / mSectionCount;

        if (mSectionValue < 1) {
            isFloatType = true;
        }
        if (isFloatType) {
            isShowProgressInFloat = true;
        }
        if (mSectionTextPosition != NONE) {
            isShowSectionText = true;
        }
        if (isShowSectionText) {
            if (mSectionTextPosition == NONE) {
                mSectionTextPosition = TextPosition.SIDES;
            }
            if (mSectionTextPosition == TextPosition.BELOW_SECTION_MARK) {
                isShowSectionMark = true;
            }
        }
        if (mSectionTextInterval < 1) {
            mSectionTextInterval = 1;
        }
        if (isAutoAdjustSectionMark && !isShowSectionMark) {
            isAutoAdjustSectionMark = false;
        }
        if (isSeekBySection) {
            mPreSecValue = mMin;
            if (mProgress != mMin) {
                mPreSecValue = mSectionValue;
            }
            isShowSectionMark = true;
            isAutoAdjustSectionMark = true;
            isTouchToSeek = false;
        }

        setProgress(mProgress);

        mThumbTextSize = isFloatType || isSeekBySection || (isShowSectionText && mSectionTextPosition ==
                TextPosition.BELOW_SECTION_MARK) ? mSectionTextSize : mThumbTextSize;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int height = mThumbRadiusOnDragging * 2;
        if (isShowThumbText) {
            mPaint.setTextSize(mThumbTextSize);
            mPaint.getTextBounds("j", 0, 1, mRectText);
            height += mRectText.height();
        }
        if (isShowSectionText && mSectionTextPosition >= TextPosition.BOTTOM_SIDES) {
            String measuretext = isSidesLabels ? mSidesLabels[0] : "j";
            mPaint.setTextSize(mSectionTextSize);
            mPaint.getTextBounds(measuretext, 0, measuretext.length(), mRectText);
            height = Math.max(height, mThumbRadiusOnDragging * 2 + mRectText.height());
        }
        height += mSignHeight;//加上提示框的高度
        if (isShowSignBorder) height += mSignBorderSize;//加上提示框边框高度
        setMeasuredDimension(resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec), height);

        mLeft = getPaddingLeft() + mThumbRadiusOnDragging;
        mRight = getMeasuredWidth() - getPaddingRight() - mThumbRadiusOnDragging;

        if (isShowSectionText) {
            mPaint.setTextSize(mSectionTextSize);

            if (mSectionTextPosition == TextPosition.SIDES) {
                String text = getMinText();
                mPaint.getTextBounds(text, 0, text.length(), mRectText);
                mLeft += (mRectText.width());

                text = getMaxText();
                mPaint.getTextBounds(text, 0, text.length(), mRectText);
                mRight -= (mRectText.width());
            } else if (mSectionTextPosition >= TextPosition.BOTTOM_SIDES) {
                String text = isSidesLabels ? mSidesLabels[0] : getMinText();
                mPaint.getTextBounds(text, 0, text.length(), mRectText);
                float max = Math.max(mThumbRadiusOnDragging, mRectText.width() / 2f);
                mLeft = getPaddingLeft() + max;

                text = isSidesLabels ? mSidesLabels[mSidesLabels.length - 1] : getMaxText();
                mPaint.getTextBounds(text, 0, text.length(), mRectText);
                max = Math.max(mThumbRadiusOnDragging, mRectText.width() / 2f);
                mRight = getMeasuredWidth() - getPaddingRight() - max;
            }
        } else if (isShowThumbText && mSectionTextPosition == NONE) {
            mPaint.setTextSize(mThumbTextSize);

            String text = getMinText();
            mPaint.getTextBounds(text, 0, text.length(), mRectText);
            float max = Math.max(mThumbRadiusOnDragging, mRectText.width() / 2f);
            mLeft = getPaddingLeft() + max;

            text = getMaxText();
            mPaint.getTextBounds(text, 0, text.length(), mRectText);
            max = Math.max(mThumbRadiusOnDragging, mRectText.width() / 2f);
            mRight = getMeasuredWidth() - getPaddingRight() - max;
        }

        if (isShowSign && !isSignArrowAutofloat) {//提示框 三角指示是否自动移动
            mLeft = Math.max(mLeft, getPaddingLeft() + mSignWidth / 2f + mSignBorderSize);
            mRight = Math.min(mRight, getMeasuredWidth() - getPaddingRight() - mSignWidth / 2f - mSignBorderSize);
        }

        mTrackLength = mRight - mLeft;
        mSectionOffset = mTrackLength / mSectionCount;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float xLeft = getPaddingLeft();
        float xRight = getMeasuredWidth() - getPaddingRight();
        float yTop = getPaddingTop() + mThumbRadiusOnDragging;
        if (isShowSign) {//加上提示框高度
            yTop += mSignHeight;
        }
        if (isShowSignBorder) {//加上提示框边框高度
            yTop += mSignBorderSize;
        }
        if (isShowSign && !isSignArrowAutofloat) {//是否浮动显示提示框三角指示，默认浮动，否则居中显示
            xLeft += (mSignWidth / 2f + mSignBorderSize);
            xRight -= (mSignWidth / 2f + mSignBorderSize);
        }
        if (isShowSectionText) {
            mPaint.setTextSize(mSectionTextSize);
            if(isEnabled())mPaint.setColor(mSectionTextColor);

            if (mSectionTextPosition == TextPosition.SIDES) {
                float y_ = yTop + mRectText.height() / 2f;

                String text = isSidesLabels ? mSidesLabels[0] : getMinText();
                mPaint.getTextBounds(text, 0, text.length(), mRectText);
                canvas.drawText(text, xLeft + mRectText.width() / 2f, y_, mPaint);
                xLeft += mRectText.width();

                text = isSidesLabels && mSidesLabels.length > 1 ? mSidesLabels[mSidesLabels.length - 1] : getMaxText();
                mPaint.getTextBounds(text, 0, text.length(), mRectText);
                canvas.drawText(text, xRight - mRectText.width() / 2f, y_, mPaint);
                xRight -= (mRectText.width());

            } else if (mSectionTextPosition >= TextPosition.BOTTOM_SIDES) {
                float y_ = yTop + mThumbRadiusOnDragging;

                String text = isSidesLabels ? mSidesLabels[0] : getMinText();
                mPaint.getTextBounds(text, 0, text.length(), mRectText);
                y_ += mRectText.height();
                xLeft = mLeft;
                if (mSectionTextPosition == TextPosition.BOTTOM_SIDES) {
                    canvas.drawText(text, xLeft, y_, mPaint);
                }

                text = isSidesLabels && mSidesLabels.length > 1 ? mSidesLabels[mSidesLabels.length - 1] : getMaxText();
                mPaint.getTextBounds(text, 0, text.length(), mRectText);
                xRight = mRight;
                if (mSectionTextPosition == TextPosition.BOTTOM_SIDES) {
                    canvas.drawText(text, xRight, y_, mPaint);
                }
            }
        } else if (isShowThumbText && mSectionTextPosition == NONE) {
            xLeft = mLeft;
            xRight = mRight;
        }

        if ((!isShowSectionText && !isShowThumbText) || mSectionTextPosition == TextPosition.SIDES) {
            xLeft += mThumbRadiusOnDragging;
            xRight -= mThumbRadiusOnDragging;
        }

        boolean isShowTextBelowSectionMark = isShowSectionText && mSectionTextPosition ==
                TextPosition.BELOW_SECTION_MARK;
        boolean conditionInterval = true;

        if (isShowTextBelowSectionMark || isShowSectionMark) {
            drawMark(canvas, xLeft, yTop, isShowTextBelowSectionMark, conditionInterval);
        }

        if (!isThumbOnDragging) {
            mThumbCenterX = mTrackLength / mDelta * (mProgress - mMin) + xLeft;
        }

        if (isShowThumbText && !isThumbOnDragging && isTouchToSeekAnimEnd) {
            drawThumbText(canvas, yTop);
        }

        mPaint.setColor(mSecondTrackColor);
        mPaint.setStrokeWidth(mSecondTrackSize);
        canvas.drawLine(xLeft, yTop, mThumbCenterX, yTop, mPaint);

        mPaint.setColor(mTrackColor);
        mPaint.setStrokeWidth(mTrackSize);
        canvas.drawLine(mThumbCenterX, yTop, xRight, yTop, mPaint);

        mPaint.setColor(mThumbColor);
        if (isShowThumbShadow) {
            canvas.drawCircle(mThumbCenterX, yTop, isThumbOnDragging ? mThumbRadiusOnDragging * mThumbRatio : mThumbRadius * mThumbRatio, mPaint);
            mPaint.setColor(getColorWithAlpha(mThumbColor, mThumbBgAlpha));
        }
        canvas.drawCircle(mThumbCenterX, yTop, isThumbOnDragging ? mThumbRadiusOnDragging : mThumbRadius, mPaint);


        if (!isShowSign) return;
        drawValueSign(canvas, mSignHeight, (int) mThumbCenterX);
    }

    private void drawMark(Canvas canvas, float xLeft, float yTop, boolean isShowTextBelowSectionMark, boolean conditionInterval) {
        float r = (mThumbRadiusOnDragging - DevicesUtils.dip2px(2)) / 2f;
        float junction = mTrackLength / mDelta * Math.abs(mProgress - mMin) + mLeft;
        mPaint.setTextSize(mSectionTextSize);
        mPaint.getTextBounds("0123456789", 0, "0123456789".length(), mRectText);

        float x_;
        float y_ = yTop + mRectText.height() + mThumbRadiusOnDragging;

        for (int i = 0; i <= mSectionCount; i++) {
            x_ = xLeft + i * mSectionOffset;
            mPaint.setColor(x_ <= junction ? mSecondTrackColor : mTrackColor);
            canvas.drawCircle(x_, yTop, r, mPaint);

            if (isShowTextBelowSectionMark) {
                float m = mMin + mSectionValue * i;
                if(isEnabled()) mPaint.setColor(mSectionTextColor);
                if (mSectionTextInterval > 1) {
                    if (conditionInterval && i % mSectionTextInterval == 0) {
                        if (isSidesLabels) {
                            canvas.drawText(mSidesLabels[i], x_, y_, mPaint);
                        } else {
                            canvas.drawText(isFloatType ? float2String(m) : (int) m + "", x_, y_, mPaint);
                        }
                    }
                } else {
                    if (conditionInterval && i % mSectionTextInterval == 0) {
                        if (isSidesLabels && i / mSectionTextInterval <= mSidesLabels.length) {
                            canvas.drawText(mSidesLabels[i / mSectionTextInterval], x_, y_, mPaint);
                        } else {
                            canvas.drawText(isFloatType ? float2String(m) : (int) m + "", x_, y_, mPaint);
                        }
                    }
                }
            }
        }
    }

    private void drawThumbText(Canvas canvas, float yTop) {
        mPaint.setColor(mThumbTextColor);
        mPaint.setTextSize(mThumbTextSize);
        mPaint.getTextBounds("0123456789", 0, "0123456789".length(), mRectText);
        float y_ = yTop + mRectText.height() + mThumbRadiusOnDragging;

        if (isFloatType || (isShowProgressInFloat && mSectionTextPosition == TextPosition.BOTTOM_SIDES &&
                mProgress != mMin && mProgress != mMax)) {
            float progress = getProgressFloat();
            String value = String.valueOf(progress);
            if (mFormat != null) {
                value = mFormat.format(progress);
            }
            if (unit != null && !unit.isEmpty()) {
                if (!mReverse) {
                    value += String.format("%s", unit);
                } else {
                    value = String.format("%s", unit) + value;
                }
            }
            drawSignText(canvas, value, mThumbCenterX, y_, mPaint);
        } else {
            int progress = getProgress();
            String value = String.valueOf(progress);
            if (mFormat != null) {
                value = mFormat.format(progress);
            }
            if (unit != null && !unit.isEmpty()) {
                if (!mReverse) {
                    value += String.format("%s", unit);
                } else {
                    value = String.format("%s", unit) + value;
                }
            }
            drawSignText(canvas, value, mThumbCenterX, y_, mPaint);
        }
    }

    public void drawSignText(Canvas canvas, String text, float x, float y, Paint paint) {
        canvas.drawText(text, x, y, paint);
    }

    private void drawValueSign(Canvas canvas, int valueSignSpaceHeight, int valueSignCenter) {
        valueSignBounds.set(valueSignCenter - mSignWidth / 2, getPaddingTop(), valueSignCenter + mSignWidth / 2, mSignHeight - mSignArrowHeight + getPaddingTop());

        int bordersize = isShowSignBorder ? mSignBorderSize : 0;
        if (valueSignBounds.left < getPaddingLeft()) {
            int difference = -valueSignBounds.left + getPaddingLeft() + bordersize;
            roundRectangleBounds.set(valueSignBounds.left + difference, valueSignBounds.top, valueSignBounds.right +
                    difference, valueSignBounds.bottom);
        } else if (valueSignBounds.right > getMeasuredWidth() - getPaddingRight()) {
            int difference = valueSignBounds.right - getMeasuredWidth() + getPaddingRight() + bordersize;
            roundRectangleBounds.set(valueSignBounds.left - difference, valueSignBounds.top, valueSignBounds.right -
                    difference, valueSignBounds.bottom);
        } else {
            roundRectangleBounds.set(valueSignBounds.left, valueSignBounds.top, valueSignBounds.right,
                    valueSignBounds.bottom);
        }

        canvas.drawRoundRect(roundRectangleBounds, mSignRound, mSignRound, signPaint);
        if (isShowSignBorder) {
            roundRectangleBounds.top = roundRectangleBounds.top + mSignBorderSize / 2f;
            canvas.drawRoundRect(roundRectangleBounds, mSignRound, mSignRound, signborderPaint);
        }
        int barRoundingRadius = isThumbOnDragging ? mThumbRadiusOnDragging : mThumbRadius;
        int difference = 0;
        if (valueSignCenter - mSignArrowWidth / 2 < barRoundingRadius + getPaddingLeft() + bordersize) {
            difference = barRoundingRadius - valueSignCenter + getPaddingLeft() + bordersize;
        } else if (valueSignCenter + mSignArrowWidth / 2 > getMeasuredWidth() - barRoundingRadius - getPaddingRight() - bordersize) {
            difference = (getMeasuredWidth() - barRoundingRadius) - valueSignCenter - getPaddingRight() - bordersize;
        }

        point1.set(valueSignCenter - mSignArrowWidth / 2 + difference, valueSignSpaceHeight - mSignArrowHeight + getPaddingTop());
        point2.set(valueSignCenter + mSignArrowWidth / 2 + difference, valueSignSpaceHeight - mSignArrowHeight + getPaddingTop());
        point3.set(valueSignCenter + difference, valueSignSpaceHeight + getPaddingTop());

        drawTriangle(canvas, point1, point2, point3, signPaint);
        if (isShowSignBorder) {
            drawTriangleBoder(canvas, point1, point2, point3, signborderPaint);
        }

        createValueTextLayout();
        if (valueTextLayout != null) {
            canvas.translate(roundRectangleBounds.left, roundRectangleBounds.top + roundRectangleBounds.height() / 2 - valueTextLayout.getHeight() / 2);
            valueTextLayout.draw(canvas);
        }
    }

    private void drawTriangle(Canvas canvas, Point point1, Point point2, Point point3, Paint paint) {
        trianglePath.reset();
        trianglePath.moveTo(point1.x, point1.y);
        trianglePath.lineTo(point2.x, point2.y);
        trianglePath.lineTo(point3.x, point3.y);
        trianglePath.lineTo(point1.x, point1.y);
        trianglePath.close();

        canvas.drawPath(trianglePath, paint);
    }

    
    private void drawTriangleBoder(Canvas canvas, Point point1, Point point2, Point point3, Paint paint) {
        triangleboderPath.reset();
        triangleboderPath.moveTo(point1.x, point1.y);
        triangleboderPath.lineTo(point2.x, point2.y);
        paint.setColor(signPaint.getColor());
        float value = mSignBorderSize / 6f;
        paint.setStrokeWidth(mSignBorderSize + 1f);
        canvas.drawPath(triangleboderPath, paint);
        triangleboderPath.reset();
        paint.setStrokeWidth(mSignBorderSize);
        triangleboderPath.moveTo(point1.x - value, point1.y - value);
        triangleboderPath.lineTo(point3.x, point3.y);
        triangleboderPath.lineTo(point2.x + value, point2.y - value);
        paint.setColor(mSignBorderColor);
        canvas.drawPath(triangleboderPath, paint);
    }

    
    public void setUnit(String unit) {
        this.unit = unit;
        createValueTextLayout();
        invalidate();
        requestLayout();
    }

    private void createValueTextLayout() {
        String value = "";
        if (isShowProgressInFloat) {
            float progress = getProgressFloat();
            value = String.valueOf(progress);
            if (mFormat != null) {
                value = mFormat.format(progress);
            }
        } else {
            int progress = getProgress();
            value = String.valueOf(progress);
            if (mFormat != null) {
                value = mFormat.format(progress);
            }
        }
        if (unit != null && !unit.isEmpty()) {
            if (!mReverse) {
                value += String.format(" <small>%s</small> ", unit);
            } else {
                value = String.format(" %s ", unit) + value;
            }
        }
        Spanned spanned = Html.fromHtml(value);
        valueTextLayout = new StaticLayout(spanned, valueTextPaint, mSignWidth, Layout.Alignment.ALIGN_CENTER, 1, 0, false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        post(this::requestLayout);
    }

    float dx;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);

                isThumbOnDragging = isThumbTouched(event);
                if (isThumbOnDragging) {
                    if (isSeekBySection && !triggerSeekBySection) {
                        triggerSeekBySection = true;
                    }
                    invalidate();
                } else if (isTouchToSeek && isTrackTouched(event)) {
                    isThumbOnDragging = true;
                    mThumbCenterX = event.getX();
                    if (mThumbCenterX < mLeft) {
                        mThumbCenterX = mLeft;
                    }
                    if (mThumbCenterX > mRight) {
                        mThumbCenterX = mRight;
                    }
                    mProgress = (mThumbCenterX - mLeft) * mDelta / mTrackLength + mMin;
                    invalidate();
                }

                dx = mThumbCenterX - event.getX();

                break;
            case MotionEvent.ACTION_MOVE:
                if (isThumbOnDragging) {
                    mThumbCenterX = event.getX() + dx;
                    if (mThumbCenterX < mLeft) {
                        mThumbCenterX = mLeft;
                    }
                    if (mThumbCenterX > mRight) {
                        mThumbCenterX = mRight;
                    }
                    mProgress = (mThumbCenterX - mLeft) * mDelta / mTrackLength + mMin;
                    invalidate();

                    if (mProgressListener != null) {
                        mProgressListener.onProgressChanged(this, getProgress(), getProgressFloat(), true);
                    }
                }

                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);

                if (isAutoAdjustSectionMark) {
                    if (isTouchToSeek) {
                        postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                isTouchToSeekAnimEnd = false;
                                autoAdjustSection();
                            }
                        }, isThumbOnDragging ? 0 : 300);
                    } else {
                        autoAdjustSection();
                    }
                } else if (isThumbOnDragging || isTouchToSeek) {
                    animate()
                            .setDuration(mAnimDuration)
                            .setStartDelay(!isThumbOnDragging && isTouchToSeek ? 300 : 0)
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    isThumbOnDragging = false;
                                    invalidate();

                                    if (mProgressListener != null) {
                                        mProgressListener.onProgressChanged(SignSeekBar.this,
                                                getProgress(), getProgressFloat(), true);
                                    }
                                }

                                @Override
                                public void onAnimationCancel(Animator animation) {
                                    isThumbOnDragging = false;
                                    invalidate();
                                }
                            })
                            .start();
                }

                if (mProgressListener != null) {
                    mProgressListener.getProgressOnActionUp(this, getProgress(), getProgressFloat());
                }

                break;
        }

        return isThumbOnDragging || isTouchToSeek || super.onTouchEvent(event);
    }

    
    public int getColorWithAlpha(int color, float ratio) {
        int newColor = 0;
        int alpha = Math.round(Color.alpha(color) * ratio);
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        newColor = Color.argb(alpha, r, g, b);
        return newColor;
    }

    
    private boolean isThumbTouched(MotionEvent event) {
        if (!isEnabled())
            return false;
        float mCircleR = isThumbOnDragging ? mThumbRadiusOnDragging : mThumbRadius;
        float x = mTrackLength / mDelta * (mProgress - mMin) + mLeft;
        float y = getMeasuredHeight() / 2f;
        return (event.getX() - x) * (event.getX() - x) + (event.getY() - y) * (event.getY() - y)
                <= (mLeft + mCircleR) * (mLeft + mCircleR);
    }

    
    private boolean isTrackTouched(MotionEvent event) {
        return isEnabled() && event.getX() >= getPaddingLeft() && event.getX() <= getMeasuredWidth() - getPaddingRight()
                && event.getY() >= getPaddingTop() && event.getY() <= getMeasuredHeight() - getPaddingBottom();
    }

    
    private void autoAdjustSection() {
        int i;
        float x = 0;
        for (i = 0; i <= mSectionCount; i++) {
            x = i * mSectionOffset + mLeft;
            if (x <= mThumbCenterX && mThumbCenterX - x <= mSectionOffset) {
                break;
            }
        }

        BigDecimal bigDecimal = BigDecimal.valueOf(mThumbCenterX);
        float x_ = bigDecimal.setScale(1, RoundingMode.HALF_UP).floatValue();
        boolean onSection = x_ == x;

        AnimatorSet animatorSet = new AnimatorSet();
        ValueAnimator valueAnim = null;
        if (!onSection) {
            if (mThumbCenterX - x <= mSectionOffset / 2f) {
                valueAnim = ValueAnimator.ofFloat(mThumbCenterX, x);
            } else {
                valueAnim = ValueAnimator.ofFloat(mThumbCenterX, (i + 1) * mSectionOffset + mLeft);
            }
            valueAnim.setInterpolator(new LinearInterpolator());
            valueAnim.addUpdateListener(animation -> {
                mThumbCenterX = (float) animation.getAnimatedValue();
                mProgress = (mThumbCenterX - mLeft) * mDelta / mTrackLength + mMin;
                invalidate();

                if (mProgressListener != null) {
                    mProgressListener.onProgressChanged(SignSeekBar.this, getProgress(), getProgressFloat(), true);
                }
            });
        }
        if (!onSection) {
            animatorSet.setDuration(mAnimDuration).playTogether(valueAnim);
        }
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mProgress = (mThumbCenterX - mLeft) * mDelta / mTrackLength + mMin;
                isThumbOnDragging = false;
                isTouchToSeekAnimEnd = true;
                invalidate();

                if (mProgressListener != null) {
                    mProgressListener.getProgressOnFinally(SignSeekBar.this, getProgress(), getProgressFloat(), true);
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mProgress = (mThumbCenterX - mLeft) * mDelta / mTrackLength + mMin;
                isThumbOnDragging = false;
                isTouchToSeekAnimEnd = true;
                invalidate();
            }
        });
        animatorSet.start();
    }

    private String getMinText() {
        return isFloatType ? float2String(mMin) : String.valueOf((int) mMin);
    }

    private String getMaxText() {
        return isFloatType ? float2String(mMax) : String.valueOf((int) mMax);
    }

    public float getMin() {
        return mMin;
    }

    public float getMax() {
        return mMax;
    }

    public void setProgress(float progress) {
        mProgress = progress;
        if (mProgressListener != null) {
            mProgressListener.onProgressChanged(this, getProgress(), getProgressFloat(), false);
            mProgressListener.getProgressOnFinally(this, getProgress(), getProgressFloat(), false);
        }
        postInvalidate();
    }

    public int getProgress() {
        if (isSeekBySection && triggerSeekBySection) {
            float half = mSectionValue / 2;

            if (mProgress >= mPreSecValue) {
                if (mProgress >= mPreSecValue + half) {
                    mPreSecValue += mSectionValue;
                    return Math.round(mPreSecValue);
                } else {
                    return Math.round(mPreSecValue);
                }
            } else {
                if (mProgress >= mPreSecValue - half) {
                    return Math.round(mPreSecValue);
                } else {
                    mPreSecValue -= mSectionValue;
                    return Math.round(mPreSecValue);
                }
            }
        }

        return Math.round(mProgress);
    }

    public float getProgressFloat() {
        return formatFloat(mProgress);
    }

    public void setOnProgressChangedListener(OnProgressChangedListener onProgressChangedListener) {
        mProgressListener = onProgressChangedListener;
    }

    void config(SignConfigBuilder builder) {
        mMin = builder.min;
        mMax = builder.max;
        mProgress = builder.progress;
        isFloatType = builder.floatType;
        mTrackSize = builder.trackSize;
        mSecondTrackSize = builder.secondTrackSize;
        mThumbRadius = builder.thumbRadius;
        mThumbRadiusOnDragging = builder.thumbRadiusOnDragging;
        mTrackColor = builder.trackColor;
        mSecondTrackColor = builder.secondTrackColor;
        mThumbColor = builder.thumbColor;
        mSectionCount = builder.sectionCount;
        isShowSectionMark = builder.showSectionMark;
        isAutoAdjustSectionMark = builder.autoAdjustSectionMark;
        isShowSectionText = builder.showSectionText;
        mSectionTextSize = builder.sectionTextSize;
        mSectionTextColor = builder.sectionTextColor;
        mSectionTextPosition = builder.sectionTextPosition;
        mSectionTextInterval = builder.sectionTextInterval;
        isShowThumbText = builder.showThumbText;
        mThumbTextSize = builder.thumbTextSize;
        mThumbTextColor = builder.thumbTextColor;
        isShowProgressInFloat = builder.showProgressInFloat;
        mAnimDuration = builder.animDuration;
        isTouchToSeek = builder.touchToSeek;
        isSeekBySection = builder.seekBySection;
        mSidesLabels = mConfigBuilder.bottomSidesLabels;
        isSidesLabels = mSidesLabels != null && mSidesLabels.length > 0;
        mThumbBgAlpha = mConfigBuilder.thumbBgAlpha;
        mThumbRatio = mConfigBuilder.thumbRatio;
        isShowThumbShadow = mConfigBuilder.showThumbShadow;
        unit = mConfigBuilder.unit;
        mReverse = mConfigBuilder.reverse;
        mFormat = mConfigBuilder.format;
        mSignColor = builder.signColor;
        mSignTextSize = builder.signTextSize;
        mSignTextColor = builder.signTextColor;
        isShowSign = builder.showSign;
        mSignArrowWidth = builder.signArrowWidth;
        mSignArrowHeight = builder.signArrowHeight;
        mSignRound = builder.signRound;
        mSignHeight = builder.signHeight;
        mSignWidth = builder.signWidth;
        isShowSignBorder = builder.showSignBorder;
        mSignBorderSize = builder.signBorderSize;
        mSignBorderColor = builder.signBorderColor;
        isSignArrowAutofloat = builder.signArrowAutofloat;

        init();
        initConfigByPriority();
        createValueTextLayout();
        if (mProgressListener != null) {
            mProgressListener.onProgressChanged(this, getProgress(), getProgressFloat(), false);
            mProgressListener.getProgressOnFinally(this, getProgress(), getProgressFloat(), false);
        }

        mConfigBuilder = null;

        requestLayout();
    }

    public SignConfigBuilder getConfigBuilder() {
        if (mConfigBuilder == null) {
            mConfigBuilder = new SignConfigBuilder(this);
        }
        mConfigBuilder.min = mMin;
        mConfigBuilder.max = mMax;
        mConfigBuilder.progress = mProgress;
        mConfigBuilder.floatType = isFloatType;
        mConfigBuilder.trackSize = mTrackSize;
        mConfigBuilder.secondTrackSize = mSecondTrackSize;
        mConfigBuilder.thumbRadius = mThumbRadius;
        mConfigBuilder.thumbRadiusOnDragging = mThumbRadiusOnDragging;
        mConfigBuilder.trackColor = mTrackColor;
        mConfigBuilder.secondTrackColor = mSecondTrackColor;
        mConfigBuilder.thumbColor = mThumbColor;
        mConfigBuilder.sectionCount = mSectionCount;
        mConfigBuilder.showSectionMark = isShowSectionMark;
        mConfigBuilder.autoAdjustSectionMark = isAutoAdjustSectionMark;
        mConfigBuilder.showSectionText = isShowSectionText;
        mConfigBuilder.sectionTextSize = mSectionTextSize;
        mConfigBuilder.sectionTextColor = mSectionTextColor;
        mConfigBuilder.sectionTextPosition = mSectionTextPosition;
        mConfigBuilder.sectionTextInterval = mSectionTextInterval;
        mConfigBuilder.showThumbText = isShowThumbText;
        mConfigBuilder.thumbTextSize = mThumbTextSize;
        mConfigBuilder.thumbTextColor = mThumbTextColor;
        mConfigBuilder.showProgressInFloat = isShowProgressInFloat;
        mConfigBuilder.animDuration = mAnimDuration;
        mConfigBuilder.touchToSeek = isTouchToSeek;
        mConfigBuilder.seekBySection = isSeekBySection;
        mConfigBuilder.bottomSidesLabels = mSidesLabels;
        mConfigBuilder.thumbBgAlpha = mThumbBgAlpha;
        mConfigBuilder.thumbRatio = mThumbRatio;
        mConfigBuilder.showThumbShadow = isShowThumbShadow;
        mConfigBuilder.unit = unit;
        mConfigBuilder.reverse = mReverse;
        mConfigBuilder.format = mFormat;
        mConfigBuilder.signColor = mSignColor;
        mConfigBuilder.signTextSize = mSignTextSize;
        mConfigBuilder.signTextColor = mSignTextColor;
        mConfigBuilder.showSign = isShowSign;
        mConfigBuilder.signArrowHeight = mSignArrowHeight;
        mConfigBuilder.signArrowWidth = mSignArrowWidth;
        mConfigBuilder.signRound = mSignRound;
        mConfigBuilder.signHeight = mSignHeight;
        mConfigBuilder.signWidth = mSignWidth;
        mConfigBuilder.showSignBorder = isShowSignBorder;
        mConfigBuilder.signBorderSize = mSignBorderSize;
        mConfigBuilder.signBorderColor = mSignBorderColor;
        mConfigBuilder.signArrowAutofloat = isSignArrowAutofloat;

        return mConfigBuilder;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable("save_instance", super.onSaveInstanceState());
        bundle.putFloat("progress", mProgress);
        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle bundle) {
            mProgress = bundle.getFloat("progress");
            super.onRestoreInstanceState(bundle.getParcelable("save_instance"));
            setProgress(mProgress);
            return;
        }
        super.onRestoreInstanceState(state);
    }

    private String float2String(float value) {
        return String.valueOf(formatFloat(value));
    }

    private float formatFloat(float value) {
        BigDecimal bigDecimal = BigDecimal.valueOf(value);
        return bigDecimal.setScale(1, RoundingMode.HALF_UP).floatValue();
    }

    
    public interface OnProgressChangedListener {

        void onProgressChanged(SignSeekBar signSeekBar, int progress, float progressFloat, boolean fromUser);

        void getProgressOnActionUp(SignSeekBar signSeekBar, int progress, float progressFloat);

        void getProgressOnFinally(SignSeekBar signSeekBar, int progress, float progressFloat, boolean fromUser);
    }
}