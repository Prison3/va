package com.android.actor.utils;

import static com.android.actor.control.RocketComponent.PKG_GOOGLE_WEBVIEW;
import static com.android.actor.device.FlashConfig.NO_SELF_BUILD_WEBVIEW;

import android.os.IBinder;
import android.os.ServiceManager;
import android.webkit.IWebViewUpdateService;

import com.android.actor.control.ActPackageManager;
import com.android.actor.device.FlashConfig;
import com.android.actor.monitor.Logger;

public class WebUtils {

    private static final String TAG = WebUtils.class.getSimpleName();
    private static final String WEBVIEW = PKG_GOOGLE_WEBVIEW;

    public static void changeWebViewProvider() {
        try {
            if (FlashConfig.has(NO_SELF_BUILD_WEBVIEW)) {
                Logger.w(TAG, "No changeWebViewProvider.");
                return;
            }
            Logger.d(TAG, "changeWebViewProvider " + WEBVIEW);
            if (!ActPackageManager.getInstance().isAppInstalled(WEBVIEW)) {
                Logger.w(TAG, WEBVIEW + " is not installed.");
                return;
            }
            IBinder binder = ServiceManager.getService("webviewupdate");
            IWebViewUpdateService service = IWebViewUpdateService.Stub.asInterface(binder);
            String result = service.changeProviderAndSetting(WEBVIEW);
            if (!WEBVIEW.equals(result)) {
                throw new Exception("changeProviderAndSetting return " + result);
            }
        } catch (Throwable e) {
            Logger.e(TAG, "changeWebViewProvider exception.", e);
        }
    }
}
