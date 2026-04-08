package com.android.actor.device;

import android.hardware.ICameraService;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.ReflectUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CameraMockManager {
    private final static String TAG = CameraMockManager.class.getSimpleName();
    public final static String MOCK_MEDIA_DIR = "/data/new_stage/camera";
    private ICameraService mService;
    private static final CameraMockManager instance = new CameraMockManager();
    public static CameraMockManager getInstance(){
        return instance;
    }

    private CameraMockManager(){
        try {
            Class<?> cameraManagerGoalClz = ReflectUtils.findClass("android.hardware.camera2.CameraManager$CameraManagerGlobal", ClassLoader.getSystemClassLoader());
            Object cameraManagerGoalObj = ReflectUtils.callStaticMethod(cameraManagerGoalClz, "get");
            mService = (ICameraService) ReflectUtils.callMethod(cameraManagerGoalObj, "getCameraService");
        }catch (Throwable e) {
            Logger.e(TAG, e);
        }
    }

    public boolean setMockData(String name, int type){
        try {
            int result = mService.setMockSource(String.format("%s/%s", MOCK_MEDIA_DIR, name), type);
            Logger.i(TAG, "setMockSource result: " + result);
            return result == 0;
        }catch (Throwable e) {
            Logger.e(TAG, e);
        }
        return false;
    }

    public List<String> getMediaList(){
        ArrayList<String> list = new ArrayList<>();
        File f = new File(MOCK_MEDIA_DIR);
        Collections.addAll(list, Objects.requireNonNull(f.list()));
        return list;
    }
}
