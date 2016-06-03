package com.moshangjian.switchview;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import java.util.LinkedList;

/**
 * 主要实现Banner的轮播,不重复使用ImageView
 * Created by lbe on 16-5-16.
 */
public class SwitchView extends ViewGroup implements Runnable {

    private final static int DEFAULT_AUTO_DELAY = 15000;
    public final static String LEFT_BANNER = "left";
    public final static String CENTER_BANNER = "center";
    public final static String RIGHT_BANNER = "right";

    private int width;
    private int height;
    private int distanceX;
    private LinkedList<Location> locations;
    private VelocityTracker mVelocityTracker;
    private double childRatios = 0.6;
    private int boundaryValue;
    private int imageWidth;
    private boolean isIntercept = false;
    private boolean switching = false;

    private long autoDelay = DEFAULT_AUTO_DELAY;

    /**
     * 记录被判定为滚动运动的最小滚动值
     */
    private int mTouchSlop;

    /**
     * 记录上次触摸的横坐标值
     */
    private float mLastMotionX;
    /**
     * 滚动到下一张图片的速度
     */
    private static final int SNAP_VELOCITY = 600;

    private BannerClickListener bannerClickListener;

    public SwitchView(Context context) {
        super(context);
        init();
    }

    public SwitchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SwitchView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        locations = new LinkedList<Location>();
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int childHeightMeasureSpace = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
        int childWidthMeasureSpace = MeasureSpec.makeMeasureSpec((int) (widthSize * childRatios), MeasureSpec.EXACTLY);

