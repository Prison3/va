package com.android.actor.script.lua;

import android.util.Base64;
import com.android.actor.models.RotationAI;
import com.android.actor.models.SliderAI;
import com.android.actor.monitor.Logger;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import java.util.Arrays;

public class LuaAI {
    private static final String TAG = "LuaAI";

    public LuaTable sliderIdentify(byte[] bArr) {
        return sliderIdentify(bArr, false);
    }

    public LuaTable sliderIdentify(byte[] bArr, boolean z) {
        return sliderIdentify(bArr, 0, 0, 0, 5, z);
    }

    public LuaTable sliderIdentify(byte[] bArr, int i, int i2, int i3, int i4, boolean z) {
        LuaTable luaTable = new LuaTable();
        if (!SliderAI.getInstance().isReady()) {
            luaTable.set("ok", LuaValue.FALSE);
            luaTable.set("reason", "model is not ready, please load first");
            return luaTable;
        }
        else {
            Logger.d(TAG, "sliderIdentify: " + Arrays.toString(bArr));
            Logger.d(TAG,"model is ready");
        }
        int identify = SliderAI.getInstance().identify(bArr, i, i2, i3, i4, z);
        if (identify == -1) {
            luaTable.set("ok", LuaValue.FALSE);
            luaTable.set("reason", "identify nothing");
            return luaTable;
        } else if (identify == 0) {
            luaTable.set("ok", LuaValue.FALSE);
            luaTable.set("reason", "number of identified boxes less than 2");
            return luaTable;
        } else {
            luaTable.set("ok", LuaValue.TRUE);
            luaTable.set("dist", identify);
            luaTable.set("reason", LuaValue.NIL);
            return luaTable;
        }
    }

    public LuaTable sliderIdentify(String str, int i, int i2, int i3, int i4, boolean z) {
        return sliderIdentify(Base64.decode(str, 0), i, i2, i3, i4, z);
    }

    public LuaTable sliderIdentify(String str, boolean z) {
        return sliderIdentify(str, 0, 0, 0, 5, z);
    }

    public LuaTable sliderIdentify(String str) {
        return sliderIdentify(str, false);
    }

    public LuaTable rotationIdentify(byte[] bArr) {
        return rotationIdentify(bArr, false);
    }

    public LuaTable rotationIdentify(byte[] bArr, boolean z) {
        LuaTable luaTable = new LuaTable();
//        if (!RotationAI.getInstance().isReady()) {
//            luaTable.set("ok", LuaValue.FALSE);
//            luaTable.set("reason", "model is not ready, please load first");
//            return luaTable;
//        }
//        int identify = RotationAI.getInstance().identify(bArr, z);
//        if (identify == -1) {
//            luaTable.set("ok", LuaValue.FALSE);
//            luaTable.set("reason", "identify nothing");
//            return luaTable;
//        }
//        luaTable.set("ok", LuaValue.TRUE);
//        luaTable.set("angle", identify);
//        luaTable.set("reason", LuaValue.NIL);
        return luaTable;
    }

    public LuaTable rotationIdentify(String str) {
        return rotationIdentify(str, false);
    }

    public LuaTable rotationIdentify(String str, boolean z) {
        return rotationIdentify(Base64.decode(str, 0), z);
    }

    public LuaTable loadModel(String str) {
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
            }
            return luaTable;
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
            return luaTable;
        }
    }
}
