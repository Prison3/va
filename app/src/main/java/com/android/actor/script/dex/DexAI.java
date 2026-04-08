package com.android.actor.script.dex;

import android.os.RemoteException;

import com.android.actor.models.SliderAI;
import com.android.actor.monitor.Logger;
import com.android.internal.ds.IDexAI;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.Arrays;
import java.util.Map;

public class DexAI extends IDexAI.Stub {

    private static final String TAG = DexAI.class.getSimpleName();

    @Override
    public boolean loadModel(String str) throws RemoteException {
        LuaTable luaTable = new LuaTable();
        luaTable.set("ok", LuaValue.TRUE);
        String lowerCase = str.toLowerCase();
        lowerCase.hashCode();
        if (lowerCase.equals("slider")) {
//            RotationAI.getInstance().release();
            Logger.d(TAG, "loadModel: " + lowerCase);
            if (!SliderAI.getInstance().isReady() && !SliderAI.getInstance().reload()) {
                Logger.w(TAG, "failed to load slider model");
                luaTable.set("ok", LuaValue.FALSE);
                luaTable.set("reason", "failed to load slider model");
                return false;
            }
            return true;
        }
//        else if (lowerCase.equals("rotation")) {
//            SliderAI.getInstance().release();
//            if (!RotationAI.getInstance().isReady() && !RotationAI.getInstance().reload()) {
//                Logger.w(TAG, "failed to load rotation model");
//                luaTable.set("ok", LuaValue.FALSE);
//                luaTable.set("reason", "failed to load rotation model");
//            }
//            return luaTable;
//        }
        else {
            return false;
        }
    }

    @Override
    public String sliderIdentify(byte[] bArr) throws RemoteException {
        return sliderIdentify(bArr, false);
    }

    public String sliderIdentify(byte[] bArr, boolean z) {
        return sliderIdentify(bArr, 0, 0, 0, 5, z);
    }

    public String sliderIdentify(byte[] bArr, int i, int i2, int i3, int i4, boolean z) {
        if (!SliderAI.getInstance().isReady()) {
            return "model is not ready, please load first";
        }
        else {
            Logger.d(TAG, "sliderIdentify: " + Arrays.toString(bArr));
            Logger.d(TAG,"model is ready");
        }
        int identify = SliderAI.getInstance().identify(bArr, i, i2, i3, i4, z);
        if (identify == -1) {
            return "identify nothing";
        } else if (identify == 0) {
            return "number of identified boxes less than 2";
        } else {
            return String.valueOf(identify);
        }
    }
}
