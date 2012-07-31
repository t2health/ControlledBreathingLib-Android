
package com.t2.controlledbreathing;

import com.t2.controlledbreathing.ControlledBreathingBarView.OnControlledBreathingEventListener.ControlledBreathingEvent;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Transformation;
import android.widget.LinearLayout;

public class ControlledBreathingBarView extends View implements OnSharedPreferenceChangeListener {

    private static final int BORDER_HEIGHT = 12;

    private static final int SEGMENT_HEIGHT = 1;

    // Represents the maximum possible height of the bar. This is set when
    // the view is first laid out.
    private int mMaxBarHeight;

    // Represents the current height of the breath bar
    private int mBarHeight;

    // Represents the amount of space required between each one-second segment
    // line to fill the max bar height
    private float mInhaleSegmentHeight;
    private float mExhaleSegmentHeight;

    private InhaleAnimation mInhaleAnimation;
    private ExhaleAnimation mExhaleAnimation;
    private HoldAnimation mHoldAnimation;

    // Attribute assigned via xml. This determines if the bar fills horizontally
    // or vertically.
    private boolean mLandscape;

    // Paint used to draw the bar itself
    private Paint mFillPaint;

    // Paint used to draw segment lines while inhaling
    private Paint mInhaleLinePaint;
    private Paint mInhaleInnerLinePaint;

    // Paint used to draw segment lines while exhaling
    private Paint mExhaleLinePaint;

    // Represents the current state of the bar. Used to restore state.
    private BarState mState;

    // A temporary animation used to finish a partially completed animation in
    // the event the activity gets consumed e.g. orientation change
    private Animation mRestoredAnimation;

    private RectF mBarRect = new RectF();

    // The previous animation interpolation time. Used to restore the state of
    // the breathing bar in the event the activity gets consumed.
    private float mRestoredInterp = -1;

    private OnControlledBreathingEventListener mListener;

    public ControlledBreathingBarView(Context context) {
        super(context);
        init();
    }

