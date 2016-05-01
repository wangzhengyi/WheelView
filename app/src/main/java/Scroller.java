import android.content.Context;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.FloatMath;
import android.view.ViewConfiguration;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;

/**
 * 平滑滑动封装类.
 */
public class Scroller {
    /**
     * 默认本次滑动的持续实现.
     */
    private static final int DEFAULT_DURATION = 250;
    /**
     * 滚动模式.
     */
    private static final int SCROLL_MODE = 0;
    /**
     * 抛掷模式.
     */
    private static final int FLING_MODE = 1;
    private static final float INFLEXION = 0.35f; // Tension lines cross at (INFLEXION, 1)
    private static final float START_TENSION = 0.5f;
    private static final float END_TENSION = 1.0f;
    private static final float P1 = START_TENSION * INFLEXION;
    private static final float P2 = 1.0f - END_TENSION * (1.0f - INFLEXION);
    private static final int NB_SAMPLES = 100;
    private static final float[] SPLINE_POSITION = new float[NB_SAMPLES + 1];
    private static final float[] SPLINE_TIME = new float[NB_SAMPLES + 1];
    private static float DECELERATION_RATE = (float) (Math.log(0.78) / Math.log(0.9));

    static {
        float x_min = 0.0f;
        float y_min = 0.0f;
        for (int i = 0; i < NB_SAMPLES; i++) {
            final float alpha = (float) i / NB_SAMPLES;

            float x_max = 1.0f;
            float x, tx, coef;
            while (true) {
                x = x_min + (x_max - x_min) / 2.0f;
                coef = 3.0f * x * (1.0f - x);
                tx = coef * ((1.0f - x) * P1 + x * P2) + x * x * x;
                if (Math.abs(tx - alpha) < 1E-5) break;
                if (tx > alpha) x_max = x;
                else x_min = x;
            }
            SPLINE_POSITION[i] = coef * ((1.0f - x) * START_TENSION + x) + x * x * x;

            float y_max = 1.0f;
            float y, dy;
            while (true) {
                y = y_min + (y_max - y_min) / 2.0f;
                coef = 3.0f * y * (1.0f - y);
                dy = coef * ((1.0f - y) * START_TENSION + y) + y * y * y;
                if (Math.abs(dy - alpha) < 1E-5) break;
                if (dy > alpha) y_max = y;
                else y_min = y;
            }
            SPLINE_TIME[i] = coef * ((1.0f - y) * P1 + y * P2) + y * y * y;
        }
        SPLINE_POSITION[NB_SAMPLES] = SPLINE_TIME[NB_SAMPLES] = 1.0f;
    }

    private final Interpolator mInterpolator;
    private final float mPpi;
    /**
     * 滚动模式,包括SCROLL_MODE和FLING_MODE.
     * 对应的方法分别为:startScroll和fling
     */
    private int mMode;
    /**
     * x轴方向起始坐标.
     */
    private int mStartX;
    /**
     * y轴方向起始坐标.
     */
    private int mStartY;
    /**
     * x轴方向滑动终止点的x轴坐标.
     */
    private int mFinalX;
    /**
     * y轴方向滑动终止点的y轴坐标.
     */
    private int mFinalY;
    private int mMinX;
    private int mMaxX;
    private int mMinY;
    private int mMaxY;
    /**
     * 当前view应该滑动到的x轴坐标.
     */
    private int mCurrX;
    /**
     * 当前view应该滑动到的y轴坐标.
     */
    private int mCurrY;
    /**
     * View滑动的起始时间.
     */
    private long mStartTime;
    /**
     * 滑动过程的总时间.
     */
    private int mDuration;
    private float mDurationReciprocal;
    private float mDeltaX;
    private float mDeltaY;
    private boolean mFinished;
    private boolean mFlywheel;
    private float mVelocity;
    private float mCurrVelocity;
    private int mDistance;
    private float mFlingFriction = ViewConfiguration.getScrollFriction();
    private float mDeceleration;
    // A context-specific coefficient adjusted to physical values.
    private float mPhysicalCoeff;

    /**
     * 使用默认的滑动时间和插值器构造Scroller.
     */
    public Scroller(Context context) {
        this(context, null);
    }

    /**
     * 使用给定的插值器来构造Scroller.
     */
    public Scroller(Context context, Interpolator interpolator) {
        this(context, interpolator,
                context.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.HONEYCOMB);
    }

