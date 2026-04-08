package com.android.actor.fi;

import android.content.Context;

import com.android.actor.ActApp;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.Callback;
import com.android.actor.utils.ReflectUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

// v335.0.0.39.93
public class Instagram373511179 {

    private static final String TAG = "Instagram373511179";
    private static Object X_OYG;
    private static ExecutorService sExecutor = Executors.newSingleThreadExecutor();

    public static void piRequest(String nonce, String cloudProjectNumber, Callback.C2<Boolean, String> callback) {
        sExecutor.execute(() -> {
            _piRequest(nonce, cloudProjectNumber, callback);
        });
    }

    public static void _piRequest(String nonce, String cloudProjectNumber, Callback.C2<Boolean, String> callback) {
        try {
            Logger.d(TAG, "piRequest nonce " + nonce + ", cloudProjectNumber " + cloudProjectNumber);
            Class clsSj9 = ReflectUtils.findClass("X.Sj9", ActApp.classLoader());
            if (X_OYG == null) {
                Class cls50P = ReflectUtils.findClass("X.50P", ActApp.classLoader());
                XposedHelpers.findAndHookMethod(clsSj9, "CrC", cls50P, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        /**
                         11:26:42.512 23395-23395 Rocket_Actor       pid-23395               D  [Beer] [Entrance]   A00: null
                         11:26:42.512 23395-23395 Rocket_Actor       pid-23395               D  [Beer] [Entrance]   A01: IntegrityTokenResponse{token=eyJhbGciOiJBMjU2S1ciLCJlbmMiOiJBMjU2R0NNIn0._2rBC8iMM2wm5zuoC9HvCPLyYIFnES3g5p4HonOT0T9kNpg5ucTRlA.0cZqjCdoeyqZ4_ld.MOw84D6n4h5zqm_H3u08w7e12PHHHvbhXUXhbsuGqwm38tJkw6z0L5VS0XF1P_S-iVEsiiWfwidsdDul_I5O365kJtTqFH963hvIFK6zV9bGg3Fcs4tGZHnvdvQjqbqanYPG-yfKleJaRiOk82__DbySAkX9LyY43wLmb2DfIBabFQFl2IYV12lx42wX9gCdI9iOUPtJop5r3_drXxyPTcgD8JhLidKLsXRd449VzpPVVXURDXgQsoysdaEfNwUG0mHBEC1A6lEcRMFPI9JB-60tvq__6dGRqd0dCN7-nkZnQuJs0or5NJOna9EdZBGWZT7xQ88l2_2gu5qoKzK9fwKkJGSJ5N-dD4phBHzSC6dt8OgGC46dYEXyHMJs32afdG1Z7IcAqqt9fmuFVW4K2lZpMijUVev3OS2gbuQdxd3luxtEWV8eNiGVTQLGtZrqPdrNxjeCd-rzS7oLVlzple1eLBaKNT2XUA-8wpaIIvQLUEUyqKlxIzAyXtUUptWRYGXMPhRoQNmetDbgZ5L9SU0eHLlLO0q42m_vltQ7hkLZwI2QqXIuVu26z-o1UBeXDZiffXZvCCyvA8bHWAcoInqBK9dC3Zg9svU0CcpglAXKFtgfHAXMD_U3esvvN6m-aqqrFFPOc5Zh5PivIgI0jAPdenbEhp1S6OHZHpmb9dgLFuJhfYWwL6WhwqQ58k6CIYv0awQYVjiUq9720v5ksstTaZ8jmebEQ9myzxQOJanVBJCvxoWlnGhgpT4RicpHko92QvZAMfeDqTkrpQM7gywVuE38av_1mNdWr-ty7LpaD8Z3k79runJ336z3MqlMc_hIGe-tRmAn4TEAduGslMyIjZejDPCUdCrQlT-a7AsornOhdz2KRCnZ0OdA-_9_cOnXkIOckX4SK1rc5fccwA0UOde8tDWpzOzTb4qngOUDaojqZRgRgT3MXSSS8I27ou_kxqEBY68-SQEI8j5_8yXkP5kRdUDVU2wai9lzN-LoYlJ7pyj3AmzC9guqxLKANYznO11-GMWmyBo2x6fRHislxAUcTBB27Y2bRZweVEIYX-OG1u5sT4V7BHDL-gJ_Vc27r4CNg3rGtKkpSVsGomVOBfbdjRtojeD7.oaWssjJTy181DI8DVw-lFQ}
                         11:26:42.512 23395-23395 Rocket_Actor       pid-23395               D  [Beer] [Entrance]   A02: true
                         11:26:42.512 23395-23395 Rocket_Actor       pid-23395               D  [Beer] [Entrance]   A03: X.50Q@fe413e0
                         11:26:42.512 23395-23395 Rocket_Actor       pid-23395               D  [Beer] [Entrance]   A04: java.lang.Object@16ae699
                         11:26:42.512 23395-23395 Rocket_Actor       pid-23395               D  [Beer] [Entrance]   A05: false
                         */
                        /**
                         11:29:44.151 24119-24119 Rocket_Actor       pid-24119               D  [Beer] [Entrance]   A00: X.Nh4: -3: Integrity API error (-3): Network error: unable to obtain integrity details.
                         Ask the user to check for a connection.
                         (https://developer.android.com/google/play/integrity/reference/com/google/android/play/core/integrity/model/IntegrityErrorCode.html#NETWORK_ERROR).
                         11:29:44.151 24119-24119 Rocket_Actor       pid-24119               D  [Beer] [Entrance]   A01: null
                         11:29:44.151 24119-24119 Rocket_Actor       pid-24119               D  [Beer] [Entrance]   A02: true
                         11:29:44.151 24119-24119 Rocket_Actor       pid-24119               D  [Beer] [Entrance]   A03: X.50Q@1a68e0c
                         11:29:44.151 24119-24119 Rocket_Actor       pid-24119               D  [Beer] [Entrance]   A04: java.lang.Object@c7a5355
                         11:29:44.151 24119-24119 Rocket_Actor       pid-24119               D  [Beer] [Entrance]   A05: false
                         */
                        Object C50P = param.args[0];
                        Logger.d(TAG, "PI result " + C50P);
                        Logger.clsFieldValues(TAG, C50P);

                        Object A01 = ReflectUtils.getObjectField(C50P, "A01");
                        if (A01 != null) {
                            String token = (String) ReflectUtils.getObjectField(A01, "A00");
                            Logger.d(TAG, " token " + token);
                            callback.onResult(true, token);
                        } else {
                            String msg = "C50P error.";
                            Object err = ReflectUtils.getObjectField(C50P, "A00");
                            if (err != null) {
                                msg = err.toString();
                            }
                            callback.onResult(false, msg);
                        }
                    }
                });

                Class clsOYG = ReflectUtils.findClass("X.OYG", ActApp.classLoader());
                X_OYG = ReflectUtils.newInstance(clsOYG, new Object[] {
                        ActApp.currentApplication()
                }, new Class[] {
                        Context.class
                });
            }

