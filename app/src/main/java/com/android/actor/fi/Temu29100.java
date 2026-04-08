package com.android.actor.fi;

import static com.android.actor.fi.FIEntryPoint.EXPRESS_INTEGRITY_PACKAGES;
import static com.android.actor.fi.FIEntryPoint.sPackageName;

import android.content.Context;

import com.android.actor.ActApp;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.BlockingReference;
import com.android.actor.utils.Callback;
import com.android.actor.utils.ReflectUtils;

import yf1.f;
import yf1.g;

public class Temu29100 {

    private static final String TAG = "Temu29100";
    private static Object sTokenProvider;

    public static void expressIntegrityWarmup() throws Throwable {
        Thread.sleep(5000);
        while (true) {
            Logger.i(TAG, "Start warmup express integrity.");
            BlockingReference<Integer> blockingReference = new BlockingReference<>();

            Class clsRequest = ReflectUtils.findClass("cj1.c$a", ActApp.classLoader());
            Object builder = ReflectUtils.callStaticMethod(clsRequest, "c");
            ReflectUtils.callMethod(builder, "b", new Object[] {
                    158704287764L
            }, new Class[] {
                    long.class
            });
            Object request = ReflectUtils.callMethod(builder, "a");

            Class clsIntegrityManagerFactory = ReflectUtils.findClass("cj1.a", ActApp.classLoader());
            Object standardIntegrityManager = ReflectUtils.callStaticMethod(clsIntegrityManagerFactory, "a", new Object[] {
                    ActApp.currentApplication()
            }, new Class[] {
                    Context.class
            });

            Object task = ReflectUtils.callMethod(standardIntegrityManager, "a", new Object[] {
                    request
            }, new Class[] {
                    clsRequest
            });
            ReflectUtils.callMethod(task, "i", new Object[] {
                    (g) tokenProvider -> {
                        Logger.d(TAG, "tokenProvider " + tokenProvider);
                        sTokenProvider = tokenProvider;
                        blockingReference.put(3600);
                    }
            }, new Class[] {
                    g.class
            });
            ReflectUtils.callMethod(task, "f", new Object[] {
                    (f) e -> {
                        Logger.e("Failed to warmup express integrity, sleep a while and retry.", e);
                        blockingReference.put(5);
                    }
            }, new Class[] {
                    f.class
            });

            int seconds = blockingReference.take();
            Thread.sleep(seconds * 1000);
        }
    }

    public static void piExpressRequest(String nonce, String cloudProjectNumber, Callback.C2<Boolean, String> callback) {
        try {
            Logger.d(TAG, "piExpressRequest nonce " + nonce + ", cloudProjectNumber " + cloudProjectNumber);
            if (sTokenProvider == null) {
                callback.onResult(false, "No token provider.");
                return;
            }
            if (Long.parseLong(cloudProjectNumber) != EXPRESS_INTEGRITY_PACKAGES.get(sPackageName)) {
                callback.onResult(false, "Cloud project number not match, " + cloudProjectNumber);
                return;
            }

            Class clsRequest = ReflectUtils.findClass("cj1.c$d", ActApp.classLoader());
            Object builder = ReflectUtils.callStaticMethod(clsRequest, "b");
            ReflectUtils.callMethod(builder, "b", nonce);
            Object request = ReflectUtils.callMethod(builder, "a");

            Object task = ReflectUtils.callMethod(sTokenProvider, "a", new Object[] {
                    request
            }, new Class[] {
                    clsRequest
            });
            ReflectUtils.callMethod(task, "i", new Object[] {
                    (g) response -> {
                        Logger.d(TAG, "onSuccess " + response);
                        Logger.d(TAG, " " + response.getClass());
                        try {
                            String token = (String) ReflectUtils.callMethod(response, "a");
                            Logger.d(TAG, " " + token);
                            callback.onResult(true, token);
                        } catch (Throwable e) {
                            throw new RuntimeException(e);
                        }
                    }
            }, new Class[] {
                    g.class
            });
            ReflectUtils.callMethod(task, "f", new Object[] {
                    (f) e -> {
                        Logger.e(TAG, "onFailure", e);
                        callback.onResult(false, e.toString());
                    }
            }, new Class[] {
                    f.class
            });
        } catch (Throwable e) {
            Logger.e(TAG, "Request exception.", e);
            callback.onResult(false, e.toString());
        }
    }
}
