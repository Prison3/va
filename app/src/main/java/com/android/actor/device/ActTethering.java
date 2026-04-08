package com.android.actor.device;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.LinkAddress;
import android.net.TetheringManager;
import android.net.TetheringManager.TetheringRequest;
import android.net.TetheringManager.StartTetheringCallback;

import com.android.actor.monitor.Logger;
import com.android.actor.utils.ReflectUtils;

public class ActTethering {

    private static final String TAG = "ActTethering";
    private static final int TETHERING_USB = 1;

    public static void startTethering(Context context) {
        Logger.d(TAG, "startTethering");
        @SuppressLint("WrongConstant") TetheringManager tetheringManager = (TetheringManager) context.getSystemService("tethering");
        try {
            LinkAddress ip = (LinkAddress) ReflectUtils.newInstance(LinkAddress.class, DeviceNumber.calcWiredIP() + "/24");
            LinkAddress gateway = (LinkAddress) ReflectUtils.newInstance(LinkAddress.class, DeviceNumber.calcGateway() + "/24");

            TetheringRequest request = new TetheringRequest.Builder(TETHERING_USB)
                    .setStaticIpv4Addresses(ip, gateway)
                    .build();
            tetheringManager.startTethering(request, context.getMainExecutor(), new StartTetheringCallback() {
                @Override
                public void onTetheringStarted() {
                    Logger.i(TAG, "onTetheringStarted");
                }

                @Override
                public void onTetheringFailed(int error) {
                    Logger.e(TAG, "onTetheringFailed " + error);
                }
            });
        } catch (Throwable e) {
            Logger.e(TAG, "Error to startTethering", e);
        }
    }

    public static void stopTethering(Context context) {
        Logger.d(TAG, "stopTethering");
        @SuppressLint("WrongConstant") TetheringManager tetheringManager = (TetheringManager) context.getSystemService("tethering");
        tetheringManager.stopTethering(TETHERING_USB);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
        }
    }
}
