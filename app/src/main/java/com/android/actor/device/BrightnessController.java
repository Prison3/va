package com.android.actor.device;

import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.SurfaceControl;
import android.widget.Toast;

import com.android.actor.ActApp;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.ReflectUtils;

import org.apache.commons.io.FileUtils;

import java.io.File;

public class BrightnessController {

    private static final String TAG = BrightnessController.class.getSimpleName();
    public static final int POWER_MODE_OFF = 0;
    public static final int POWER_MODE_NORMAL = 2;
    public static final int DEFAULT_TIMEOUT = 30 * 60;
    private static final File DISABLE_FILE = new File("/data/new_stage/disable_screen_black");

    private static BrightnessController sInstance;
    private Handler mHandler = new Handler(Looper.getMainLooper());

    public synchronized static BrightnessController instance() {
        if (sInstance == null) {
            sInstance = new BrightnessController();
        }
        return sInstance;
    }

    public boolean isEnabled() {
        return !DISABLE_FILE.exists();
    }

    public void setEnable(boolean enable) {
        try {
            if (enable) {
                if (DISABLE_FILE.exists()) {
                    FileUtils.forceDelete(DISABLE_FILE);
                }
            } else {
                DISABLE_FILE.createNewFile();
            }
        } catch (Throwable e) {
            Toast.makeText(ActApp.getInstance(), "Error.", Toast.LENGTH_SHORT).show();
        }
    }

    public void acquireScreenReadable() {
        acquireScreenReadable(DEFAULT_TIMEOUT);
    }

    /**
     * @param timeout
     *   > 0, seconds to off
     *   = 0, off right now
     *   < 0, never off
     */
    public void acquireScreenReadable(int timeout) {
        if (DISABLE_FILE.exists()) {
            return;
        }
        mHandler.removeCallbacksAndMessages(null);
        if (timeout > 0 && timeout < 30) {
            timeout = 30;
        }
        if (timeout != 0) {
            mHandler.post(() -> {
                setDisplayPowerMode(POWER_MODE_NORMAL);
            });
        }
        if (timeout >= 0) {
            Logger.i(TAG, "Dispatch " + timeout + "s to off screen.");
            mHandler.postDelayed(() -> {
                setDisplayPowerMode(POWER_MODE_OFF);
            }, timeout * 1000);
        }
    }

    private void setDisplayPowerMode(int mode) {
        /*try {
            Logger.i(TAG, "setDisplayPowerMode " + mode);
            IBinder displayToken = (IBinder) ReflectUtils.callStaticMethod(SurfaceControl.class, "getInternalDisplayToken");
            ReflectUtils.callStaticMethod(SurfaceControl.class, "setDisplayPowerMode", new Object[] {
                    displayToken, mode
            }, new Class[] {
                    IBinder.class, int.class
            });
        } catch (Throwable e) {
            Logger.e(TAG, "setDisplayPowerMode exception.", e);
        }*/
    }

    public void setDisplayOn(boolean on){
        if(on){
            setDisplayPowerMode(POWER_MODE_NORMAL);
        }else{
            setDisplayPowerMode(POWER_MODE_OFF);
        }
    }
}
