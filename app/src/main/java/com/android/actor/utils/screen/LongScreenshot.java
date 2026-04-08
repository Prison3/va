package com.android.actor.utils.screen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.view.Display;

import com.android.actor.ActApp;
import com.android.actor.control.api.Swipe;
import com.android.actor.monitor.Logger;

import org.apache.commons.io.FileUtils;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaValue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

public class LongScreenshot {
    /**
     * 长截图：
     * function swipe_callback()
     *     ...
     *     if reach_recommendation then
     *         return true -- stop swipe
     *     end
     *     return false -- continue swipe
     * end
     *
     * bytes = g_driver:newLongShotInstance()
     *                 :setMatchLines(200)
     *                 :setSwipeCount(6)
     *                 :setSwipeCallback(swipe_callback)
     *                 :setSwipeSleep(3000)
     *                 :take()
     *
     *
     *
     * | long_shot_instance:func                                 | desc                                                         |
     * | ------------------------------------------------------- | ------------------------------------------------------------ |
     * | setScreenRect(int left, int top, int right, int bottom) | 设置可滑动的 view 在屏幕中的区域，比如优惠券列表只展示在下半屏，则需要设置此项 |
     * | setMatchLines(int matchLines)                           | 在拼接图片的时候，通过比较其像素是否相同来确定拼接位置，此参数用于设置比较多少行像素，默认100。若有大面积相同颜色，可考虑增大此参数的值 |
     * | setLineRightMismatch(float percent)                     | 在比较的时候，每一行会忽略右侧 2/10 部分，通常右侧会有滑动条和按钮 |
     * | setSwipeCount(int swipeCount)                           | 若只需要截取前面部分截图，可设置滑动次数，默认 100           |
     * | setSwipeCallback(LuaClosure callback)                   | 设置 swipe 回调，每次 swipe 之前会调一次，可用于脚本检测页面情况，进而停止滑动 |
     * | setDiffPercent(float diffPercent)                       | 在比较的时候，允许少量颜色不同，默认 2%                      |
     * | setSwipeSleep(int ms)                                   | 每次滑动的时间间隔，默认 2000ms。请考虑网络加载的速度        |
     * | setCenterTop(int top)                                   | 每次拼接从此 Y 坐标开始计算，若顶部有更多的固定控件，需调大此值，默认 200 |
     * | setCenterBottom(int bottom)                             | 每次拼接到此 Y 坐标，若底部有更多的固定控件，需调小此值，默认 1640 |
     * | asJPG(int quality)                                      | 压缩为 jpg 格式，默认 png，同时可设置压缩质量，0-100，默认 100 |
     * | take()                                                  | 开始进行长截图，成功返回 bytes，失败返回 nil                 |
     * | longScreenshot(int count)                               | *进行长截图，count是滑动次数，滑动count次或者滑不动了，就结束截图。返回bytes |
     *
     * 长截图的注意事项：
     *
     * - 过长的长截图可能会导致 bitmap 占用内存过多引起问题，比如 OutOfMemory，比如下半部分截图为空。此为待优化项，后续可以进一步进行改进。
     * - 网络加载速度会直接影响到截图的完整性，若图片在未加载完毕的情况下就进行了截图，会导致前后两张图片无法拼接；动图因不停变化也会有此问题。
     * - 长截图需要时间可能在1分种到几分钟不等，视参数和页面长度而变化，注意 dispatch 分给你的时间片.
     */

    private static final String TAG = LongScreenshot.class.getSimpleName();
    private static final boolean DEBUG = false;

    private int SCREEN_WIDTH;
    private int SCREEN_HEIGHT;
    private Rect RECT;

    private int PIXEL_MATCH_LINES = 100;
    private float LINE_RIGHT_MISMATCH = 0.2f;
    private int MAX_SWIPE_COUNT = 100;
    private float MAX_DIFF_PERCENT = 0.02f;
    private int SWIPE_SLEEP = 2000;
    private int CENTER_TOP = 200;
    private int CENTER_BOTTOM = 1640;
    private boolean AS_JPG = false;
    private int JPG_QUALITY = 100;

    private LuaClosure mSwipeCallback;

    public static LongScreenshot newInstance() {
        return new LongScreenshot();
    }

