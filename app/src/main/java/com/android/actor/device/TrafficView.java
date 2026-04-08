package com.android.actor.device;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.android.actor.utils.TimeFormatUtils;
import com.android.actor.utils.db.TUnit;
import com.android.actor.utils.db.TrafficContract;

import java.util.List;

public class TrafficView extends View {
    private final static String TAG = TrafficView.class.getSimpleName();
    private int mWidth, mHeight;
    private Rect mDrawRect;
    private Paint mGridPaint;
    private Paint mSrvLinePaint;
    private Paint mSrvPointPaint;
    private Paint mSndLinePaint;
    private Paint mSndPointPaint;
    private List<TUnit> mTrace;

    public TrafficView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initGrid();
        initTrace();
    }

    private void initGrid() {
        mGridPaint = new Paint();
        mGridPaint.setColor(Color.BLACK);
        mGridPaint.setStrokeWidth(2);
//        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeCap(Paint.Cap.ROUND);
        mGridPaint.setTextSize(24);
    }

    private void initTrace() {
        mSrvLinePaint = new Paint();
        mSrvLinePaint.setColor(Color.GREEN);
        mSrvLinePaint.setStrokeWidth(2);
        mSrvLinePaint.setStrokeCap(Paint.Cap.ROUND);
        mSrvLinePaint.setTextSize(30);

        mSrvPointPaint = new Paint(mSrvLinePaint);
        mSrvPointPaint.setStrokeWidth(5);

        mSndLinePaint = new Paint(mSrvLinePaint);
        mSndLinePaint.setColor(Color.BLUE);

        mSndPointPaint = new Paint(mSndLinePaint);
        mSndPointPaint.setStrokeWidth(5);
    }

    public void drawSerial(List<TUnit> trace) {
        mTrace = trace;
        invalidate();
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mWidth = w;
        mHeight = h;
        mDrawRect = new Rect(mWidth / 10, mHeight / 10, 7 * mWidth / 8, 9 * mHeight / 10);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(mDrawRect.left, mDrawRect.bottom, mDrawRect.right, mDrawRect.bottom, mGridPaint);
        canvas.drawLine(mDrawRect.left, mDrawRect.top, mDrawRect.left, mDrawRect.bottom, mGridPaint);
        canvas.drawText("srv bytes", mDrawRect.left + mDrawRect.width() / 3, 2 * mDrawRect.top / 3, mSrvLinePaint);
        canvas.drawText("snd bytes", mDrawRect.left + 2 * mDrawRect.width() / 3, 2 * mDrawRect.top / 3, mSndLinePaint);

        if (mTrace != null) {
            int count = mTrace.size();
            TUnit start = mTrace.get(0);
            TUnit end = mTrace.get(count - 1);
            float rateX = (float) (end.time - start.time) / mDrawRect.width();
            float rateY = (int) (end.rsv > end.snd ? (end.rsv / mDrawRect.height()) : (end.snd / mDrawRect.height()));
            for (int i = 0; i < count - 1; i++) {
                TUnit a = mTrace.get(i);
                TUnit b = mTrace.get(i + 1);
                float ax = mDrawRect.left + (a.time - start.time) / rateX;
                float aSrv = mDrawRect.bottom - a.rsv / rateY;
                float aSnd = mDrawRect.bottom - a.snd / rateY;
                float bx = mDrawRect.left + (b.time - start.time) / rateX;
                float bSrv = mDrawRect.bottom - b.rsv / rateY;
                float bSnd = mDrawRect.bottom - b.snd / rateY;
                canvas.drawLine(ax, aSrv, bx, bSrv, mSrvLinePaint);
                canvas.drawLine(ax, aSnd, bx, bSnd, mSndLinePaint);
                canvas.drawPoint(bx, bSrv, mSrvPointPaint);
                canvas.drawPoint(bx, bSnd, mSndPointPaint);
//                if (i == 0){ // both first points are (0, 0)
//                    canvas.drawPoint(ax, aSrv, mSrvPaint);
//                    canvas.drawPoint(ax, aSnd, mSrvPaint);
//                }
            }
            canvas.drawText(TrafficContract.getBytesText(end.rsv), mDrawRect.right, mDrawRect.bottom - end.rsv / rateY, mSrvLinePaint);
            canvas.drawText(TrafficContract.getBytesText(end.snd), mDrawRect.right, mDrawRect.bottom - end.snd / rateY, mSndLinePaint);
            canvas.drawText(TimeFormatUtils.getDatetimeString(start.time), 3 * mDrawRect.left / 5, mDrawRect.bottom + mDrawRect.top / 3, mGridPaint);
            canvas.drawText(TimeFormatUtils.getDatetimeString(end.time), mDrawRect.right - mDrawRect.width() / 5, mDrawRect.bottom + mDrawRect.top / 3, mGridPaint);
        }
    }
}
