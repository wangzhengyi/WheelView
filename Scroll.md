# View弹性滑动详解

之前写了一个滚动选择控件![WheelView](https://github.com/wangzhengyi/WheelView),在这个控件中我设计了弹性滚动的实现机制,这里将实现方法和原理性的东西分享给大家.
再了解View弹性滚动之前,我们先来学习一下View滚动机制的实现.

-----
# View的scrollTo/scrollBy

这里基于Android5.0版本的源码介绍View类中这两个函数的具体实现.

scrollTo源码如下:
```java
/**
 * 对View设置滚动的x和y轴坐标.
 * @param x x轴滚动的终点坐标
 * @param y y轴滚动的终点坐标
 */
public void scrollTo(int x, int y) {
    if (mScrollX != x || mScrollY != y) {
        int oldX = mScrollX;
        int oldY = mScrollY;
        mScrollX = x;
        mScrollY = y;
        invalidateParentCaches();
        onScrollChanged(mScrollX, mScrollY, oldX, oldY);
        if (!awakenScrollBars()) {
            postInvalidateOnAnimation();
        }
    }
}
```
scrollBy源码如下:
```java
/**
 * 设置View的x轴和y轴的滚动增量.
 * @param x x轴的滚动增量
 * @param y y轴的滚动增量
 */
public void scrollBy(int x, int y) {
    scrollTo(mScrollX + x, mScrollY + y);
}
```

从上述源码可以看出,scrollBy依赖于scrollTo的实现.他俩的区别是:
> scrollBy的x和y是滚动增量,即在上次滚动的终点坐标上增加x和y,是相对滑动.(注意:x和y可能为负数)
> scrollTo的x和y是滚动的终点坐标,是绝对滑动.

而且,scrollTo的实现也仅仅是修改了mScrollX和mScrollY的值,然后调用了invalidate方法重绘了View.那么,mScrollX和mScrollY的含义是什么呢?

* mScrollX:View的左边缘和View内容左边缘在x轴上的距离,即View左边缘x轴坐标-View内容左边缘的x轴坐标.
* mScrollY:View的上边缘和View内容上边缘在y轴上的距离,即View上边缘y轴坐标-View内容上边缘的y轴坐标.

同时,需要明确很重要的一点:**View的滑动并非是View的滑动,而是View内容的滑动**.

提供一个图示来理解View的滑动:

![view_scroll](https://github.com/wangzhengyi/WheelView/raw/master/screenshots/view_scroll.png)

缺陷:
>虽然调用View的scrollBy和scrollTo方法可以很方便的实现View的滚动,但是这种滚动是瞬间完成的(调用invalidate方法),没有弹性滑动的效果,为了达到弹性滑动的目的,我们开始介绍本篇文章的主角:Scroller.

-----
# Scroller

在介绍Scroller之前,我们需要明确知道:
**Scroller代码和View代码完全解耦,Scroller代码本身不会引起View的滑动,通过Scroller代码,我们可以平滑的获取当前View需要滑动的位置,然后调用View的scrollTo/scrollBy进行移动**.

## 构造函数

我们先来看一下Scroller的构造函数注释源码:
```java
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
    // 设置滑动停止标识位为true
    mFinished = true;
    // 构造插值器
    if (interpolator == null) {
        mInterpolator = new ViscousFluidInterpolator();
    } else {
        mInterpolator = interpolator;
    }

    // 获取屏幕的密度(每英寸的像素数)
    mPpi = context.getResources().getDisplayMetrics().density * 160.0f;
    // 计算摩擦力
    mDeceleration = computeDeceleration(ViewConfiguration.getScrollFriction());
    // 标记是否支持flying模式
    mFlywheel = flywheel;

    mPhysicalCoeff = computeDeceleration(0.84f); // look and feel tuning
}
```

## 两种模式

Scroller支持两种模式的滑动,分别是:

* SCROLL_MODE:调用startScroll,正常滚动模式.
* FLING_MODE:调用fling,抛掷模式.

接下来,针对这两种模式进行分别讲解.

### SCROLL_MODE

我们直接看一下startScroll的源码做了哪些事情:
```java
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
    // 持续时间的倒数,用来得到的插值器返回的值
    mDurationReciprocal = 1.0f / (float) mDuration;
}
```

通过注释的源码,我们可以验证最初的结论:Scroller和View完全解耦,Scroller并不会直接控制View的滑动,它只是为View提供滑动的参数.具体参数包括:

* mMode: 设置为滑动模式.
* mFinished: 设置滑动结束标识为false.
* mDuration: 设置滑动时间间隔.
* mStartTime: 设置滑动的起始时间.
* mStartX: 设置x轴的起始点坐标.
* mStartY: 设置Y轴的起始点坐标
* mFinalX: 设置x轴的终点坐标.
* mFinalY: 设置y轴的终点坐标.
* mDeltaX: 设置x轴的滑动距离.
* mDeltaY: 设置y轴的滑动距离.
* mDurationReciprocal: 设置时间的倒数.

#### computeScrollOffset

之所以这里提前介绍computeScrollOffset函数,是因为View只有配合computeScrollOffset函数,才能实现真正的滑动.源码中跟SCROLL_MODE相关代码如下：
```java
public boolean computeScrollOffset() {
    // 如果已经结束,直接返回false.
    if (mFinished) {
        return false;
    }

    // 计算已经度过的时间.
    int timePassed = (int) (AnimationUtils.currentAnimationTimeMillis() - mStartTime);

    if (timePassed < mDuration) {
        switch (mMode) {
            // 处理滚动模式
            case SCROLL_MODE:
                // 根据过度的时间计算偏移比例
                final float x = mInterpolator.getInterpolation(timePassed * mDurationReciprocal);
                mCurrX = mStartX + Math.round(x * mDeltaX);
                mCurrY = mStartY + Math.round(x * mDeltaY);
                break;
            // 处理fling模式......
        }
    } else {
        // 当时间结束时,直接将x和y坐标置为终止状态的x和y坐标,同时将终止标志位置为true.
        mCurrX = mFinalX;
        mCurrY = mFinalY;
        mFinished = true;
    }
    return true;
}
```
可以看出,computeScrollOffset也只是根据时间偏移计算x轴和y轴应该到达的坐标.

#### SCROLL_MODE实战

介绍了SCROLL_MODE的具体实现,接下来就通过代码演示一下Scroller是如何和View进行互动的.这里提供一个例子,在40秒内将TextView的内容在x轴向右移动400:
```java
private void initScrollCase() {
    mImageView = (ImageView) findViewById(R.id.id_img_tv);
    // 获取起始滑动点坐标
    int startX = mImageView.getScrollX();
    int startY = mImageView.getScrollY();
    mScroller = new Scroller(getApplicationContext());
    Log.e("zhengyi.wzy", "startX=" + startX + ", startY=" + startY);
    mScroller.startScroll(startX, startY, -400, 0, 40000);
    mImageView.setOnClickListener(new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    boolean isFinished = mScroller.computeScrollOffset();
                    if (!isFinished) {
                        return;
                    }
                    // 获取当前滑动点坐标
                    int x = mScroller.getCurrX();
                    int y = mScroller.getCurrY();
                    mImageView.scrollTo(x, y);
                    mHandler.postDelayed(this, 25);
                }
            }, 25);
        }
    });
}
```

**千万注意:起始点是View.getScrollX()和View.getScrollY(),而不是View.getLeft()或者View.getTop()**

### SCROLL_FLING
Scroller还提供一种FLING模式,我认为它的中文翻译已经叫“抛掷模式”. 