    private LongScreenshot() {
        DisplayManager displayManager = (DisplayManager) ActApp.getInstance().getSystemService(Context.DISPLAY_SERVICE);
        Display display = displayManager.getDisplays()[0];
        SCREEN_WIDTH = display.getWidth();
        SCREEN_HEIGHT = display.getHeight();
        RECT = new Rect(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        Logger.d(TAG, "SCREEN " + SCREEN_WIDTH + "x" + SCREEN_HEIGHT);
    }

    public LongScreenshot setScreenRect(int left, int top, int right, int bottom) {
        RECT = new Rect(left, top, right, bottom);
        SCREEN_WIDTH = RECT.width();
        SCREEN_HEIGHT = RECT.height();
        CENTER_TOP = RECT.top + 200;
        CENTER_BOTTOM = RECT.bottom - 200;
        return this;
    }

    /**
     * 在拼接图片的时候，通过比较其像素是否相同来确定拼接位置，此参数用于设置比较多少行像素，默认100
     * 若有大面积相同颜色，可考虑增大此参数的值
     */
    public LongScreenshot setMatchLines(int matchLines) {
        PIXEL_MATCH_LINES = matchLines;
        return this;
    }

    /**
     * 在比较的时候，每一行会忽略右侧 2/10 部分，通常右侧会有滑动条和按钮
     */
    public LongScreenshot setLineRightMismatch(float percent) {
        LINE_RIGHT_MISMATCH = percent;
        return this;
    }

    /**
     * 若只需要截取前面部分截图，可设置滑动次数，默认 100
     */
    public LongScreenshot setSwipeCount(int swipeCount) {
        MAX_SWIPE_COUNT = swipeCount;
        return this;
    }

    public LongScreenshot setSwipeCallback(LuaClosure callback) {
        mSwipeCallback = callback;
        return this;
    }

    /**
     * 在比较的时候，允许少量颜色不同，默认 2%
     */
    public LongScreenshot setDiffPercent(float diffPercent) {
        MAX_DIFF_PERCENT = diffPercent;
        return this;
    }

    /**
     * 每次滑动的时间间隔，默认 2000ms
     * 请考虑网络加载的速度
     */
    public LongScreenshot setSwipeSleep(int ms) {
        SWIPE_SLEEP = ms;
        return this;
    }

    /**
     * 每次拼接从此 Y 坐标开始计算，若顶部有更多的固定控件，需调大此值，默认 200
     */
    public LongScreenshot setCenterTop(int top) {
        CENTER_TOP = top;
        return this;
    }

    /**
     * 每次拼接到此 Y 坐标，若底部有更多的固定控件，需调小此值，默认 1640
     */
    public LongScreenshot setCenterBottom(int bottom) {
        CENTER_BOTTOM = bottom;
        return this;
    }

    public LongScreenshot asJPG() {
        return asJPG(100);
    }

    public LongScreenshot asJPG(int quality) {
        AS_JPG = true;
        JPG_QUALITY = quality;
        return this;
    }

    public byte[] take() {
        final int MAX_DIFF_COUNT = (int) (SCREEN_WIDTH * (1 - LINE_RIGHT_MISMATCH) * PIXEL_MATCH_LINES * MAX_DIFF_PERCENT);
        Logger.d(TAG, "RECT " + RECT
                + ", PIXEL_MATCH_LINES " + PIXEL_MATCH_LINES
                + ", LINE_RIGHT_MISMATCH " + LINE_RIGHT_MISMATCH
                + ", MAX_SWIPE_COUNT " + MAX_SWIPE_COUNT
                + ", MAX_DIFF_COUNT " + MAX_DIFF_COUNT
                + ", SWIPE_SLEEP " + SWIPE_SLEEP
                + ", CENTER_TOP " + CENTER_TOP
                + ", CENTER_BOTTOM " + CENTER_BOTTOM
                + ", AS_JPG " + AS_JPG
                + ", JPG_QUALITY " + JPG_QUALITY);

        try {
            Bitmap longBitmap = null;
            Rect largeTopRect = new Rect(RECT.left, RECT.top, RECT.right, CENTER_BOTTOM);
            Rect centerRect = new Rect(RECT.left, CENTER_TOP, RECT.right, largeTopRect.bottom);
            Rect scrollRect = new Rect(RECT.left, centerRect.top, RECT.right, centerRect.bottom);
            Rect largeBottomRect = new Rect(RECT.left, centerRect.top, RECT.right, RECT.bottom);
            Logger.d(TAG, "largeTopRect " + largeTopRect);
            Logger.d(TAG, "centerRect " + centerRect);
            Logger.d(TAG, "scrollRect " + scrollRect);
            Logger.d(TAG, "largeBottomRect " + largeBottomRect);

            int i = 0;
            long startTime = System.currentTimeMillis();
            for (; i < MAX_SWIPE_COUNT; ++i) {
                if (longBitmap == null) {
                    Bitmap hwBitmap = Screenshot.takeBitmap(largeTopRect);
                    longBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false);
                    hwBitmap.recycle();

                    if (DEBUG) {
                        FileOutputStream output = new FileOutputStream(new File("/sdcard/a.png"));
                        longBitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                    }
                } else {
                    if (mSwipeCallback != null) {
                        LuaValue stop = mSwipeCallback.call();
                        if (stop.toboolean()) {
                            Logger.i(TAG, "Swipe stop from lua script.");
                            break;
                        }
                    }

                    Swipe.swipeUpExact(scrollRect);
                    Thread.sleep(SWIPE_SLEEP);
                    Bitmap hwBitmap = Screenshot.takeBitmap(largeBottomRect);
                    Bitmap bitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false);
                    hwBitmap.recycle();

                    if (DEBUG) {
                        FileOutputStream output = new FileOutputStream(new File("/sdcard/b.png"));
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
                    }

                    Bitmap newBitmap = compareAndCopy(longBitmap, bitmap, centerRect.height(), MAX_DIFF_COUNT);
                    Logger.d(TAG, "newBitmap " + newBitmap);
                    if (newBitmap == null) {
                        Logger.w(TAG, "No linesMatch.");
                        newBitmap = compareAndCopy(longBitmap, bitmap, bitmap.getHeight(), MAX_DIFF_COUNT);
                        if (newBitmap != null) {
                            Logger.d(TAG, "Got last bitmap.");
                            longBitmap.recycle();
                            longBitmap = newBitmap;
                        }
                        bitmap.recycle();
                        break;
                    } else {
                        bitmap.recycle();
                        longBitmap.recycle();
                        longBitmap = newBitmap;
                    }
                }
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (AS_JPG) {
                longBitmap.compress(Bitmap.CompressFormat.JPEG, JPG_QUALITY, output);
            } else {
                longBitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            }
            byte[] bytes = output.toByteArray();
            if (DEBUG) {
                FileUtils.writeByteArrayToFile(new File("/sdcard/c.png"), bytes);
            }

            longBitmap.recycle();
            System.gc();
            int seconds = (int) ((System.currentTimeMillis() - startTime) / 1000);
            Logger.d(TAG, "end, scroll count " + i + ", time cost " + seconds);
            return bytes;
        } catch (Throwable e) {
            Logger.e(TAG, "take exception.", e);
            return null;
        }
    }