    /**
     * 使用给定的插值器来构造Scroller.Android3.0以上的版本支持"flywheel"的行为.
     */
    public Scroller(Context context, Interpolator interpolator, boolean flywheel) {
        mFinished = true;
        if (interpolator == null) {
            mInterpolator = new ViscousFluidInterpolator();
        } else {
            mInterpolator = interpolator;
        }
        mPpi = context.getResources().getDisplayMetrics().density * 160.0f;
        mDeceleration = computeDeceleration(ViewConfiguration.getScrollFriction());
        mFlywheel = flywheel;

        mPhysicalCoeff = computeDeceleration(0.84f); // look and feel tuning
    }

    /**
     * The amount of friction applied to flings. The default value
     * is {@link ViewConfiguration#getScrollFriction}.
     *
     * @param friction A scalar dimension-less value representing the coefficient of
     *                 friction.
     */
    public final void setFriction(float friction) {
        mDeceleration = computeDeceleration(friction);
        mFlingFriction = friction;
    }

    private float computeDeceleration(float friction) {
        return SensorManager.GRAVITY_EARTH   // g (m/s^2)
                * 39.37f               // inch/meter
                * mPpi                 // pixels per inch
                * friction;
    }

    /**
     * 当前View滑动是否已经停止.
     */
    public final boolean isFinished() {
        return mFinished;
    }

    /**
     * 设置强制停止标志位.强制停止,则computeScrollOffset不再计算后续移动位置.
     */
    public final void forceFinished(boolean finished) {
        mFinished = finished;
    }

    /**
     * 返回滑动过程的时间.
     */
    public final int getDuration() {
        return mDuration;
    }

    /**
     * 返回当前View在x轴应该滑动到的坐标.
     */
    public final int getCurrX() {
        return mCurrX;
    }

    /**
     * 返回当前View在y轴应该滑动到的坐标.
     */
    public final int getCurrY() {
        return mCurrY;
    }

    /**
     * 获取当前的滑行速率.
     */
    public float getCurrVelocity() {
        return mMode == FLING_MODE ?
                mCurrVelocity : mVelocity - mDeceleration * timePassed() / 2000.0f;
    }

    /**
     * 返回滑动起始点的x轴坐标.
     */
    public final int getStartX() {
        return mStartX;
    }

    /**
     * 返回滑动起始点的y轴坐标.
     */
    public final int getStartY() {
        return mStartY;
    }

    /**
     * View滑动的x轴终点坐标.
     */
    public final int getFinalX() {
        return mFinalX;
    }

    /**
     * Sets the final position (X) for this scroller.
     *
     * @param newX The new X offset as an absolute distance from the origin.
     * @see #extendDuration(int)
     * @see #setFinalY(int)
     */
    public void setFinalX(int newX) {
        mFinalX = newX;
        mDeltaX = mFinalX - mStartX;
        mFinished = false;
    }

    /**
     * View滑动的y轴终点坐标.
     */
    public final int getFinalY() {
        return mFinalY;
    }

    /**
     * Sets the final position (Y) for this scroller.
     *
     * @param newY The new Y offset as an absolute distance from the origin.
     * @see #extendDuration(int)
     * @see #setFinalX(int)
     */
    public void setFinalY(int newY) {
        mFinalY = newY;
        mDeltaY = mFinalY - mStartY;
        mFinished = false;
    }

