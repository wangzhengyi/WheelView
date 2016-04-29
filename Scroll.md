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