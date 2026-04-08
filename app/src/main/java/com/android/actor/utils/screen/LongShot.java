package com.android.actor.utils.screen;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;

import com.android.actor.BuildConfig;
import com.android.actor.control.api.Swipe;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.NDK;

import org.apache.commons.io.FileUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class LongShot {
    private final static String TAG = LongShot.class.getSimpleName();

    public static void test() {
        try {
            Thread.sleep(3000);
            Bitmap bm1 = Screenshot.takeBitmap().copy(Bitmap.Config.ARGB_8888, false);
            Swipe.swipeUp();
            Thread.sleep(3000);
            Bitmap bm2 = Screenshot.takeBitmap().copy(Bitmap.Config.ARGB_8888, false);
            long t4 = System.currentTimeMillis();
            int[] config = new int[3];
            NDK.compareSameSizeImage(bm1, bm2, config);
            int startY = config[0];
            int endY = config[1];
            int offset = config[2];
            Logger.d(TAG, String.format("%d - %d, offset %d", startY, endY, offset));
            long t5 = System.currentTimeMillis();
            Logger.d(TAG, "compare using time: " + (t5 - t4) + " ms.");

            int width = bm1.getWidth();
            int height = bm1.getHeight();
            Bitmap newBitmap = Bitmap.createBitmap(width, height + offset, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(newBitmap);
            canvas.drawBitmap(bm1, new Rect(0, 0, width, endY), new Rect(0, 0, width, endY), null);
            canvas.drawBitmap(bm2, new Rect(0, endY - offset, width, height), new Rect(0, endY, width, height + offset), null);
            long t6 = System.currentTimeMillis();
            Logger.d(TAG, "mix using time: " + (t6 - t5) + " ms.");
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            newBitmap.compress(Bitmap.CompressFormat.JPEG, 50, output);
            FileUtils.writeByteArrayToFile(new File("/sdcard/b.jpg"), output.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @SuppressLint("DefaultLocale")
    public static Bitmap takeShotBitmap(int count) {
        List<Bitmap> bitmapList = new ArrayList<>();
        List<int[]> offsetList = new ArrayList<>();
        int[] config = new int[3]; // 0: topY, 1: bottomY, 2: offset
        try {
            Bitmap firstHWBitmap = Screenshot.takeBitmap();
            bitmapList.add(firstHWBitmap.copy(Bitmap.Config.ARGB_8888, false));
            firstHWBitmap.recycle();
            int width = bitmapList.get(0).getWidth();
            int height = bitmapList.get(0).getHeight();
            for (int i = 0; i < count; i++) {
                if (i == 0) {
                    Swipe.swipeVertical(540, 1300, 540, 1000, 40, Swipe.TYPE_LINER);
                } else {
                    Swipe.swipeVertical(540, 1400, 540, 600, 50, Swipe.TYPE_LINER);
                }
                Thread.sleep(2000);
                Bitmap scrollHWBitmap = Screenshot.takeBitmap();
                bitmapList.add(scrollHWBitmap.copy(Bitmap.Config.ARGB_8888, false));
                scrollHWBitmap.recycle();
                NDK.compareSameSizeImage(bitmapList.get(i), bitmapList.get(i + 1), config);
                if (config[2] == 0) {
                    bitmapList.remove(i + 1);
                    break;
                }
                offsetList.add(Arrays.copyOf(config, 3));
            }
            int offsetSum = 0;
            count = offsetList.size();
            for (int i = 0; i < count; i++) {
                offsetSum += offsetList.get(i)[2];
            }
            long startTime = System.currentTimeMillis();
            Bitmap bitmap;
            if (count > 0) {
                bitmap = Bitmap.createBitmap(width, height + offsetSum, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                int posY = offsetList.get(0)[1];
                canvas.drawBitmap(bitmapList.get(0), new Rect(0, 0, width, posY), new Rect(0, 0, width, posY), null);
                bitmapList.get(0).recycle();
                int i = 0;
                for (; i < count - 1; i++) {
                    int bottomY = offsetList.get(i)[1];
                    int offsetY = offsetList.get(i)[2];
                    canvas.drawBitmap(bitmapList.get(i + 1), new Rect(0, bottomY - offsetY, width, bottomY), new Rect(0, posY, width, posY + offsetY), null);
                    posY += offsetY;
                    bitmapList.get(i).recycle();
                }
                canvas.drawBitmap(bitmapList.get(i + 1), new Rect(0, offsetList.get(i)[1] - offsetList.get(i)[2], width, height), new Rect(0, posY, width, height + offsetSum), null);
                bitmapList.get(i + 1).recycle();
            } else {
                bitmap = bitmapList.get(0);
            }
            Logger.i(TAG, String.format("long screen shot get image width %d, height %d, using time %d ms", width, height + offsetSum, System.currentTimeMillis() - startTime));
            return bitmap;
        } catch (Throwable e) {
            Logger.e(TAG, e.toString(), e);
        }
        return null;

    }

    public static byte[] takeLongShot(int count) {
        Bitmap bitmap = takeShotBitmap(count);
        if (bitmap != null) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, output);
            if (BuildConfig.DEBUG) {
                try {
                    FileUtils.writeByteArrayToFile(new File("/sdcard/a.jpg"), output.toByteArray());
                    Logger.d(TAG, "save bitmap success");
                } catch (IOException e) {
                    e.printStackTrace();
                    Logger.e(TAG, e.toString(), e);
                }
            }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, output);
            return output.toByteArray();
        }
        return null;
    }

    public static byte[] takeLongShot() {
        return takeLongShot(100);
    }

    public static void takeJpg(int quality, float ratio) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Logger.i(TAG, "take jpg: " + quality + ", " + ratio);
        byte[] bytes = Screenshot.takeJPG(quality, ratio);
        try {
            output.write(bytes);
            FileUtils.writeByteArrayToFile(new File("/sdcard/a.jpg"), output.toByteArray());
            Logger.d(TAG, "save jpg success: " + bytes.length);
        } catch (IOException e) {
            e.printStackTrace();
            Logger.e(TAG, e.toString(), e);
        }
    }
}
