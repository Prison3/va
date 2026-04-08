package com.android.actor.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;

import com.android.actor.monitor.Logger;

import java.util.HashSet;
import java.util.Set;

public class PermissionUtils {
    private final static String TAG = PermissionUtils.class.getSimpleName();

    public static boolean hasServicePermission(Context context, Class<?> serviceClass) {
        int enable = 0;
        try {
            enable = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            Logger.e(TAG, "Cannot check ACCESSIBILITY_ENABLED of secure setting: " + e);
        }

        TextUtils.SimpleStringSplitter split = new TextUtils.SimpleStringSplitter(':');
        if (enable == 1) {
            String settingValue = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            Logger.d(TAG, "ENABLED_ACCESSIBILITY_SERVICES: " + settingValue);
            if (settingValue != null) {
                split.setString(settingValue);
                while (split.hasNext()) {
                    String accessibilityService = split.next();
                    if (accessibilityService.contains(serviceClass.getSimpleName())) {
                        return true;
                    }
                }
            }
        } else {
            Logger.e(TAG, "Accessibility is not enable in secure setting.");
        }
        return false;
    }

    public static void openServicePermission(Context context, Class serviceClass) {
        Set<ComponentName> enabledServices = getEnabledServicesFromSettings(context, serviceClass);
        if (null == enabledServices) {
            return;
        }
        ComponentName toggledService = ComponentName.unflattenFromString(context.getPackageName() + "/" + serviceClass.getName());
        final boolean accessibilityEnabled = true;
        enabledServices.add(toggledService);
        // Update the enabled services setting.
        StringBuilder enabledServicesBuilder = new StringBuilder();
        for (ComponentName enabledService : enabledServices) {
            enabledServicesBuilder.append(enabledService.flattenToString());
            enabledServicesBuilder.append(":");
        }
        final int enabledServicesBuilderLength = enabledServicesBuilder.length();
        if (enabledServicesBuilderLength > 0) {
            enabledServicesBuilder.deleteCharAt(enabledServicesBuilderLength - 1);
        }
        Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enabledServicesBuilder.toString());
        // Update accessibility enabled.
        Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, accessibilityEnabled ? 1 : 0);
    }

    public static Set<ComponentName> getEnabledServicesFromSettings(Context context, Class serviceClass) {
        String enabledServicesSetting = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServicesSetting == null) {
            enabledServicesSetting = "";
        }
        Set<ComponentName> enabledServices = new HashSet<ComponentName>();
        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServicesSetting);
        while (colonSplitter.hasNext()) {
            String componentNameString = colonSplitter.next();
            ComponentName enabledService = ComponentName.unflattenFromString(componentNameString);
            if (enabledService != null) {
                if (enabledService.flattenToString().contains(serviceClass.getSimpleName())) {
                    return null;
                }
                enabledServices.add(enabledService);
            }
        }
        return enabledServices;
    }

    public static void jumpSystemAccessibilitySetting(Context context) {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}