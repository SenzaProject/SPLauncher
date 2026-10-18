package com.senzaproject.splauncher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.OverScroller;

/** Pager horizontal: beberapa halaman Home + satu halaman App Library di akhir. */
final class Workspace extends ViewGroup {
    interface Listener {
        void onScrollFraction(float pos);
        void onPageSettled(int page);
        void onPullDown(float x, float y);
        void onEmptyLongPress(float x, float y);
    }

    private static final int IDLE = 0, SCROLLING = 1, PULLING = 2;

    private final OverScroller scroller;
    private VelocityTracker vt;
    private final int slop, minFling;
    private int state = IDLE;
    private float downX, downY, lastX;
    private int page = 0;
    private int homeCount = 1;
    private boolean pullEnabled = true;
    private boolean longPressEnabled = true;
    private Listener listener;
    private boolean longFired;
    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            if (state == IDLE && longPressEnabled && listener != null) {
                longFired = true;
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                listener.onEmptyLongPress(downX, downY);
            }
        }
    };

    Workspace(Context c) {
        super(c);
        scroller = new OverScroller(c);
        ViewConfiguration vc = ViewConfiguration.get(c);
        slop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity() * 6;
        setClipChildren(true);
    }

    void setListener(Listener l) { listener = l; }
    void setHomeCount(int n) { homeCount = n; }
    int page() { return page; }
    void setPullEnabled(boolean e) { pullEnabled = e; }
    void setLongPressEnabled(boolean e) { longPressEnabled = e; }
    int pageCount() { return getChildCount(); }

    void setPage(int p) {
        page = U.clamp(p, 0, Math.max(0, getChildCount() - 1));
        scroller.abortAnimation();
        scrollTo(page * Math.max(1, getWidth()), 0);
        if (listener != null) listener.onPageSettled(page);
    }

    void smoothTo(int p) {
        p = U.clamp(p, 0, Math.max(0, getChildCount() - 1));
        page = p;
        int dx = p * getWidth() - getScrollX();
        scroller.startScroll(getScrollX(), 0, dx, 0, 320);
        postInvalidateOnAnimation();
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec), h = MeasureSpec.getSize(hSpec);
        setMeasuredDimension(w, h);
        int cw = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
        int ch = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(cw, ch);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).layout(i * w, 0, (i + 1) * w, b - t);
        if (changed) scrollTo(page * w, 0);
    }

    @Override public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.getCurrX(), 0);
            postInvalidateOnAnimation();
            if (scroller.isFinished() && listener != null) listener.onPageSettled(page);
        }
    }

    @Override protected void onScrollChanged(int l, int t, int ol, int ot) {
        super.onScrollChanged(l, t, ol, ot);
        if (listener != null && getWidth() > 0) listener.onScrollFraction(l / (float) getWidth());
        Glass.invalidateAll();
    }

    private void track(MotionEvent e) {
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
    }

    private void release() {
        if (vt != null) { vt.recycle(); vt = null; }
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                track(e);
                if (!scroller.isFinished()) { scroller.abortAnimation(); state = SCROLLING; return true; }
                state = IDLE;
                break;
            case MotionEvent.ACTION_MOVE: {
                track(e);
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (state == IDLE) {
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        state = SCROLLING; lastX = e.getX(); removeCallbacks(longPress);
                        return true;
                    }
                    if (pullEnabled && listener != null && dy > slop * 2 && dy > Math.abs(dx) * 1.5f
                            && page < homeCount) {
                        state = PULLING; removeCallbacks(longPress);
                        return true;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                release();
                break;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        track(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = lastX = e.getX();
                downY = e.getY();
                state = IDLE;
                longFired = false;
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX, dy = e.getY() - downY;
                if (state == IDLE) {
                    if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        state = SCROLLING; lastX = e.getX(); removeCallbacks(longPress);
                    } else if (Math.abs(dy) > slop * 2 || Math.abs(dx) > slop * 2) {
                        removeCallbacks(longPress);
                    }
                }
                if (state == SCROLLING) {
                    float d = lastX - e.getX();
                    lastX = e.getX();
                    int w = getWidth();
                    int max = (getChildCount() - 1) * w;
                    float nx = getScrollX() + d;
                    if (nx < 0 || nx > max) d *= 0.4f; // tahanan di tepi
                    scrollBy(Math.round(d), 0);
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                removeCallbacks(longPress);
                if (state == SCROLLING) {
                    vt.computeCurrentVelocity(1000);
                    float vx = vt.getXVelocity();
                    int w = Math.max(1, getWidth());
                    float base = getScrollX() / (float) w;
                    int target;
                    if (vx < -minFling) target = (int) Math.floor(base) + 1;
                    else if (vx > minFling) target = (int) Math.ceil(base) - 1;
                    else target = Math.round(base);
                    smoothTo(target);
                } else if (state == PULLING) {
                    vt.computeCurrentVelocity(1000);
                    float dy = e.getY() - downY;
                    if ((dy > U.dpf(getContext(), 70) || vt.getYVelocity() > 1200) && listener != null) {
                        listener.onPullDown(downX, downY);
                    }
                }
                state = IDLE;
                release();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                if (state == SCROLLING) smoothTo(Math.round(getScrollX() / (float) Math.max(1, getWidth())));
                state = IDLE;
                release();
                return true;
        }
        return true;
    }
}
