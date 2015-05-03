package cdflynn.android.library.crossview;


import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

public class CrossView extends View {
    /**
     * Flag to denote the "plus" configuration
     */
    public static final int FLAG_STATE_PLUS = 0;
    /**
     * Flag to denote the "cross" configuration
     */
    public static final int FLAG_STATE_CROSS = 1;

    private static final float ARC_TOP_START = 225;
    private static final float ARC_TOP_ANGLE = 45f;
    private static final float ARC_BOTTOM_START = 45f;
    private static final float ARC_BOTTOM_ANGLE = 45f;
    private static final float ARC_LEFT_START = 315f;
    private static final float ARC_LEFT_ANGLE = -135f; // sweep backwards
    private static final float ARC_RIGHT_START = 135f;
    private static final float ARC_RIGHT_ANGLE = -135f; // sweep backwards

    private static final long ANIMATION_DURATION_MS = 300l;

    private static final int DEFAULT_COLOR = Color.BLACK;
    private static final float DEFAULT_STROKE_WIDTH = 4f;

    // Arcs that define the set of all points between which the two lines are drawn
    // Names (top, bottom, etc) are from the reference point of the "plus" configuration.
    private Path mArcTop;
    private Path mArcBottom;
    private Path mArcLeft;
    private Path mArcRight;

    // Pre-compute arc lengths when layout changes
    private float mArcLengthTop;
    private float mArcLengthBottom;
    private float mArcLengthLeft;
    private float mArcLengthRight;

    private Paint mPaint = new Paint();
    private int mColor = DEFAULT_COLOR;
    private RectF mRect;
    private PathMeasure mPathMeasure;

    /**
     * Internal state flag for the drawn appearance, plus or cross.
     * The default starting position is "plus". This represents the real configuration, whereas
     * {@code mPercent} holds the frame-by-frame position when animating between
     * the states.
     */
    private int mState = FLAG_STATE_PLUS;

    /**
     * The percent value upon the arcs that line endpoints should be found
     * when drawing.
     */
    private float mPercent = 1f;


    public CrossView(Context context) {
        super(context);
    }

    public CrossView(Context context, AttributeSet attrs) {
        super(context, attrs);
        readXmlAttributes(context, attrs);
    }

