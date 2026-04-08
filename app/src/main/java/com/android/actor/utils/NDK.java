package com.android.actor.utils;

import android.graphics.Bitmap;

import com.android.actor.monitor.Logger;

public class NDK {
    private final static String TAG = NDK.class.getSimpleName();

    static {
        try {
            System.loadLibrary("bitmap");
        } catch (Exception e) {
            Logger.e(TAG, e.toString(), e);
        }
    }

    public native static boolean rgb2gray(Bitmap bitmap);

    public native static int compareSameSizeImage(Bitmap bm1, Bitmap bm2, int[] array);

    public native static void filterArray(float[] array, int[] filter);

    public native static float[] interpolateLiner(float start, float end, int count);

    public native static float[] interpolateAccelerateDecelerate(float start, float end, int count);

    public native static float[] interpolateAccelerate(float start, float end, int count);

    public native static float[] interpolateDecelerate(float start, float end, int count);

    public native static float[] interpolateBezierCurveTwo(float y0, float y1, float y2, int count);
}
