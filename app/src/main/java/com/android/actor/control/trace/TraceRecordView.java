package com.android.actor.control.trace;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class TraceRecordView extends View {
    private final static String TAG = TraceRecordView.class.getSimpleName();
    private EventTrace mTrace;
    private int mWidth, mHeight;
    private final Paint mPointPaint;
    private final Paint mPathPaint;
    private final Paint mRectPaint;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private RectF mRect;
    private long mLastTime;
    public boolean canDraw = true;

    public TraceRecordView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPointPaint = new Paint();
        mPointPaint.setAntiAlias(true);
        mPointPaint.setColor(Color.RED);
        mPointPaint.setStyle(Paint.Style.STROKE);
        mPointPaint.setStrokeJoin(Paint.Join.ROUND);
        mPointPaint.setStrokeCap(Paint.Cap.SQUARE);
        mPointPaint.setStrokeWidth(4);

        mPathPaint = new Paint();
        mPathPaint.setAntiAlias(true);
        mPathPaint.setColor(Color.BLACK);
        mPathPaint.setStyle(Paint.Style.STROKE);
        mPathPaint.setStrokeJoin(Paint.Join.ROUND);
        mPathPaint.setStrokeCap(Paint.Cap.SQUARE);
        mPathPaint.setStrokeWidth(2);

        mRectPaint = new Paint();
        mRectPaint.setAntiAlias(true);
        mRectPaint.setColor(Color.BLUE);
        mRectPaint.setAlpha(80);
        mRectPaint.setStyle(Paint.Style.STROKE);
        mRectPaint.setStrokeJoin(Paint.Join.ROUND);
        mRectPaint.setStrokeCap(Paint.Cap.SQUARE);
        mRectPaint.setStrokeWidth(2);
    }

    public void reset() {
        mTrace = new EventTrace();
        mBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mBitmap);
        mRect = new RectF();
        invalidate();
        canDraw = true;
    }

    public EventTrace record() {
        if (mTrace.size > 0) {
            EventTrace ret = mTrace.clone();
            reset();
            return ret;
        }
        return null;
    }

    public void redraw(EventTrace trace) {
        if (trace != null) {
            reset();
            mCanvas.drawRect(trace.originRect, mRectPaint);
            EventPoint p = trace.records.get(0);
            float x0 = p.pointerCoords.x + trace.offsetX;
            float y0 = p.pointerCoords.y + trace.offsetY;
            markPoint(x0, y0);
            mCanvas.drawPoint(x0, y0, mPointPaint);
            for (int i = 1; i < trace.records.size(); i++) {
                p = trace.records.get(i);
                float x1 = p.pointerCoords.x + trace.offsetX;
                float y1 = p.pointerCoords.y + trace.offsetY;
                mCanvas.drawPoint(x1, y1, mPointPaint);
                mCanvas.drawLine(x0, y0, x1, y1, mPathPaint);
                x0 = x1;
                y0 = y1;
            }
            invalidate();
            canDraw = false;
        }
    }

    private void markPoint(float x, float y) {
        mRectPaint.setColor(Color.GREEN);
        mRectPaint.setAlpha(100);
        mCanvas.drawLine(x, 0, x, mHeight, mRectPaint);
        mCanvas.drawLine(0, y, mWidth, y, mRectPaint);
        mRectPaint.setColor(Color.BLUE);
        mRectPaint.setAlpha(100);
    }

    private void updateRect(MotionEvent event) {
        if (event.getX() < mRect.left) {
            mRect.left = event.getX();
        } else if (event.getX() > mRect.right) {
            mRect.right = event.getX();
        }
        if (event.getY() < mRect.top) {
            mRect.top = event.getY();
        } else if (event.getY() > mRect.bottom) {
            mRect.bottom = event.getY();
        }
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mWidth = w;
        mHeight = h;
        reset();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawBitmap(mBitmap, 0, 0, mPointPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (canDraw) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                mTrace.add(event);
            } else {
                mTrace.add(event, (int) (event.getEventTime() - mLastTime));
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mCanvas.drawPoint(event.getX(), event.getY(), mPointPaint);
                    mRect.left = mRect.right = event.getX();
                    mRect.top = mRect.bottom = event.getY();
                    markPoint(event.getX(), event.getY());
                    break;

                case MotionEvent.ACTION_MOVE:
                    mCanvas.drawPoint(event.getX(), event.getY(), mPointPaint);
                    if (mTrace.size > 2) {
                        EventPoint last = mTrace.records.get(mTrace.size - 2);
                        mCanvas.drawLine(event.getX(), event.getY(), last.pointerCoords.x, last.pointerCoords.y, mPathPaint);
                    }
                    updateRect(event);
                    break;

                case MotionEvent.ACTION_UP:
                    mCanvas.drawPoint(event.getX(), event.getY(), mPointPaint);
                    if (mTrace.size > 2) {
                        EventPoint last = mTrace.records.get(mTrace.size - 2);
                        mCanvas.drawLine(event.getX(), event.getY(), last.pointerCoords.x, last.pointerCoords.y, mPathPaint);
                    }
                    updateRect(event);
                    mTrace.setRectAndOffset(mRect);
                    mCanvas.drawRect(mRect, mRectPaint);
                    canDraw = false;
                    invalidate();
                    break;
            }
            mLastTime = event.getEventTime();
        }
        return true;
    }

}
