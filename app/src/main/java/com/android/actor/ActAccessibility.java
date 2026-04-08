package com.android.actor;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.PowerManager;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.Nullable;

import com.android.actor.control.api.InputKeyCode;
import com.android.actor.device.BatteryChangeReceiver;
import com.android.actor.device.BatteryController;
import com.android.actor.device.BrightnessController;
import com.android.actor.monitor.Logcat;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.PermissionUtils;
import com.android.actor.utils.db.DBHelper;
import com.android.actor.utils.filebeat.FileBeatRunner;
import com.android.actor.utils.shell.Root;
import com.android.actor.utils.shell.Shell;

import java.lang.reflect.Method;

import static com.android.actor.MainActivity.RECEIVER_SERVICE_READY;


public class ActAccessibility extends AccessibilityService {
    private final static String TAG = ActAccessibility.class.getSimpleName();
    private final String notificationId = "channelId";

    private static ActAccessibility sInstance = null;
    private static boolean isStart = false;

    public BatteryController mBatteryController;
    public BatteryChangeReceiver mBatteryReceiver;
    private Logcat mLogcat;
    private FileBeatRunner mFileBeatRunner;
    private PowerManager.WakeLock mWakeLock;
    private View mMask;
    private boolean isMasked;

    /**
     * Cannot override onBind(). SingleInstance way is used instead although it's not smart.
     */
    @Nullable
    public static ActAccessibility getInstance() {
        return sInstance;
    }

    @Override
    public AccessibilityNodeInfo getRootInActiveWindow() {
        AccessibilityNodeInfo node = super.getRootInActiveWindow();
        if (node == null) {
            Logger.e(TAG, "Get root null.");
        }
        return node;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.d(TAG, "onCreate");
        isStart = false;
        checkPermission(this);
        register();
        mBatteryController = new BatteryController(mBatteryReceiver);
        mBatteryController.start();

        mLogcat = new Logcat(getExternalFilesDir("log").getPath());
        mLogcat.startCollect();
        mFileBeatRunner = new FileBeatRunner();
        mFileBeatRunner.start();

        DisplayManager displayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        if (displayManager.getDisplays()[0].getState() == Display.STATE_OFF) {
            Logger.w(TAG, "Screen is off, press menu button turn it on.");
            InputKeyCode.inputKeyCode(KeyEvent.KEYCODE_MENU);
        }
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, ActAccessibility.class.getSimpleName());
        mWakeLock.acquire();
        BrightnessController.instance().acquireScreenReadable();

        mMask = new View(this);
        mMask.setBackgroundColor(Color.BLACK);
        mMask.setAlpha(0.8f);
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Logger.d(TAG, "onServiceConnected");
        sInstance = this;
        setupConfig();
        Logger.d(TAG, "flags: " + getServiceInfo().flags + ", isRestricted: " + isRestricted() + ", windows cnt: " + getWindows().size());
        isStart = true;
        startForegroundService();
        sendBroadcast(new Intent(RECEIVER_SERVICE_READY));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.d(TAG, "onDestroy");
        stopForeground(true);
        sInstance = null;
        isStart = false;
        unregisterReceiver(mBatteryReceiver);
        DBHelper.getInstance().getDatabase().close();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    }

    @Override
    public void onInterrupt() {

    }

    public static boolean isStart() {
        return ActAccessibility.getInstance() != null && isStart;
    }

    private void startForegroundService() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        //创建NotificationChannel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String notificationName = "channelName";
            NotificationChannel channel = new NotificationChannel(notificationId, notificationName, NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }
        startForeground(1, getNotification());
    }

    private Notification getNotification() {
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Rocket foreground service.")
                .setContentText("Rocket accessibility is running.");
        //设置Notification的ChannelID,否则不能正常显示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(notificationId);
        }
        return builder.build();
    }

    /**
     * Static config is set in res/xml/accessibility_service_config.xml. But not useful in pixel.
     */
    private void setupConfig() {
        AccessibilityServiceInfo asi = new AccessibilityServiceInfo();
        asi.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        asi.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK;
        asi.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS |
                AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE |
                AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY |
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS |
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            asi.flags |= AccessibilityServiceInfo.FLAG_REQUEST_SHORTCUT_WARNING_DIALOG_SPOKEN_FEEDBACK;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            asi.flags |= AccessibilityServiceInfo.FLAG_ENABLE_ACCESSIBILITY_VOLUME |
                    AccessibilityServiceInfo.FLAG_REQUEST_ACCESSIBILITY_BUTTON |
                    AccessibilityServiceInfo.FLAG_REQUEST_FINGERPRINT_GESTURES;
        }
        try {
            @SuppressLint("DiscouragedPrivateApi") Method method = asi.getClass().getDeclaredMethod("setCapabilities", int.class);
            if (method != null) {
                method.invoke(asi, AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Logger.d(TAG, "getCapabilities: " + asi.getCapabilities());
        asi.notificationTimeout = 100;
        setServiceInfo(asi);
    }


    public static void checkPermission(Context context) {
        if (!PermissionUtils.hasServicePermission(context, ActAccessibility.class)) {
            Logger.w(TAG, ActAccessibility.class.getSimpleName() + " is not in enabled accessibility service list");
            try {
                PermissionUtils.openServicePermission(context, ActAccessibility.class);
            } catch (Exception e) {
                Logger.w(TAG, e.toString());
                Logger.i(TAG, "App not get accessibility permission");
                if (Root.checkRoot()) {
                    Shell.execRootCmdSilent("settings put secure enabled_accessibility_services com.android.actor/com.android.actor.ActAccessibility");
                }
            }
        }
    }

    private void register() {
        mBatteryReceiver = new BatteryChangeReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        registerReceiver(mBatteryReceiver, filter);
        Logger.d(TAG, "register BatteryChangeReceiver");

        DBHelper.getInstance().getDatabase();
    }

    public boolean isMasked() {
        return isMasked;
    }

    public void switchDarkMask() {
        if (isMasked) {
            unsetDarkMask();
        } else {
            setDarkMask();
        }
    }

    public void setDarkMask(float alpha) {
        if (mMask != null) {
            mMask.setAlpha(alpha);
            setDarkMask();
        }
    }

    public void setDarkMask() {
        if (mMask != null) {
            Logger.d(TAG, "add flow mask.");
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            WindowManager.LayoutParams layoutParams;
            layoutParams = new WindowManager.LayoutParams();
            layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            layoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
            layoutParams.format = PixelFormat.RGBA_8888;
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            windowManager.addView(mMask, layoutParams);
            isMasked = true;
        }
    }

    public void unsetDarkMask() {
        if (mMask != null) {
            Logger.d(TAG, "remove flow mask.");
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            windowManager.removeView(mMask);
            isMasked = false;
        }
    }
}