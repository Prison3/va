package com.android.actor.utils;

import android.content.Context;
import android.graphics.Point;
import android.util.TypedValue;
import android.view.WindowManager;

import com.android.actor.ActApp;

public class ViewUtils {

    private static Point sScreenPoint;

    public static int dp2px(float dpValue) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dpValue,
                ActApp.getInstance().getResources().getDisplayMetrics());
    }

    public static int getScreenWidth() {
        createScreenPointIfNull();
        return sScreenPoint.x;
    }

    public static int getScreenHeight() {
        createScreenPointIfNull();
        return sScreenPoint.y;
    }

    private static void createScreenPointIfNull() {
        if (sScreenPoint == null) {
            WindowManager wm = (WindowManager) ActApp.getInstance().getSystemService(Context.WINDOW_SERVICE);
            Point point = new Point();
            wm.getDefaultDisplay().getRealSize(point);
            sScreenPoint = point;
        }
    }
}