    public ControlledBreathingBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.controlled_breathing_bar);
        mLandscape = a.getInteger(R.styleable.controlled_breathing_bar_android_orientation, LinearLayout.VERTICAL) == LinearLayout.HORIZONTAL;

        init();
    }

    public ControlledBreathingBarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.controlled_breathing_bar, defStyle, -1);
        mLandscape = a.getInteger(R.styleable.controlled_breathing_bar_android_orientation, LinearLayout.VERTICAL) == LinearLayout.HORIZONTAL;

        init();
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof BarState) {
            super.onRestoreInstanceState(((BarState) state).getSuperState());
            mState = ((BarState) state);

            if (mState.mStarted) {
                mRestoredInterp = mState.mInterpTime;
                mRestoredAnimation = null;
                if (mState.mHolding) {
                    mRestoredAnimation = new HoldAnimation();
                    mRestoredAnimation.setDuration(mHoldAnimation.getDuration());
                } else if (mState.mInhaling) {
                    mRestoredAnimation = new InhaleAnimation();
                    mRestoredAnimation.setDuration(mInhaleAnimation.getDuration());
                } else {
                    mRestoredAnimation = new ExhaleAnimation();
                    mRestoredAnimation.setDuration(mExhaleAnimation.getDuration());
                }
                mRestoredAnimation.setInterpolator(new DecelerateInterpolator());
                mRestoredAnimation.scaleCurrentDuration(1 - mState.mInterpTime);
                startAnimation(mRestoredAnimation);
            }
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable state = super.onSaveInstanceState();
        BarState saveState = new BarState(state);
        saveState.mInterpTime = mState.mInterpTime;
        saveState.mInhaling = mState.mInhaling;
        saveState.mHolding = mState.mHolding;
        saveState.mStarted = mState.mStarted;
        saveState.mCanceled = mState.mCanceled;
        return saveState;
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getContext().getString(R.string.pref_exhale_duration))) {
            mExhaleAnimation.setDuration(sharedPreferences.getLong(key, 7000));
            float secs = mExhaleAnimation.getDuration() / 1000.0f;
            mExhaleSegmentHeight = (float) mMaxBarHeight / secs;
        } else if (key.equals(getContext().getString(R.string.pref_inhale_duration))) {
            mInhaleAnimation.setDuration(sharedPreferences.getLong(key, 7000));
            float secs = mInhaleAnimation.getDuration() / 1000.0f;
            mInhaleSegmentHeight = (float) mMaxBarHeight / secs;
        } else if (key.equals(getContext().getString(R.string.pref_hold_duration))) {
            long holdDuration = Long.valueOf(sharedPreferences.getString(key, "2000"));
            if (holdDuration > 0) {
                if (mHoldAnimation == null) {
                    mHoldAnimation = new HoldAnimation();
                }
                mHoldAnimation.setDuration(holdDuration);
            } else {
                mHoldAnimation = null;
            }
        }
    }

    public void setOnControlledBreathingEventListener(OnControlledBreathingEventListener listener) {
        mListener = listener;
    }

    public void start() {
        mState.mStarted = true;
        mState.mCanceled = false;
        startAnimation(mExhaleAnimation);
    }

    public void stop() {
        mState.mStarted = false;
        mState.mCanceled = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        super.onDraw(canvas);
        canvas.restore();

        // Rotate the canvas if we are drawing a horizontal bar
        canvas.save();
        if (mLandscape) {
            canvas.translate(mMaxBarHeight + BORDER_HEIGHT, 0);
            canvas.rotate(90.0f);
        }

        canvas.save();
        canvas.translate(6, 6);

        if (isInEditMode()) {

            drawExampleBar(canvas);
            canvas.drawText(mMaxBarHeight + "", 5, 50, mExhaleLinePaint);
            canvas.drawText(mHeight + "", 5, 100, mExhaleLinePaint);
            canvas.drawText(mWidth + "", 5, 150, mExhaleLinePaint);
            return;
        }

        evaluateSegmentLineAlpha();

        canvas.save();
        canvas.translate(0, mMaxBarHeight - mBarHeight);
        mBarRect.set(0, 0, mWidth, mBarHeight);
        canvas.drawRect(mBarRect, mFillPaint);
        canvas.restore();

        drawSegmentLines(canvas);

        canvas.restore();
        canvas.restore();

    }

    private RectF mSegmentRect;
    private int mHeight;
    private int mWidth;

    private void drawSegmentLines(Canvas canvas) {
        canvas.save();
        canvas.clipRect(0, mMaxBarHeight - mBarHeight, mWidth, mMaxBarHeight, Region.Op.REPLACE);
        canvas.translate(0, mMaxBarHeight);
        for (int i = 1; i < FloatMath.ceil(mExhaleAnimation.getDuration() / 1000.0f); i++) {
            canvas.translate(0, -mExhaleSegmentHeight);
            canvas.drawRoundRect(mSegmentRect, 2f, 2f, mExhaleLinePaint);
        }
        canvas.restore();

        canvas.save();
        canvas.clipRect(0, 0, mWidth, mMaxBarHeight - mBarHeight, Region.Op.REPLACE);
        canvas.translate(0, mMaxBarHeight);
        for (int i = 1; i < FloatMath.ceil(mInhaleAnimation.getDuration() / 1000.0f); i++) {
            canvas.translate(0, -mInhaleSegmentHeight);
            canvas.drawRoundRect(mSegmentRect, 2f, 2f, mInhaleLinePaint);
        }
        canvas.restore();
    }

    private void evaluateSegmentLineAlpha() {
        float ratio = (mState.mInhaling ? mMaxBarHeight - mBarHeight : mBarHeight) / (0.1f * mMaxBarHeight);
        if (ratio > 1) {
            ratio = 0;
        } else {
            ratio = Math.abs(1 - ratio) * 255;
        }

        if (mState.mInhaling) {
            mExhaleLinePaint.setAlpha((int) ratio);
            mInhaleLinePaint.setAlpha(255);
            mInhaleInnerLinePaint.setAlpha(255);
        } else {
            mInhaleLinePaint.setAlpha((int) ratio);
            mInhaleInnerLinePaint.setAlpha((int) ratio);
            mExhaleLinePaint.setAlpha(255);
        }
    }

    private void drawExampleBar(Canvas canvas) {
        mBarHeight = (int) (mBarHeight);
        canvas.save();
        canvas.translate(0, mMaxBarHeight - mBarHeight);
        canvas.drawRect(new RectF(0, 0, mWidth, mBarHeight), mFillPaint);
        canvas.restore();
        final float segmentHeight = mMaxBarHeight / 7.2f;
        canvas.save();
        canvas.clipRect(0, mMaxBarHeight - mBarHeight, mWidth, mMaxBarHeight, Region.Op.REPLACE);
        canvas.translate(0, mMaxBarHeight);
        for (int i = 1; i < Math.ceil(7.2); i++) {
            canvas.translate(0, -segmentHeight);
            canvas.drawRoundRect(mSegmentRect, 5f, 5f, mExhaleLinePaint);
        }

        canvas.restore();
        return;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (mMaxBarHeight == 0) {
            mMaxBarHeight = (mLandscape ? getMeasuredWidth() : getMeasuredHeight()) - BORDER_HEIGHT;
            mBarHeight = mMaxBarHeight;
            mHeight = (mLandscape ? getMeasuredWidth() : getMeasuredHeight());
            mWidth = (mLandscape ? getMeasuredHeight() : getMeasuredWidth()) - BORDER_HEIGHT;
            mSegmentRect = new RectF(5, -SEGMENT_HEIGHT, mWidth - 5, SEGMENT_HEIGHT);
        }

        if (!isInEditMode()) {
            float secs = mInhaleAnimation.getDuration() / 1000.0f;
            mInhaleSegmentHeight = (float) mMaxBarHeight / secs;
            secs = mExhaleAnimation.getDuration() / 1000.0f;
            mExhaleSegmentHeight = (float) mMaxBarHeight / secs;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void fireEvent(ControlledBreathingEvent event, long duration) {
        if (mListener != null) {
            mListener.OnControlledBreathingEvent(event, duration);
        }
    }

    private void init() {
        mFillPaint = new Paint();
        mFillPaint.setColor(0xAA000000);

        mInhaleLinePaint = new Paint();
        mInhaleLinePaint.setColor(0xAAF8F8F8);
        mInhaleLinePaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mInhaleInnerLinePaint = new Paint();
        mInhaleInnerLinePaint.setColor(0xAAF8F8F8);
        mInhaleInnerLinePaint.setFlags(Paint.ANTI_ALIAS_FLAG);

        mExhaleLinePaint = new Paint();
        mExhaleLinePaint.setColor(0xAAF8F8F8);
        mExhaleLinePaint.setFlags(Paint.ANTI_ALIAS_FLAG);

        mState = new BarState();

        if (isInEditMode()) {
            return;
        }

        mInhaleAnimation = new InhaleAnimation();
        mExhaleAnimation = new ExhaleAnimation();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        long holdDuration = Long.valueOf(prefs.getString(getContext().getString(R.string.pref_hold_duration), "2000"));
        if (holdDuration > 0) {
            mHoldAnimation = new HoldAnimation();
            mHoldAnimation.setDuration(holdDuration);
        }
        prefs.registerOnSharedPreferenceChangeListener(this);
        mInhaleAnimation.setDuration(prefs.getLong(getContext().getString(R.string.pref_inhale_duration), 7000));
        mExhaleAnimation.setDuration(prefs.getLong(getContext().getString(R.string.pref_exhale_duration), 7000));

    }

    public static interface OnControlledBreathingEventListener {
        public void OnControlledBreathingEvent(ControlledBreathingEvent event, long duration);

        public static enum ControlledBreathingEvent {
            INHALE_START,
            INHALE_END,
            INHALE_HALF,
            EXHALE_START,
            EXHALE_HALF,
            EXHALE_END,
            HOLD_START,
            HOLD_END,
            INHALE_RESUME,
            EXHALE_RESUME,
            HOLD_RESUME;
        }
    }

    private static final class BarState extends BaseSavedState {

        @SuppressWarnings("unused")
        public static final Parcelable.Creator<BarState> CREATOR =
                new Parcelable.Creator<BarState>() {
                    public BarState createFromParcel(Parcel source) {
                        return new BarState(source);
                    }

                    public BarState[] newArray(int size) {
                        return new BarState[size];
                    }
                };

        private float mInterpTime;
        private boolean mInhaling;
        private boolean mHolding;
        private boolean mStarted;
        private boolean mCanceled;

        public BarState() {
            super(Parcel.obtain());
        }

        public BarState(Parcel in) {
            super(in);
            mInterpTime = in.readFloat();
            mInhaling = in.readInt() == 1;
            mHolding = in.readInt() == 1;
            mStarted = in.readInt() == 1;
            mCanceled = in.readInt() == 1;
        }

        public BarState(Parcelable parcelable) {
            super(parcelable);
        }

    }

    private class ExhaleAnimation extends Animation implements AnimationListener {

        private boolean mMidpointFired;

        @Override
        public void initialize(int width, int height, int parentWidth, int parentHeight) {
            super.initialize(width, height, parentWidth, parentHeight);
            setAnimationListener(this);
            setInterpolator(new AccelerateDecelerateInterpolator());
        }

        public void onAnimationEnd(Animation animation) {
            reset();

            if (mState.mCanceled) {
                return;
            }

            if (mRestoredInterp >= 0) {
                mRestoredInterp = -1;
            }

            fireEvent(ControlledBreathingEvent.EXHALE_END, 0);
            startAnimation(mInhaleAnimation);
        }

        public void onAnimationRepeat(Animation animation) {
        }

        public void onAnimationStart(Animation animation) {
            mState.mInhaling = false;
            if (mRestoredInterp >= 0) {
                fireEvent(ControlledBreathingEvent.EXHALE_RESUME, getDuration());
            } else {
                fireEvent(ControlledBreathingEvent.EXHALE_START, getDuration());
            }
        }

        @Override
        public void reset() {
            super.reset();
            mMidpointFired = false;
        }

        @Override
        public boolean willChangeBounds() {
            return true;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            if (mRestoredInterp >= 0) {
                mState.mInterpTime = mRestoredInterp + (interpolatedTime * (1.0f - mRestoredInterp));
            } else {
                mState.mInterpTime = interpolatedTime;
            }

            if (!mMidpointFired && mState.mInterpTime >= 0.5f) {
                fireEvent(ControlledBreathingEvent.EXHALE_HALF, (long) (getDuration() / 2.0f));
                mMidpointFired = true;
            }

            mBarHeight = (int) (mMaxBarHeight * (1 - mState.mInterpTime)) + (int) (5 * mState.mInterpTime);
            postInvalidate();
        }

    }

    private class HoldAnimation extends Animation implements AnimationListener {

        @Override
        public void initialize(int width, int height, int parentWidth, int parentHeight) {
            super.initialize(width, height, parentWidth, parentHeight);
            setAnimationListener(this);
        }

        public void onAnimationEnd(Animation animation) {
            reset();

            if (mState.mCanceled) {
                return;
            }

            if (mRestoredInterp >= 0) {
                mRestoredInterp = -1;
            }

            mState.mHolding = false;
            fireEvent(ControlledBreathingEvent.HOLD_END, 0);
            startAnimation(mExhaleAnimation);
        }

        public void onAnimationRepeat(Animation animation) {
        }

        public void onAnimationStart(Animation animation) {
            mState.mHolding = true;
            if (mRestoredInterp >= 0) {
                fireEvent(ControlledBreathingEvent.HOLD_RESUME, getDuration());
            } else {
                fireEvent(ControlledBreathingEvent.HOLD_START, getDuration());
            }

        }

        @Override
        public void reset() {
            super.reset();
        }

        @Override
        public boolean willChangeBounds() {
            return true;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            if (mRestoredInterp >= 0) {
                mState.mInterpTime = mRestoredInterp + (interpolatedTime * (1.0f - mRestoredInterp));
            } else {
                mState.mInterpTime = interpolatedTime;
            }
        }
    }

    private class InhaleAnimation extends Animation implements AnimationListener {

        private boolean mMidpointFired;

        @Override
        public void initialize(int width, int height, int parentWidth, int parentHeight) {
            super.initialize(width, height, parentWidth, parentHeight);
            setAnimationListener(this);
            setInterpolator(new AccelerateDecelerateInterpolator());

        }

        public void onAnimationEnd(Animation animation) {
            reset();

            if (mState.mCanceled) {
                return;
            }

            if (mRestoredInterp >= 0) {
                mRestoredInterp = -1;
            }

            fireEvent(ControlledBreathingEvent.INHALE_END, 0);
            if (mHoldAnimation != null) {
                startAnimation(mHoldAnimation);
            } else {
                startAnimation(mExhaleAnimation);
            }
        }

        public void onAnimationRepeat(Animation animation) {
        }

        public void onAnimationStart(Animation animation) {
            mState.mInhaling = true;
            if (mRestoredInterp >= 0) {
                fireEvent(ControlledBreathingEvent.INHALE_RESUME, getDuration());
            } else {
                fireEvent(ControlledBreathingEvent.INHALE_START, getDuration());
            }
        }

        @Override
        public void reset() {
            super.reset();
            mMidpointFired = false;
        }

        @Override
        public boolean willChangeBounds() {
            return true;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            if (mRestoredInterp >= 0) {
                mState.mInterpTime = mRestoredInterp + (interpolatedTime * (1.0f - mRestoredInterp));
            } else {
                mState.mInterpTime = interpolatedTime;
            }

            if (!mMidpointFired && mState.mInterpTime >= 0.5f) {
                fireEvent(ControlledBreathingEvent.INHALE_HALF, (long) (getDuration() / 2.0f));
                mMidpointFired = true;
            }

            mBarHeight = (int) (mMaxBarHeight * mState.mInterpTime) + (int) (5 * (1 - mState.mInterpTime));
            postInvalidate();

        }
    }

}