            Object A00 = ReflectUtils.getObjectField(X_OYG, "A00");
            A00 = ReflectUtils.getObjectField(A00, "A00");
            Object PMX = ReflectUtils.callMethod(A00, "A6a");
            Logger.d(TAG, "PMX " + PMX);

            Class clsNpY = ReflectUtils.findClass("X.NpY", ActApp.classLoader());
            Class clsOHR = ReflectUtils.findClass("X.OHR", ActApp.classLoader());
            Object NpY = ReflectUtils.newInstance(clsNpY, nonce);
            Object task = ReflectUtils.callMethod(PMX, "Dwv", new Object[] {
                    NpY
            }, new Class[] {
                    clsOHR
            });
            Logger.d(TAG, "task " + task);

            Class clsQI4 = ReflectUtils.findClass("X.QI4", ActApp.classLoader());
            Class clsTj2 = ReflectUtils.findClass("X.Tj2", ActApp.classLoader());
            Object QI4 = ReflectUtils.newInstance(clsQI4);
            Object Sj9 = ReflectUtils.newInstance(clsSj9, QI4);
            ReflectUtils.callMethod(task, "A03", new Object[] {
                    Sj9
            }, new Class[] {
                    clsTj2
            });
        } catch (Throwable e) {
            Logger.e(TAG, "Request exception.", e);
            callback.onResult(false, e.toString());
        }
    }
}