    public CrossView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        readXmlAttributes(context, attrs);
    }

    private void readXmlAttributes(Context context, AttributeSet attrs) {
        // Size will be used for width and height of the icon, plus the space in between
        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.CrossView, 0, 0);
        try {
            mColor = a.getColor(R.styleable.CrossView_lineColor, DEFAULT_COLOR);
        } finally {
            a.recycle();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float[] from = getPointFromPercent(mArcTop, mArcLengthTop, mPercent);
        float[] to = getPointFromPercent(mArcBottom, mArcLengthBottom, mPercent);

        canvas.drawLine(from[0], from[1], to[0], to[1], mPaint);

        from = getPointFromPercent(mArcLeft, mArcLengthLeft, mPercent);
        to = getPointFromPercent(mArcRight, mArcLengthRight, mPercent);

        canvas.drawLine(from[0], from[1], to[0], to[1], mPaint);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (changed) {
            init();
            invalidate();
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable parcelable = super.onSaveInstanceState();
        if (parcelable == null) {
            parcelable = new Bundle();
        }

        AddRemoveSavedState savedState = new AddRemoveSavedState(parcelable);
        savedState.flagState = mState;
        return savedState;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if(!(state instanceof AddRemoveSavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        AddRemoveSavedState ss = (AddRemoveSavedState)state;
        mState = ss.flagState;
        if (mState != FLAG_STATE_PLUS && mState != FLAG_STATE_CROSS) {
            mState = FLAG_STATE_PLUS;
        }

        super.onRestoreInstanceState(ss.getSuperState());
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        init();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        super.setPaddingRelative(start, top, end, bottom);
        init();
    }

    public void setColor(int argb) {
        mColor = argb;
        mPaint.setColor(argb);
        invalidate();
    }


    /**
     * Tell this view to switch states from cross to plus, or back, using the default animation duration.
     * @return an integer flag that represents the new state after toggling.
     *         This will be either {@link #FLAG_STATE_PLUS} or {@link #FLAG_STATE_CROSS}
     */
    public int toggle() {
        return toggle(ANIMATION_DURATION_MS);
    }

    /**
     * Tell this view to switch states from cross to plus, or back.
     * @param animationDurationMS duration in milliseconds for the toggle animation
     * @return an integer flag that represents the new state after toggling.
     *         This will be either {@link #FLAG_STATE_PLUS} or {@link #FLAG_STATE_CROSS}
     */
    public int toggle(long animationDurationMS) {
        mState = mState == FLAG_STATE_PLUS? FLAG_STATE_CROSS : FLAG_STATE_PLUS;
        // invert percent, because state was just flipped
        mPercent = 1 - mPercent;
        ValueAnimator animator = ValueAnimator.ofFloat(mPercent, 1);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setDuration(animationDurationMS);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                setPercent(animation.getAnimatedFraction());
            }
        });

        animator.start();
        return mState;
    }

    /**
     * Transition to "X"
     */
    public void cross() {
        cross(ANIMATION_DURATION_MS);
    }

    /**
     * Transition to "X" over the given animation duration
     * @param animationDurationMS
     */
    public void cross(long animationDurationMS) {
        if (mState == FLAG_STATE_CROSS) {
            return;
        }
        toggle(animationDurationMS);
    }

    /**
     * Transition to "+"
     */
    public void plus() {
        plus(ANIMATION_DURATION_MS);
    }

    /**
     * Transition to "+" over the given animation duration
     */
    public void plus(long animationDurationMS) {
        if (mState == FLAG_STATE_PLUS) {
            return;
        }
        toggle(animationDurationMS);
    }

    private void setPercent(float percent) {
        mPercent = percent;
        invalidate();
    }

    /**
     * Perform measurements and pre-calculations.  This should be called any time
     * the view measurements or visuals are changed, such as with a call to {@link #setPadding(int, int, int, int)}
     * or an operating system callback like {@link #onLayout(boolean, int, int, int, int)}.
     */
    private void init() {
        mRect = new RectF();
        mRect.left = getPaddingLeft();
        mRect.right = getWidth() - getPaddingRight();
        mRect.top = getPaddingTop();
        mRect.bottom = getHeight() - getPaddingBottom();

        mPathMeasure = new PathMeasure();

        mArcTop = new Path();
        mArcTop.addArc(mRect, ARC_TOP_START, ARC_TOP_ANGLE);
        mPathMeasure.setPath(mArcTop, false);
        mArcLengthTop = mPathMeasure.getLength();

        mArcBottom = new Path();
        mArcBottom.addArc(mRect, ARC_BOTTOM_START, ARC_BOTTOM_ANGLE);
        mPathMeasure.setPath(mArcBottom, false);
        mArcLengthBottom = mPathMeasure.getLength();

        mArcLeft = new Path();
        mArcLeft.addArc(mRect, ARC_LEFT_START, ARC_LEFT_ANGLE);
        mPathMeasure.setPath(mArcLeft, false);
        mArcLengthLeft = mPathMeasure.getLength();

        mArcRight = new Path();
        mArcRight.addArc(mRect, ARC_RIGHT_START, ARC_RIGHT_ANGLE);
        mPathMeasure.setPath(mArcRight, false);
        mArcLengthRight = mPathMeasure.getLength();

        mPaint.setAntiAlias(true);
        mPaint.setColor(mColor);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeCap(Paint.Cap.SQUARE);
        mPaint.setStrokeWidth(DEFAULT_STROKE_WIDTH);
    }

    /**
     * Given some path and its length, find the point ([x,y]) on that path at
     * the given percentage of length.
     * @param path any path
     * @param length the length of {@code path}
     * @param percent the percentage along the path's length to find a point
     * @return the point along {@code path} at {@code percent} of its length
     */
    private float[] getPointFromPercent(Path path, float length, float percent) {
        float percentFromState = mState == FLAG_STATE_PLUS ? percent : 1 - percent;
        float[] xy = new float[]{0f, 0f};
        mPathMeasure.setPath(path, false);
        mPathMeasure.getPosTan(length * percentFromState, xy, null);
        return xy;
    }

    /**
     * Internal saved state
     */
    static class AddRemoveSavedState extends BaseSavedState {
        private int flagState;

        AddRemoveSavedState(Parcelable superState) {
            super(superState);
        }

        private AddRemoveSavedState(Parcel in) {
            super(in);
            this.flagState = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(this.flagState);
        }

        public static final Parcelable.Creator<AddRemoveSavedState> CREATOR =
                new Parcelable.Creator<AddRemoveSavedState>() {
                    public AddRemoveSavedState createFromParcel(Parcel in) {
                        return new AddRemoveSavedState(in);
                    }
                    public AddRemoveSavedState[] newArray(int size) {
                        return new AddRemoveSavedState[size];
                    }
                };
    }
}
