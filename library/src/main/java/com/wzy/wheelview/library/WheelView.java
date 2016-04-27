package com.wzy.wheelview.library;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Scroller;

import java.util.List;

@SuppressWarnings("unused")
public class WheelView extends View {
    /**
     * Default center text size (px).
     */
    private static final float DEFAULT_CENTER_TEXT_SIZE = 60;

    /**
     * Default two side text size (px).
     */
    private static final float DEFAULT_SIDE_TEXT_SIZE = 22;

    /**
     * Default center text color.
     */
    private static final int DEFAULT_CENTER_TEXT_COLOR = Color.parseColor("#ffffff");

    /**
     * Default two side text color.
     */
    private static final int DEFAULT_SIDE_TEXT_COLOR = Color.parseColor("#808080");

    /**
     * Default center text margin top text distance.
     */
    private static final float DEFAULT_CENTER_MARGIN_TOP = 16;

    /**
     * Default center text margin bottom text distance.
     */
    private static final float DEFAULT_CENTER_MARGIN_BOTTOM = 18;

    /**
     * Default scrolling duration.
     */
    private static final int SCROLLING_DURATION = 400;

    /**
     * Min delta for scroll.
     */
    private static final int MIN_DELTA_FOR_SCROLLING = 1;

    /**
     * Handler message for content scroll.
     */
    private static final int MESSAGE_SCROLL = 0;

    /**
     * Handler message for scroll justify.
     */
    private static final int MESSAGE_JUSTIFY = 1;

    /**
     * Center text paint.
     */
    private Paint mCenterPaint;

    /**
     * Center text color.
     */
    private int mCenterTextColor;

    /**
     * Center text size.
     */
    private float mCenterTextSize;

    /**
     * The distance between center text and side text.
     */
    private float mCenterMarginTop, mCenterMarginBottom;

    /**
     * Bottom text paint.
     */
    private Paint mBottomTextPaint;

    /**
     * Bottom text color.
     */
    private int mBottomTextColor;

    /**
     * Bottom text size.
     */
    private float mBottomTextSize;

    /**
     * Top scale content text paint.
     */
    private Paint mTopScaleTextPaint;

    /**
     * Top text color.
     */
    private int mTopScaleTextColor;

    /**
     * Top text size.
     */
    private float mTopScaleTextSize;

    /**
     * Center coordinate of view.
     */
    private float mCenterX, mCenterY;

    /**
     * Display contents.
     */
    private List<String> mItemList;

    /**
     * Current selected position.
     */
    private int mSelectedPosition;

    /**
     * Scale Content, e.g. Hour or Minute.
     */
    private String mScaleTextContent;

    /**
     * 记录手指按下的距离
     */
    private float mLastDownY;
    /**
     * Touch事件需要改变的高度.
     */
    private int mTouchChangeHeight;
    /**
     * 文字的Align
     */
    private Paint.Align mPaintAlign;
    /**
     * 是否呈现Bottom文本
     */
    private boolean mIsShowBottomText;
    /**
     * 手势检测器
     */
    private GestureDetector mGestureDetector;
    /**
     * Scroller类封装滚动操作.
     */
    private Scroller mScroller;

    /**
     * view scrolling state.
     */
    private boolean isScrollingPerformed;

    /**
     * 先前y轴所在的位置.
     */
    private int mLastScrollY;

    /**
     * View scrolling offset.
     */
    private int mScrollingOffset;

    /**
     * Is view scroll cyclic.
     * TODO:add set function
     */
    private boolean isCyclic = false;

    /**
     * Scroller handler
     */
    @SuppressLint("HandlerLeak")
    private Handler mAnimationHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // 回调该方法获取当前位置,如果返回true,说明动画还没有执行完毕
            mScroller.computeScrollOffset();
            // 获取当前y位置
            int currY = mScroller.getCurrY();
            // 获取已经滚动的位置,使用上一次位置减去当前位置
            int delta = mLastScrollY - currY;
            mLastScrollY = currY;
            if (delta != 0) {
                // 改变值不为0,继续滚动
                doScroll(delta);
            }