    public void test() {
        try {
            Bitmap longBitmap = BitmapFactory.decodeFile("/sdcard/c.png");
            Bitmap bitmap = BitmapFactory.decodeFile("/sdcard/b.png");
            Bitmap result = compareAndCopy(longBitmap, bitmap, bitmap.getHeight(), 864);
            Logger.d(TAG, "test result " + result);
        } catch (Throwable e) {
            Logger.e(TAG, "e", e);
        }
    }

    private Bitmap compareAndCopy(Bitmap longBitmap, Bitmap bitmap, int bitmapHeight, final int MAX_DIFF_COUNT) throws Throwable {
        int compareWidth = (int) (bitmap.getWidth() * (1 - LINE_RIGHT_MISMATCH));
        int lineStart = 0;
        int lowestDiffCount = Integer.MAX_VALUE;

        while (lineStart < bitmapHeight - PIXEL_MATCH_LINES) {
            boolean linesMatch = true;
            int diffCount = 0;
            for (int line = 0; line < PIXEL_MATCH_LINES; ++line) {
                int[] pixels = new int[compareWidth];
                bitmap.getPixels(pixels, 0, compareWidth, 0, line + lineStart, compareWidth, 1);
                int[] longPixels = new int[compareWidth];
                longBitmap.getPixels(longPixels, 0, compareWidth, 0, longBitmap.getHeight() - PIXEL_MATCH_LINES + line, compareWidth, 1);

                for (int i = 0; i < compareWidth; ++i) {
                    int pixel = pixels[i];
                    int longPixel = longPixels[i];
                    if (pixel != longPixel) {
                        ++diffCount;
                        if (diffCount > MAX_DIFF_COUNT) {
                            linesMatch = false;
                            break;
                        }
                    }
                }
                if (!linesMatch) {
                    break;
                }
            }

            lowestDiffCount = Math.min(lowestDiffCount, diffCount);
            if (linesMatch) {
                Logger.d(TAG, "diffCount " + diffCount);
                int secondStart = lineStart + PIXEL_MATCH_LINES;
                int secondHeight = bitmapHeight - secondStart;
                Bitmap newBitmap = Bitmap.createBitmap(longBitmap.getWidth(), longBitmap.getHeight() + secondHeight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(newBitmap);
                canvas.drawBitmap(longBitmap, 0, 0, null);
                canvas.drawBitmap(bitmap,
                        new Rect(0, secondStart, bitmap.getWidth(), bitmapHeight),
                        new Rect(0, longBitmap.getHeight(), bitmap.getWidth(), newBitmap.getHeight()),
                        null);
                return newBitmap;
            }
            ++lineStart;
        }
        Logger.w(TAG, "lowestDiffCount " + lowestDiffCount);
        return null;
    }
}
