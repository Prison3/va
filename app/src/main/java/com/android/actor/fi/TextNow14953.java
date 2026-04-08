package com.android.actor.fi;

import android.content.Context;

import com.android.actor.ActApp;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.Callback;
import com.android.actor.utils.ReflectUtils;

// v24.41.1.0
public class TextNow14953 {

    private static final String TAG = "TextNow14953";

    public static void piRequest(String nonce, String cloudProjectNumber, Callback.C2<Boolean, String> callback) {
        try {
            Logger.d(TAG, "piRequest nonce " + nonce + ", cloudProjectNumber " + cloudProjectNumber);
            Class clsGooglePlayIntegrityNonceValidator = ReflectUtils.findClass("me.textnow.api.android.GooglePlayIntegrityNonceValidator", ActApp.classLoader());
            Object validator = ReflectUtils.newInstance(clsGooglePlayIntegrityNonceValidator, new Object[] {
                    ActApp.currentApplication()
            }, new Class[] {
                    Context.class
            });
            Object response = ReflectUtils.callMethod(validator, "verifyNonce", new Object[] {
                    nonce, null
            }, new Class[] {
                    String.class, ReflectUtils.findClass("kotlin.coroutines.d", ActApp.classLoader())
            });

            if (response.getClass().getName().equals("me.textnow.api.android.Response$Success")) {
                Object data = ReflectUtils.getObjectField(response, "data");
                String token = (String) ReflectUtils.getObjectField(data, "a");
                Logger.d(TAG, " token " + token);
                callback.onResult(true, token);
            } else {
                callback.onResult(false, response.toString());
            }
        } catch (Throwable e) {
            Logger.e(TAG, "Request exception.", e);
            callback.onResult(false, e.toString());
        }
    }
}