        measureChildren(childWidthMeasureSpace, childHeightMeasureSpace);
        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = getMeasuredWidth();
        height = getMeasuredHeight();
        imageWidth = (int) (width * childRatios);
        initLocation();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (getChildCount() < 3) {
            return;
        }
        for (Location location : locations) {
            View childView = getChildAt(location.childPosition);
            childView.layout(location.left, 0, location.right, height);
        }
    }

    private void initLocation() {
        int childCount = getChildCount();
        int left = -imageWidth + (width - imageWidth) / 2;
        if (locations != null) {
            locations.clear();
        }
        for (int i = 0; i < childCount; i++) {
            locations.add(new Location(i, left, imageWidth));
            left += imageWidth;
        }
        boundaryValue = (int) (width * (1 - childRatios) / 4);
    }

    private boolean moving = false;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getChildCount() < 3) {
            return super.onTouchEvent(event);
        }
        if (moveAnimator != null && moveAnimator.isRunning()) {
            return super.onTouchEvent(event);
        }
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);
        float x = event.getX();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 记录按下时的横坐标
                mLastMotionX = x;
            case MotionEvent.ACTION_MOVE:
                int disX = (int) (mLastMotionX - x);
                if (Math.abs(disX) > mTouchSlop && !moving) {
                    moving = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (moving) {
                    distanceX += disX;
                    alterLocation(disX);
                    mLastMotionX = x;
                    requestLayout();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (moving) {
                    mVelocityTracker.computeCurrentVelocity(1000);
                    int velocityX = (int) mVelocityTracker.getXVelocity();
                    if (shouldScrollToNext(velocityX)) {
                        // 滚动到下一张图
                        scrollToNext();
                    } else if (shouldScrollToPrevious(velocityX)) {
                        // 滚动到上一张图
                        scrollToPrevious();
                    } else {
                        // 滚动回当前图片
                        scrollBack();
                    }
                } else {
                    childClick(mLastMotionX);
                }
                recovery();
                break;
        }
        return true;
    }

    private void clickBannerListener(String location){
        if (bannerClickListener != null){
            bannerClickListener.click(location);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            removeCallbacks(this);
        } else if (ev.getAction() == MotionEvent.ACTION_CANCEL || ev.getAction() == MotionEvent.ACTION_UP) {
            postScroll();
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        float x = ev.getX();
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mLastMotionX = ev.getX();
                isIntercept = !(x > width * (1 - childRatios) / 2 && x < (width * (1 - childRatios) / 2 + imageWidth)) || super.onInterceptTouchEvent(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                int disX = (int) (mLastMotionX - x);
                if (Math.abs(disX) > mTouchSlop) {
                    isIntercept = true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                break;
        }
        return isIntercept;
    }

    private String getEventName(int action) {
        if (action == MotionEvent.ACTION_DOWN) {
            return "down";
        } else if (action == MotionEvent.ACTION_MOVE) {
            return "move";
        } else if (action == MotionEvent.ACTION_UP) {
            return "up";
        }
        return "";
    }

    private void recovery() {
        distanceX = 0;
        moving = false;
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    private boolean childClick(float clickX) {
        if (clickX < width * (1 - childRatios) / 2) {
            clickBannerListener(LEFT_BANNER);
            scrollToPrevious();
            return true;
        } else if (clickX > (width * (1 - childRatios) / 2 + imageWidth)) {
            clickBannerListener(RIGHT_BANNER);
            scrollToNext();
            return true;
        }else {
            clickBannerListener(CENTER_BANNER);
            return  true;
        }
    }

    private void alterLocation(int disX) {
        for (Location location : locations) {
            location.skew(disX);
        }
        int firstBoundaryLeft = locations.get(1).left;
        int zeroBoundaryLeft = locations.get(0).left;
        if (firstBoundaryLeft < 0 && Math.abs(firstBoundaryLeft) >= boundaryValue) {
            Location location = locations.get(0);
            location.resetLeft(imageWidth * (locations.size() - 1) + locations.get(1).left);
            location = locations.remove(0);
            locations.add(location);
        } else if (zeroBoundaryLeft > -boundaryValue) {
            int position = locations.size() - 1;
            locations.get(position).resetLeft(zeroBoundaryLeft - imageWidth);
            Location location = locations.remove(position);
            locations.add(0, location);
        }
    }

    /**
     * 判断是否应该滚动到下一张图片。
     */
    private boolean shouldScrollToNext(int velocityX) {
        return velocityX < -SNAP_VELOCITY || distanceX > imageWidth / 2;
    }

    /**
     * 判断是否应该滚动到上一张图片。
     */
    private boolean shouldScrollToPrevious(int velocityX) {
        return velocityX > SNAP_VELOCITY || distanceX < -imageWidth / 2;
    }

    private void scrollToNext() {
        int moveDistance = imageWidth;
        int start = 0;
        int end = moveDistance - distanceX;
        scrollAnimation(start, end);
    }

    private void scrollToPrevious() {
        int moveDistance = imageWidth;
        int start = 0;
        int end = -moveDistance - distanceX;
        scrollAnimation(start, end);
    }

    private void scrollBack() {
        scrollAnimation(0, -distanceX);
    }

    private ValueAnimator moveAnimator;
    private int lastAnimationValue;

    private void scrollAnimation(int start, int end) {
        lastAnimationValue = start;
        moveAnimator = ValueAnimator.ofInt(start, end);
        moveAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                int value = (Integer) animation.getAnimatedValue();
                alterLocation(value - lastAnimationValue);
                lastAnimationValue = value;
                requestLayout();
            }
        });
        moveAnimator.setDuration(200);
        moveAnimator.setInterpolator(new DecelerateInterpolator());
        moveAnimator.start();
    }

    private void postScroll() {
        if (switching) {
            postDelayed(this, autoDelay);
        }
    }

    /**
     * 设置轮播的间隔时间
     * @param delay
     */
    public void setAutoDelay(long delay) {
        this.autoDelay = delay;
    }

    @Override
    public void run() {
        scrollToNext();
        postScroll();
    }

    public void setBannerClickListener(BannerClickListener bannerClickListener){
        this.bannerClickListener = bannerClickListener;
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        if (visibility != View.VISIBLE) {
            if (switching) {
                removeCallbacks(this);
                switching = false;
            }
        } else {
            if (!switching) {
                switching = true;
                postScroll();
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!switching) {
            switching = true;
            postScroll();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (switching) {
            removeCallbacks(this);
            switching = false;
        }
    }

    public class Location {
        public int childPosition;
        public int left;
        public int right;
        public int mImageWidth;

        public Location(int childPosition, int left, int imageWidth) {
            this.childPosition = childPosition;
            this.mImageWidth = imageWidth;
            this.left = left;
            this.right = left + imageWidth;
        }

        public void skew(int dx) {
            this.left -= dx;
            this.right = this.left + mImageWidth;
        }

        public void resetLeft(int left) {
            this.left = left;
            this.right = this.left + mImageWidth;
        }
    }

    public interface BannerClickListener {
        public void click(String bannerPosition);
    }

}