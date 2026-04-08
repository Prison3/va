package com.android.actor.utils.screen;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.view.Display;
import android.view.SurfaceControl;

import com.android.actor.ActApp;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.ReflectUtils;
import com.android.actor.utils.ViewUtils;

import java.io.ByteArrayOutputStream;

public class Screenshot {
    private static final String TAG = Screenshot.class.getSimpleName();

    public static byte[] take() {
        return take(0, 0, ViewUtils.getScreenWidth(), ViewUtils.getScreenHeight());
    }

    public static byte[] takeJPG() {
        return takeJPG(80, 1f);
    }

    public static byte[] takeJPG(int quality, float ratio) {
        return takeJPG(0, 0, ViewUtils.getScreenWidth(), ViewUtils.getScreenHeight(), quality, ratio);
    }

    public static Bitmap takeBitmap() {
        return takeBitmap(0, 0, ViewUtils.getScreenWidth(), ViewUtils.getScreenHeight());
    }

    public static byte[] take(int x, int y, int width, int height) {
        return take(new Rect(x, y, x + width, y + height));
    }

    public static byte[] takeJPG(int x, int y, int width, int height, int quality, float ratio) {
        return take(new Rect(x, y, x + width, y + height), Bitmap.CompressFormat.JPEG, quality, ratio);
    }

    public static Bitmap takeBitmap(int x, int y, int width, int height) {
        return takeBitmap(new Rect(x, y, x + width, y + height));
    }

    public static byte[] take(Rect rect) {
        return take(rect, Bitmap.CompressFormat.PNG, 100, 1f);
    }

    public static byte[] take(Rect rect, Bitmap.CompressFormat format, int quality, float ratio) {
        try {
            Bitmap bitmap = takeBitmap(rect, ratio);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            bitmap.compress(format, quality, output);
            output.close();
            bitmap.recycle();
            Logger.d(TAG, "Screenshot of " + rect + ", size " + output.size());
            return output.toByteArray();
        } catch (Throwable e) {
            Logger.e(TAG, "Can't take screenshot.", e);
            return null;
        }
    }

    public static Bitmap takeBitmap(Rect rect) {
        return takeBitmap(rect, 1f);
    }

    public static Bitmap takeBitmap(Rect rect, float ratio) {
        try {
            DisplayManager displayManager = (DisplayManager) ActApp.getInstance().getSystemService(Context.DISPLAY_SERVICE);
            Display display = displayManager.getDisplays()[0];
            //int displayId = display.getDisplayId();
            Object address = ReflectUtils.callMethod(display, "getAddress");
            long physicalDisplayId = (long) ReflectUtils.callMethod(address, "getPhysicalDisplayId");
            IBinder displayToken = (IBinder) ReflectUtils.callStaticMethod(SurfaceControl.class, "getPhysicalDisplayToken", new Object[] {
                    physicalDisplayId
            }, new Class[] {
                    long.class
            });
            int width = rect.width();
            int height = rect.height();
            Logger.d(TAG, "physicalDisplayId " + physicalDisplayId + ", width " + width + ", height " + height + ", displayToken " + displayToken);

            Class cls = ReflectUtils.findClass(SurfaceControl.class.getName() + "$DisplayCaptureArgs$Builder", display.getClass().getClassLoader());
            Object builder = ReflectUtils.newInstance(cls, new Object[] {
                    displayToken
            }, new Class[] {
                    IBinder.class
            });
            ReflectUtils.callMethod(builder, "setSize", new Object[] {
                    width, height
            }, new Class[] {
                    int.class, int.class
            });
            ReflectUtils.callMethod(builder, "setSourceCrop", rect);
            Object args = ReflectUtils.callMethod(builder, "build");

            Object buf = ReflectUtils.callStaticMethod(SurfaceControl.class, "captureDisplay", args);
            Logger.d(TAG, "buf " + buf);
            Bitmap bitmap = (Bitmap) ReflectUtils.callMethod(buf, "asBitmap");
            Logger.d(TAG, "bitmap " + bitmap);
            return bitmap;



            /*return (Bitmap) ReflectUtils.callStaticMethod(SurfaceControl.class, "screenshot", new Object[]{
                    rect, (int) (rect.width() / ratio), (int) (rect.height() / ratio), displayManager.getDisplays()[0].getRotation()
            }, new Class[]{
                    Rect.class, int.class, int.class, int.class
            });*/
        } catch (Throwable e) {
            Logger.e(TAG, "Can't take screenshot.", e);
            return null;
        }
    }
}