    /**
     * Call this when you want to know the new location.  If it returns true,
     * the animation is not yet finished.
     */
    public boolean computeScrollOffset() {
        if (mFinished) {
            return false;
        }

        int timePassed = (int) (AnimationUtils.currentAnimationTimeMillis() - mStartTime);

        if (timePassed < mDuration) {
            switch (mMode) {
                case SCROLL_MODE:
                    final float x = mInterpolator.getInterpolation(timePassed * mDurationReciprocal);
                    mCurrX = mStartX + Math.round(x * mDeltaX);
                    mCurrY = mStartY + Math.round(x * mDeltaY);
                    break;
                case FLING_MODE:
                    final float t = (float) timePassed / mDuration;
                    final int index = (int) (NB_SAMPLES * t);
                    float distanceCoef = 1.f;
                    float velocityCoef = 0.f;
                    if (index < NB_SAMPLES) {
                        final float t_inf = (float) index / NB_SAMPLES;
                        final float t_sup = (float) (index + 1) / NB_SAMPLES;
                        final float d_inf = SPLINE_POSITION[index];
                        final float d_sup = SPLINE_POSITION[index + 1];
                        velocityCoef = (d_sup - d_inf) / (t_sup - t_inf);
                        distanceCoef = d_inf + (t - t_inf) * velocityCoef;
                    }

                    mCurrVelocity = velocityCoef * mDistance / mDuration * 1000.0f;

                    mCurrX = mStartX + Math.round(distanceCoef * (mFinalX - mStartX));
                    // Pin to mMinX <= mCurrX <= mMaxX
                    mCurrX = Math.min(mCurrX, mMaxX);
                    mCurrX = Math.max(mCurrX, mMinX);

                    mCurrY = mStartY + Math.round(distanceCoef * (mFinalY - mStartY));
                    // Pin to mMinY <= mCurrY <= mMaxY
                    mCurrY = Math.min(mCurrY, mMaxY);
                    mCurrY = Math.max(mCurrY, mMinY);

                    if (mCurrX == mFinalX && mCurrY == mFinalY) {
                        mFinished = true;
                    }

                    break;
            }
        } else {
            mCurrX = mFinalX;
            mCurrY = mFinalY;
            mFinished = true;
        }
        return true;
    }

    /**
     * 给定滚动起始点坐标,在指定的时间内滚动指定的偏移量.
     * 距离计算:
     * dx=view左边缘-view内容左边缘;dx为正,代表内容向左移动;dx为负,代表内容向右移动.
     * dy=view上边缘-view内容上边缘;dy为正,代表内容向上移动;dx为负,代表内容向下移动.
     *
     * @param startX   x轴方向滚动起始点坐标.
     * @param startY   y轴方向滚动起始点坐标.
     * @param dx       x轴方向滚动距离.
     * @param dy       y轴方向滚动距离.
     * @param duration 滚动持续的时间(默认滚动时间为250ms).
     */
    public void startScroll(int startX, int startY, int dx, int dy, int duration) {
        mMode = SCROLL_MODE;
        mFinished = false;
        mDuration = duration;
        mStartTime = AnimationUtils.currentAnimationTimeMillis();
        mStartX = startX;
        mStartY = startY;
        mFinalX = startX + dx;
        mFinalY = startY + dy;
        mDeltaX = dx;
        mDeltaY = dy;
        mDurationReciprocal = 1.0f / (float) mDuration;
    }

    /**
     * Start scrolling based on a fling gesture. The distance travelled will
     * depend on the initial velocity of the fling.
     *
     * @param startX    Starting point of the scroll (X)
     * @param startY    Starting point of the scroll (Y)
     * @param velocityX Initial velocity of the fling (X) measured in pixels per
     *                  second.
     * @param velocityY Initial velocity of the fling (Y) measured in pixels per
     *                  second
     * @param minX      Minimum X value. The scroller will not scroll past this
     *                  point.
     * @param maxX      Maximum X value. The scroller will not scroll past this
     *                  point.
     * @param minY      Minimum Y value. The scroller will not scroll past this
     *                  point.
     * @param maxY      Maximum Y value. The scroller will not scroll past this
     *                  point.
     */
    public void fling(int startX, int startY, int velocityX, int velocityY,
                      int minX, int maxX, int minY, int maxY) {
        // Continue a scroll or fling in progress
        if (mFlywheel && !mFinished) {
            float oldVel = getCurrVelocity();

            float dx = (float) (mFinalX - mStartX);
            float dy = (float) (mFinalY - mStartY);
            float hyp = (float) Math.sqrt(dx * dx + dy * dy);

            float ndx = dx / hyp;
            float ndy = dy / hyp;

            float oldVelocityX = ndx * oldVel;
            float oldVelocityY = ndy * oldVel;
            if (Math.signum(velocityX) == Math.signum(oldVelocityX) &&
                    Math.signum(velocityY) == Math.signum(oldVelocityY)) {
                velocityX += oldVelocityX;
                velocityY += oldVelocityY;
            }
        }

        mMode = FLING_MODE;
        mFinished = false;

        float velocity = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);

        mVelocity = velocity;
        mDuration = getSplineFlingDuration(velocity);
        mStartTime = AnimationUtils.currentAnimationTimeMillis();
        mStartX = startX;
        mStartY = startY;

