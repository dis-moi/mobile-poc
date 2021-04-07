package com.dismoi.scout.Floating.Layout;

import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.content.Context;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.annotation.SuppressLint;

import com.dismoi.scout.R;

public class Bubble extends Layout {
  private float initialTouchX;
  private float initialTouchY;
  private int initialX;
  private int initialY;
  private OnBubbleRemoveListener onBubbleRemoveListener;
  private OnBubbleClickListener onBubbleClickListener;
  private static final int TOUCH_TIME_THRESHOLD = 150;
  private long lastTouchDown;
  private final MoveAnimator animator;
  private int width;
  private final WindowManager windowManager;
  private boolean shouldStickToWall = true;

  public void setOnBubbleRemoveListener(OnBubbleRemoveListener listener) {
    onBubbleRemoveListener = listener;
  }

  public void setOnBubbleClickListener(OnBubbleClickListener listener) {
    onBubbleClickListener = listener;
  }

  public Bubble(Context context, AttributeSet attrs) {
    super(context, attrs);
    animator = new MoveAnimator();
    
    windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    initializeView();
  }

  public void setShouldStickToWall(boolean shouldStick) {
    this.shouldStickToWall = shouldStick;
  }

  public void notifyBubbleRemoved() {
    if (onBubbleRemoveListener != null) {
      onBubbleRemoveListener.onBubbleRemoved(this);
    }
  }

  private void initializeView() {
    setClickable(true);
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();
    playAnimation();
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (event != null) {
      switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
          initialX = getViewParams().x;
          initialY = getViewParams().y;
          initialTouchX = event.getRawX();
          initialTouchY = event.getRawY();
          playAnimationClickDown();
          lastTouchDown = System.currentTimeMillis();
          updateSize();
          animator.stop();
          break;
        case MotionEvent.ACTION_MOVE:
          int x = initialX + (int)(event.getRawX() - initialTouchX);
          int y = initialY + (int)(event.getRawY() - initialTouchY);
          getViewParams().x = x;
          getViewParams().y = y;
          getWindowManager().updateViewLayout(this, getViewParams());
          if (getLayoutCoordinator() != null) {
              getLayoutCoordinator().notifyBubblePositionChanged(this, x, y);
          }
          break;
        case MotionEvent.ACTION_UP:
          goToWall();
          if (getLayoutCoordinator() != null) {
              getLayoutCoordinator().notifyBubbleRelease(this);
              playAnimationClickUp();
          }
          if (System.currentTimeMillis() - lastTouchDown < TOUCH_TIME_THRESHOLD) {
              if (onBubbleClickListener != null) {
                  onBubbleClickListener.onBubbleClick(this);
              }
          }
          break;
      }
    }
    return super.onTouchEvent(event);
  }

  private void playAnimation() {
    if (!isInEditMode()) {
      AnimatorSet animator = (AnimatorSet) AnimatorInflater
              .loadAnimator(getContext(), R.animator.bubble_shown_animator);
      animator.setTarget(this);
      animator.start();
    }
  }

  private void playAnimationClickDown() {
    if (!isInEditMode()) {
      AnimatorSet animator = (AnimatorSet) AnimatorInflater
              .loadAnimator(getContext(), R.animator.bubble_down_click_animator);
      animator.setTarget(this);
      animator.start();
    }
  }

  private void playAnimationClickUp() {
    if (!isInEditMode()) {
      AnimatorSet animator = (AnimatorSet) AnimatorInflater
              .loadAnimator(getContext(), R.animator.bubble_up_click_animator);
      animator.setTarget(this);
      animator.start();
    }
  }

  private void updateSize() {
    DisplayMetrics metrics = new DisplayMetrics();
    windowManager.getDefaultDisplay().getMetrics(metrics);
    Display display = getWindowManager().getDefaultDisplay();
    Point size = new Point();
    display.getSize(size);
    width = (size.x - this.getWidth());
  }

  public interface OnBubbleRemoveListener {
    void onBubbleRemoved(Bubble bubble);
  }

  public interface OnBubbleClickListener {
    void onBubbleClick(Bubble bubble);
  }

  public void goToWall() {
    if (shouldStickToWall){
      int middle = width / 2;
      float nearestXWall = getViewParams().x >= middle ? width : 0;
      animator.start(nearestXWall, getViewParams().y);
    }
  }

  private void move(float deltaX, float deltaY) {
    getViewParams().x += deltaX;
    getViewParams().y += deltaY;
    windowManager.updateViewLayout(this, getViewParams());
  }


  private class MoveAnimator implements Runnable {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private float destinationX;
    private float destinationY;
    private long startingTime;

    private void start(float x, float y) {
      this.destinationX = x;
      this.destinationY = y;
      startingTime = System.currentTimeMillis();
      handler.post(this);
    }

    @Override
    public void run() {
      if (getRootView() != null && getRootView().getParent() != null) {
        float progress = Math.min(1, (System.currentTimeMillis() - startingTime) / 400f);
        float deltaX = (destinationX -  getViewParams().x) * progress;
        float deltaY = (destinationY -  getViewParams().y) * progress;
        move(deltaX, deltaY);
        if (progress < 1) {
          handler.post(this);
        }
      }
    }

    private void stop() {
      handler.removeCallbacks(this);
    }
  }
}