            /**
             * 如果滚动到了指定的位置,滚动还没有停止
             * 这时需要强制停止
             */
            if (Math.abs(currY - mScroller.getFinalY()) < MIN_DELTA_FOR_SCROLLING) {
                mScroller.forceFinished(true);
            }

            /**
             * 如果滚动没有停止
             * 再向Handler发送一个停止
             */
            if (!mScroller.isFinished()) {
                mAnimationHandler.sendEmptyMessage(msg.what);
            } else if (msg.what == MESSAGE_SCROLL) {
                justify();
            } else { // MESSAGE_JUSTIFY
                finishScrolling();
            }
        }
    };
    private GestureDetector.SimpleOnGestureListener mGestureListener =
            new GestureDetector.SimpleOnGestureListener() {
                /**
                 * 按下操作.
                 */
                @Override
                public boolean onDown(MotionEvent e) {
                    // 如果滚动在执行
                    if (isScrollingPerformed) {
                        // 滚动强制停止,按下的时候不能继续滚动
                        mScroller.forceFinished(true);
                        // 清理信息
                        clearMessages();
                        return true;
                    }
                    return false;
                }

                /**
                 * 手势监听器监听到滚动操作后的回调.
                 * @param e1 触发滚动时第一次按下的事件.
                 * @param e2 触发滚动时的移动事件.
                 * @param distanceX 从上一次调用该方法到这一次x轴滚动的距离.
                 * @param distanceY 从上一次调用该方法到这一次y轴滚动的距离.
                 * @return 事件触发成功, 执行完方法中的操作, 返回true;否则,返回false.
                 */
                @Override
                public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                                        float distanceY) {
                    startScrolling();
                    doScroll((int) distanceY);
                    return true;
                }

                /**
                 * 当一个急冲手势发生后回调该方法,会计算出该手势在x轴和y轴的速率.
                 * @param e1 急冲动作的第一次触摸事件.
                 * @param e2 急冲动作的移动发生的时候的触摸事件.
                 * @param velocityX x轴的速率.
                 * @param velocityY y轴的速率.
                 * @return 执行完毕返回true, 执行失败返回false.
                 */
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                                       float velocityY) {
                    // 计算上一次的y轴位置,当前条目高度加上剩余的不够一行高度的那部分
                    mLastScrollY = mSelectedPosition * getItemHeight() + mScrollingOffset;

                    // 如果可以循环最大值是无限大,不能循环就是条目数的高度值
                    int maxY = isCyclic ? 0x7FFFFFFF : mItemList.size() * getItemHeight();
                    int minY = isCyclic ? -maxY : 0;

                    /**
                     * Scroll 开始根据一个急冲手势滚动,滚动距离与初速度相关
                     * 参数介绍：
                     * int startX：开始时的x轴位置
                     * int startY：开始时的y轴位置
                     * int velocityX：急冲手势的x轴的初速度,单位px/s
                     * int velocityY：急冲手势的y轴的初速度,单位px/s
                     * int minX：x轴滚动的最小值
                     * int maxX：x轴滚动的最大值
                     * int minY：y轴滚动的最小值
                     * int maxY：y轴滚动的最大值
                     */
                    mScroller.fling(0, mLastScrollY, 0, (int) velocityY / 2, 0, 0, minY, maxY);
                    setNextMessage(MESSAGE_SCROLL);
                    return true;
                }
            };

    public WheelView(Context context) {
        this(context, null);
    }

    public WheelView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WheelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        final TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.WheelView);
        mCenterTextColor = ta.getColor(
                R.styleable.WheelView_center_text_color, DEFAULT_CENTER_TEXT_COLOR);
        mCenterTextSize = ta.getDimension(R.styleable.WheelView_center_text_size,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_CENTER_TEXT_SIZE,
                        getResources().getDisplayMetrics()));
        mTopScaleTextColor = ta.getColor(
                R.styleable.WheelView_top_text_color, DEFAULT_SIDE_TEXT_COLOR);
        mTopScaleTextSize = ta.getDimension(R.styleable.WheelView_top_text_size,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_SIDE_TEXT_SIZE,
                        getResources().getDisplayMetrics()));
        mBottomTextColor = ta.getColor(
                R.styleable.WheelView_btm_text_color, DEFAULT_SIDE_TEXT_COLOR);
        mBottomTextSize = ta.getDimension(R.styleable.WheelView_btm_text_size,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, DEFAULT_SIDE_TEXT_SIZE,
                        getResources().getDisplayMetrics()));
        mCenterMarginTop = ta.getDimension(R.styleable.WheelView_center_margin_top,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, DEFAULT_CENTER_MARGIN_TOP,
                        getResources().getDisplayMetrics()));
        mCenterMarginBottom = ta.getDimension(R.styleable.WheelView_center_margin_bottom,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, DEFAULT_CENTER_MARGIN_BOTTOM,
                        getResources().getDisplayMetrics()));
        mIsShowBottomText = ta.getBoolean(R.styleable.WheelView_show_bottom_text, false);
        int paintAlign = ta.getInt(R.styleable.WheelView_paint_align, 1);
        initPaintAlign(paintAlign);
        ta.recycle();

        initPaint();
        initScroll(context);
    }

    private void initPaintAlign(int paintAlign) {
        switch (paintAlign) {
            case 0:
                mPaintAlign = Paint.Align.LEFT;
                break;
            case 1:
                mPaintAlign = Paint.Align.CENTER;
                break;
            case 2:
                mPaintAlign = Paint.Align.RIGHT;
                break;
            default:
                mPaintAlign = Paint.Align.CENTER;
                break;
        }
    }

    private void initPaint() {
        mCenterPaint = createTextPaint(mCenterTextSize, mCenterTextColor);
        mBottomTextPaint = createTextPaint(mBottomTextSize, mBottomTextColor);
        mTopScaleTextPaint = createScalePaint(mTopScaleTextSize, mTopScaleTextColor);

        initTouchChangeHeight();
    }

    /**
     * Create text paint depend on text size and text color.
     *
     * @param textSize text size
     * @param color    text color
     * @return text paint
     */
    private Paint createTextPaint(float textSize, int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        paint.setColor(color);
        paint.setTextAlign(mPaintAlign);
        paint.setStyle(Paint.Style.FILL);
        return paint;
    }

    /**
     * Create scale text paint depend on center text paint align.
     * center text align == CENTER, scale text align = CENTER
     * center text align == LEFT, scale text align = RIGHT.
     * center text align == RIGHT, scale text align = LEFT.
     *
     * @param textSize scale text size
     * @param color    scale text color
     * @return scale text paint
     */
    private Paint createScalePaint(float textSize, int color) {
        Paint.Align scalePaintAlign = Paint.Align.CENTER;
        if (mPaintAlign == Paint.Align.LEFT) {
            scalePaintAlign = Paint.Align.RIGHT;
        } else if (mPaintAlign == Paint.Align.RIGHT) {
            scalePaintAlign = Paint.Align.LEFT;
        }

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        paint.setColor(color);
        paint.setTextAlign(scalePaintAlign);
        paint.setStyle(Paint.Style.FILL);
        return paint;
    }

    private void initTouchChangeHeight() {
        Paint.FontMetricsInt fmi = mCenterPaint.getFontMetricsInt();
        mTouchChangeHeight = fmi.ascent * -1;
    }

    private void initScroll(Context context) {
        mGestureDetector = new GestureDetector(context, mGestureListener);
        // Disable long press event.
        mGestureDetector.setIsLongpressEnabled(false);

        mScroller = new Scroller(context);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int viewWidth = getMeasuredWidth();
        int viewHeight = getMeasuredHeight();

        mCenterX = (float) (viewWidth / 2.0);
        mCenterY = (float) (viewHeight / 2.0);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mItemList != null && !mItemList.isEmpty()) {
            drawCenterText(canvas);
            drawScaleText(canvas);
            if (mIsShowBottomText) {
                drawSideText(canvas);
            }
        }
    }

    private void drawSideText(Canvas canvas) {
        int nextItem = (mSelectedPosition + 1) % mItemList.size();
        float baseLineY = calculateBaseLineForBottom();
        canvas.drawText(mItemList.get(nextItem), mCenterX, baseLineY, mBottomTextPaint);
    }

    private void drawScaleText(Canvas canvas) {
        if (!TextUtils.isEmpty(mScaleTextContent)) {
            float baseLineY = calculateBaseLineForTop();
            float textWidth = calculateCenterTextWidth();
            float x = mCenterX;
            if (mTopScaleTextPaint.getTextAlign() == Paint.Align.LEFT) {
                x = mCenterX - textWidth;
            } else if (mTopScaleTextPaint.getTextAlign() == Paint.Align.RIGHT) {
                x = mCenterX + textWidth;
            }

            canvas.drawText(mScaleTextContent, x, baseLineY, mTopScaleTextPaint);
        }
    }

    private float calculateCenterTextWidth() {
        String centerContent = mItemList.get(mSelectedPosition);
        return mCenterPaint.measureText(centerContent);
    }

    private void drawCenterText(Canvas canvas) {
        float baseLineY = calculateBaseLineForCenter();
        canvas.drawText(mItemList.get(mSelectedPosition), mCenterX, baseLineY, mCenterPaint);
    }

    private float calculateBaseLineForCenter() {
        // ascent = ascent线的y坐标 - baseline线的y坐标
        // descent = descent线的y坐标 - baseline线的y坐标
        // top = top线的y坐标 - baseline线的y坐标
        // bottom = bottom线的y坐标 - baseline线的y坐标
        Paint.FontMetricsInt fmi = mCenterPaint.getFontMetricsInt();
        float centerY = mCenterY;
        return (fmi.descent - fmi.ascent) / 2.0F - fmi.descent + centerY;
    }

    private float calculateBaseLineForBottom() {
        // 获取中心文字的下边界
        Paint.FontMetricsInt fmi = mCenterPaint.getFontMetricsInt();
        int centerTextHeight = fmi.bottom - fmi.top;
        float centerY = mCenterY;
        float centerTextBottomY = centerY + centerTextHeight / 2.0F;

        // 获取下方文字baseline距离top的大小
        Paint.FontMetricsInt bFmi = mBottomTextPaint.getFontMetricsInt();
        return (centerTextBottomY + mCenterMarginBottom - bFmi.top);
    }

    private float calculateBaseLineForTop() {
        // 获取中心文字的上边界
        Paint.FontMetricsInt fmi = mCenterPaint.getFontMetricsInt();
        int centerTextHeight = fmi.bottom - fmi.top;
        float centerY = mCenterY;
        float centerTextTopY = centerY - centerTextHeight / 2.0F;

        // 获取上方文字baseline距离bottom的大小
        Paint.FontMetricsInt tFmi = mBottomTextPaint.getFontMetricsInt();
        return (centerTextTopY - mCenterMarginTop - tFmi.bottom);
    }

    public void setItemListAndScaleContent(List<String> list, String content) {
        mItemList = list;
        mScaleTextContent = content;
        if (mItemList != null) {
            resetCurrentSelect();
        }
    }

    public int getSelectedPosition() {
        return mSelectedPosition;
    }

    public void setSelectedPosition(int position) {
        mSelectedPosition = position;
        resetCurrentSelect();
    }

    private void resetCurrentSelect() {
        if (mSelectedPosition < 0) {
            mSelectedPosition = 0;
        }

        if (mSelectedPosition >= mItemList.size()) {
            mSelectedPosition = mItemList.size() - 1;
        }

        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mItemList.size() > 0) {
            if (!mGestureDetector.onTouchEvent(event) && event.getAction() == MotionEvent.ACTION_UP) {
                justify();
            }
        }

        return true;
    }

    private void doScroll(int delta) {
        mScrollingOffset += delta;

        // 计算滚动的条目数,使滚动的值处于单个条目高度,注意计算整数值
        int count = mScrollingOffset / getItemHeight();

        // pos是滚动后的目标元素索引,计算滚动后位置,当前条目数加上滚动的条目数
        int pos = mSelectedPosition + count;

        if (isCyclic) { // 循环滑动
            while (pos < 0) {
                pos += mItemList.size();
            }
            // 如果位置正无限大,模条目数
            pos %= mItemList.size();
        } else if (isScrollingPerformed) { // 如果不可循环且滚动正在执行
            if (pos < 0) {
                pos = 0;
            } else if (pos >= mItemList.size()) {
                pos = mItemList.size() - 1;
            }
        } else {
            // fix position
            pos = Math.max(pos, 0);
            pos = Math.min(pos, mItemList.size() - 1);
        }

        // 滚动高度
        int offset = mScrollingOffset;

        /**
         * 如果当前位置不是滚动后的目标位置,就将当前位置设置为目标位置.
         * 否则,就重绘组件.
         */
        if (pos != mSelectedPosition) {
            setSelectedPosition(pos);
        } else {
            // 重绘组件
            invalidate();
        }

        // 将滚动后剩余的小树部分保存
        mScrollingOffset = offset - count * getItemHeight();
        if (mScrollingOffset > getHeight()) {
            mScrollingOffset = mScrollingOffset % getHeight() + getHeight();
        }
    }

    /**
     * 清空之前的Handler队列,发送下一个消息到Handler中.
     */
    private void setNextMessage(int message) {
        clearMessages();
        mAnimationHandler.sendEmptyMessage(message);
    }

    /**
     * 清空队列中的信息
     */
    private void clearMessages() {
        // 删除Handler执行队列中的滚动操作
        mAnimationHandler.removeMessages(MESSAGE_SCROLL);
        mAnimationHandler.removeMessages(MESSAGE_JUSTIFY);
    }

    private void justify() {
        if (mItemList.size() <= 0) {
            return;
        }

        // 补偿之前将Y轴位置设置为0,代表当前已经静止.
        mLastScrollY = 0;
        int offset = mScrollingOffset;
        int itemHeight = getItemHeight();

        /**
         * 当前滚动补偿大于0,说明还有没有滚动的部分,needToIncrease是当前条目是否小于条目数
         * 如果当前滚动补偿不大于0,needToIncrease是当前条目是否大于0
         */
        boolean needToIncrease = offset > 0 ? mSelectedPosition < mItemList.size() - 1
                : mSelectedPosition > 0;
        if ((isCyclic || needToIncrease) && Math.abs((float) offset) > (float) itemHeight * 4 / 5) {
            if (offset < 0) {
                offset -= itemHeight + MIN_DELTA_FOR_SCROLLING;
            } else {
                offset += itemHeight + MIN_DELTA_FOR_SCROLLING;
            }
        }
        if (Math.abs(offset) > MIN_DELTA_FOR_SCROLLING) {
            mScroller.startScroll(0, 0, 0, offset * -1, SCROLLING_DURATION);
            setNextMessage(MESSAGE_JUSTIFY);
        } else {
            finishScrolling();
        }
    }

    /**
     * 开始滚动.
     */
    private void startScrolling() {
        if (!isScrollingPerformed) {
            isScrollingPerformed = true;
        }
    }

    /**
     * 结束滚动
     */
    private void finishScrolling() {
        if (isScrollingPerformed) {
            isScrollingPerformed = false;
        }

        // 重置偏移量
        mScrollingOffset = 0;
    }

    private int getItemHeight() {
        return mTouchChangeHeight;
    }
}