        float coeffX = velocity == 0 ? 1.0f : velocityX / velocity;
        float coeffY = velocity == 0 ? 1.0f : velocityY / velocity;

        double totalDistance = getSplineFlingDistance(velocity);
        mDistance = (int) (totalDistance * Math.signum(velocity));

        mMinX = minX;
        mMaxX = maxX;
        mMinY = minY;
        mMaxY = maxY;

        mFinalX = startX + (int) Math.round(totalDistance * coeffX);
        // Pin to mMinX <= mFinalX <= mMaxX
        mFinalX = Math.min(mFinalX, mMaxX);
        mFinalX = Math.max(mFinalX, mMinX);

        mFinalY = startY + (int) Math.round(totalDistance * coeffY);
        // Pin to mMinY <= mFinalY <= mMaxY
        mFinalY = Math.min(mFinalY, mMaxY);
        mFinalY = Math.max(mFinalY, mMinY);
    }

    private double getSplineDeceleration(float velocity) {
        return Math.log(INFLEXION * Math.abs(velocity) / (mFlingFriction * mPhysicalCoeff));
    }

    private int getSplineFlingDuration(float velocity) {
        final double l = getSplineDeceleration(velocity);
        final double decelMinusOne = DECELERATION_RATE - 1.0;
        return (int) (1000.0 * Math.exp(l / decelMinusOne));
    }

    private double getSplineFlingDistance(float velocity) {
        final double l = getSplineDeceleration(velocity);
        final double decelMinusOne = DECELERATION_RATE - 1.0;
        return mFlingFriction * mPhysicalCoeff * Math.exp(DECELERATION_RATE / decelMinusOne * l);
    }

    /**
     * Stops the animation. Contrary to {@link #forceFinished(boolean)},
     * aborting the animating cause the scroller to move to the final x and y
     * position
     *
     * @see #forceFinished(boolean)
     */
    public void abortAnimation() {
        mCurrX = mFinalX;
        mCurrY = mFinalY;
        mFinished = true;
    }

    /**
     * Extend the scroll animation. This allows a running animation to scroll
     * further and longer, when used with {@link #setFinalX(int)} or {@link #setFinalY(int)}.
     *
     * @param extend Additional time to scroll in milliseconds.
     * @see #setFinalX(int)
     * @see #setFinalY(int)
     */
    public void extendDuration(int extend) {
        int passed = timePassed();
        mDuration = passed + extend;
        mDurationReciprocal = 1.0f / mDuration;
        mFinished = false;
    }

    /**
     * Returns the time elapsed since the beginning of the scrolling.
     *
     * @return The elapsed time in milliseconds.
     */
    public int timePassed() {
        return (int) (AnimationUtils.currentAnimationTimeMillis() - mStartTime);
    }

    /**
     * @hide
     */
    public boolean isScrollingInDirection(float xvel, float yvel) {
        return !mFinished && Math.signum(xvel) == Math.signum(mFinalX - mStartX) &&
                Math.signum(yvel) == Math.signum(mFinalY - mStartY);
    }

    static class ViscousFluidInterpolator implements Interpolator {
        /**
         * Controls the viscous fluid effect (how much of it).
         */
        private static final float VISCOUS_FLUID_SCALE = 8.0f;

        private static final float VISCOUS_FLUID_NORMALIZE;
        private static final float VISCOUS_FLUID_OFFSET;

        static {

            // must be set to 1.0 (used in viscousFluid())
            VISCOUS_FLUID_NORMALIZE = 1.0f / viscousFluid(1.0f);
            // account for very small floating-point error
            VISCOUS_FLUID_OFFSET = 1.0f - VISCOUS_FLUID_NORMALIZE * viscousFluid(1.0f);
        }

        private static float viscousFluid(float x) {
            x *= VISCOUS_FLUID_SCALE;
            if (x < 1.0f) {
                x -= (1.0f - (float) Math.exp(-x));
            } else {
                float start = 0.36787944117f;   // 1/e == exp(-1)
                x = 1.0f - (float) Math.exp(1.0f - x);
                x = start + x * (1.0f - start);
            }
            return x;
        }

        @Override
        public float getInterpolation(float input) {
            final float interpolated = VISCOUS_FLUID_NORMALIZE * viscousFluid(input);
            if (interpolated > 0) {
                return interpolated + VISCOUS_FLUID_OFFSET;
            }
            return interpolated;

        }
    }
}
