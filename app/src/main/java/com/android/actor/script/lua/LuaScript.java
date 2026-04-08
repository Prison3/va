package com.android.actor.script.lua;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.android.actor.ActAccessibility;
import com.android.actor.control.ActActivityManager;
import com.android.actor.control.api.MotionEventOnce;
import com.android.actor.utils.temu.A4Watcher;
import com.android.internal.aidl.service.ParcelCreator;
import com.android.internal.aidl.service.RequestV0;
import com.android.actor.ActApp;
import com.android.actor.control.AccessibilityHelper;
import com.android.actor.control.RocketComponent;
import com.android.actor.control.ActClipboardManager;
import com.android.actor.control.ActPackageManager;
import com.android.actor.control.UiElement;
import com.android.actor.control.api.Click;
import com.android.actor.control.api.FindElement;
import com.android.actor.control.api.InputKeyCode;
import com.android.actor.control.api.RequestApi;
import com.android.actor.control.api.Swipe;
import com.android.actor.control.api.Trace;
import com.android.actor.control.api.Wait;
import com.android.actor.control.trace.TraceUtils;
import com.android.actor.device.BrightnessController;
import com.android.actor.device.DeviceInfoManager;
import com.android.actor.device.NewStage;
import com.android.actor.remote.RemoteServiceConnection;
import com.android.actor.utils.downloader.DownloadManager;
import com.android.actor.monitor.Logger;
import com.android.actor.remote.LppiManagerConnection;
import com.android.actor.utils.BlockingReference;
import com.android.actor.remote.RemoteServiceManager;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.notification.GlobalNotification;
import com.android.actor.utils.proxy.ProxyManager;
import com.android.actor.utils.shell.Shell;
import com.android.actor.utils.screen.LongScreenshot;
import com.android.actor.utils.screen.LongShot;
import com.android.actor.utils.screen.Screenshot;
import com.android.internal.aidl.service.ResponseV0;


import org.apache.commons.io.FileUtils;
import org.checkerframework.checker.units.qual.Length;
import org.json.JSONException;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaClosure;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.ResourceFinder;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.JsePlatform;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static com.android.actor.BuildConfig.DEBUG;

public class LuaScript implements ResourceFinder {
    private final static String TAG = LuaScript.class.getSimpleName();
    private final static String FOLDER_NAME = "script";
    public static int optionTimeout;
    public static int optionTimeSpan;
    public static Rect clickBound;
    public final Globals globals;
    public final LuaLogger logger = new LuaLogger();
    private final LuaIO mIO = new LuaIO();

    private final String mTaskId;
    private LuaStatus mStatus;
    private final String mCreateTime;
    private String mFinishTime = null;
    private String mRecord = null;
    private String mResult = null;

    private final LuaAI mAI;

    public enum LuaStatus {
        running,
        success,
        failed,
        stopped,
        notfound,
    }

    public LuaScript() {
        this(UUID.randomUUID().toString());
    }

    public LuaScript(String taskId) {
        optionTimeout = 10 * 1000;
        optionTimeSpan = 1000;
        clickBound = new Rect(0,
                0,
                DeviceInfoManager.getInstance().width,
                DeviceInfoManager.getInstance().height);
        Log.d(TAG, "Init luaScript with actionBound: " + clickBound);
        LuaAI luaAI = new LuaAI();
        this.mAI = luaAI;
        this.mCreateTime = ActStringUtils.getStandardTime();
        this.mTaskId = taskId;
        setState(LuaStatus.running); // 没有准备阶段的status，这里用running代替
        this.globals = JsePlatform.standardGlobals();
        // setup driver
        this.globals.set("g_driver", CoerceJavaToLua.coerce(this));
        // must set the finder here if this implements the ResourceFinder
        this.globals.finder = this;
        // set up lua log
        this.globals.set("g_log", CoerceJavaToLua.coerce(logger));
        // set up io for file store, network request...
        this.globals.set("g_io", CoerceJavaToLua.coerce(mIO));
        // set up am
        this.globals.set("g_am", CoerceJavaToLua.coerce(ActActivityManager.getInstance()));
        this.globals.set("g_pm", CoerceJavaToLua.coerce(ActPackageManager.getInstance()));
        this.globals.set("g_cm", CoerceJavaToLua.coerce(ActClipboardManager.getInstance()));
        // set up api for http request
        this.globals.set("g_api", CoerceJavaToLua.coerce(new RequestApi(logger)));
        // set up utils
        this.globals.set("g_utils", CoerceJavaToLua.coerce(LuaUtils.class));
        // shell
        this.globals.set("g_shell", CoerceJavaToLua.coerce(Shell.class));
        this.globals.set("g_ai", CoerceJavaToLua.coerce(luaAI));
        if (!ActAccessibility.isStart()) {
            logger.e("Init lua but accessibility is not start !!!");
        }
    }

    public String getTaskId() {
        return mTaskId;
    }

    public void setState(LuaStatus status) {
        Logger.i(TAG, "Lua status " + status.name());
        mStatus = status;
        SPUtils.putString(SPUtils.ModuleFile.script, "status", mStatus.name());
        if (mStatus == LuaStatus.running) {
            record();
        } else if (mStatus != LuaStatus.notfound) {
            mFinishTime = ActStringUtils.getStandardTime();
            mRecord = logger.getRecordLogs();
            record();
        }
        GlobalNotification.notifyObserver(GlobalNotification.NOTIFY_TASK_STATUS, null);
    }

    public LuaStatus getState() {
        return mStatus;
    }

    public InputStream findResource(String fileName) {
        try {
            if (DEBUG && !fileName.startsWith("http")) {
                InputStream is = ActApp.getInstance().getAssets().open(fileName);
                Logger.d(TAG, is);
                return is;
            } else {
                BlockingReference<File> ref = new BlockingReference();
                if (fileName.startsWith("http")) {
                    File file = DownloadManager.instance().getFile(fileName);

                    // TODO: check to update.
                    if (file != null) {
                        FileUtils.forceDelete(file);
                        file = null;
                    }

                    if (file == null) {
                        DownloadManager.instance().addDownload(fileName, (url, _file, reason) -> {
                            ref.put(_file);
                        });
                    } else {
                        ref.put(file);
                    }
                } else {
                    String path = ActApp.getInstance().getFilesDir().getAbsolutePath() + File.separator + FOLDER_NAME + File.separator + fileName;
                    ref.put(new File(path));
                }

                File file = ref.take();
                Logger.d(TAG, "load from " + file);
                return new FileInputStream(file);
            }
        } catch (Throwable e) {
            Logger.e(TAG, "LuaScript findResource exception.", e);
            return null;
        }
    }

    private void record() {
        Map<String, String> map = new HashMap<>();
        map.put("taskId", mTaskId);
        map.put("status", mStatus.name());
        map.put("createTime", mCreateTime);
        map.put("finishTime", mFinishTime);
        map.put("record", mRecord);
        map.put("result", mResult);
        SPUtils.putStringMap(SPUtils.ModuleFile.script, map);
    }

    @NonNull
    @Override
    public String toString() {
        return String.format("[Lua]-> threadId: %s, taskId: %s, createTime: %s", Thread.currentThread().toString(), getTaskId(), mCreateTime);
    }

    // ============================ default config ==============================

    /**
     * 操作间隔单位时间。用于
     * 超时重试次数：例如'waitElement'超时是10000ms，会重试 10000/'time' 次
     * 单次操作休眠：例如'click'之后会自动等待 'time' 时间
     *
     * @param time 毫秒
     */
    public void setOptionTimeSpan(int time) {
        logger.d(TAG, "setOptionTimeSpan: " + time);
        optionTimeSpan = time;
    }

    /**
     * 默认超时时间，主要用于wait相关的操作，默认 10 000 ms
     *
     * @param timeout 毫秒
     */
    public void setOptionTimeout(int timeout) {
        logger.d(TAG, "setOptionTimeout: " + timeout);
        optionTimeout = timeout;
    }

    /**
     * 设置代理
     *
     * @param pkgName 目标app包名
     * @param proxy   代理，例如 192.168.2.1:8889
     */
    public void setAppProxy(String pkgName, String proxy) {
        try {
            ProxyManager.getInstance().addProxy(pkgName, 0, proxy);
        } catch (Exception e) {
            e.printStackTrace();
            logger.e("setAppProxy", e);
        }
    }

    /**
     * 移除代理
     *
     * @param pkgName 目标app包名
     */
    public void removeAppProxy(String pkgName) {
        ProxyManager.getInstance().removeProxy(pkgName, 0);
    }

    /**
     * 打开lppi manager，再开关lppi模块，等同于在lppi manager里面按模块的开关
     *
     * @param pkgName lppi 模块包名
     * @param on      开或者关
     */
    public void switchLppiModule(String pkgName, boolean on) {
        // ActActivityManager.getInstance().moveToFront(RocketComponent.PKG_LPPI_INSTALLER);
        LppiManagerConnection connection = new LppiManagerConnection();
        connection.switchModule(pkgName, on, (success, reason) -> {
            logger.i("switch lppi module " + (on ? "on" : "off") + " " + pkgName + ", result " + success + " " + reason);
        });
    }

    /**
     * 设置脚本结果，用于在check里面返回
     *
     * @param result result
     */
    public void setResult(String result) {
        Logger.i(TAG, "set result: " + result);
        mResult = result;
    }

    public void setResult(LuaTable table) {
        try {
            mResult = LuaUtils.tableToJsonStr(table);
            Logger.i(TAG, "set result: " + mResult);
        } catch (JSONException e) {
            e.printStackTrace();
            mResult = e.toString();
        }
    }

    public String getResult() {
        return mResult;
    }

    /**
     * 获取设备序列号
     */
    public String getSerial() {
        return DeviceInfoManager.getInstance().getSerial();
    }

    /**
     * 调用改机
     *
     * @param pkgName    app包名
     * @param profileId  改机参数id
     * @param jsonParams json参数字符串
     */
    public String modifyDevice(String pkgName, int profileId, String jsonParams) {
        if (NewStage.instance().isReady()) {
            NewStage.instance().modify(pkgName, profileId, jsonParams);
            return null;
        } else {
            return "DeviceMock is not ready";
        }
    }

    /**
     * 获取改机参数
     *
     * @param pkgName 目标app包名
     * @return 错误返回null，改机参数json字符串
     */
    public String getDeviceModified(String pkgName) {
        if (pkgName == null
                || pkgName.isEmpty()
                || !ActPackageManager.getInstance().isAppInstalled(pkgName)
                || NewStage.instance().isReady()) {
            return null;
        }
        return NewStage.instance().getPackageParameters(pkgName);
    }

    /**
     * 设置点击范围，如果超出这个范围，点击无效
     */
    public void setClickBound(int left, int top, int right, int bottom) {
        clickBound = new Rect(left, top, right, bottom);
    }

    // ============================ find element ==============================

    /**
     * 根据描述查找一个元素，如果某一个为null则不匹配该字段
     *
     * @param cond 查找條件
     * @return 找不到返回null，在Lua里面为nil
     */
    public static UiElement findElement(LuaTable cond) throws JSONException {
        List<UiElement> list = FindElement.findElementMatched(cond);
        return list.size() > 0 ? list.get(0) : null;
    }

    /**
     * 根据描述查找多个元素，如果某一个为null则不匹配该字段
     *
     * @param cond 查找條件
     * @return 找不到返回null，在Lua里面为nil
     */
    public LuaTable findElements(LuaTable cond) {
        return LuaUtils.listToTable(FindElement.findElementMatched(cond));
    }

    /**
     * 根据从根结点开始的完整xpath路径查找元素
     *
     * @param xpath /hierarchy/android.widget.FrameLayout/...
     * @return 找不到返回null，在Lua里面为nil
     */
    public UiElement findElementByXpath(String xpath) {
        return FindElement.findElementByXpath(xpath);
    }

    public UiElement findElementByText(String text) {
        return FindElement.findElementByText(text);
    }

    public UiElement xpSelect(String express, boolean needVisible) {
        return FindElement.xpSelect(express, needVisible);
    }

    public UiElement xpSelect(String express) {
        return FindElement.xpSelect(express);
    }

    public LuaTable xpSelects(String express, boolean needVisible) {
        return LuaUtils.listToTable(FindElement.xpSelects(express, needVisible));
    }

    public LuaTable xpSelects(String express) {
        return LuaUtils.listToTable(FindElement.xpSelects(express));
    }

    public UiElement xpSelectsRandom(String express, boolean needVisible) {
        List<UiElement> uiElementList = FindElement.xpSelects(express, needVisible);
        logger.d("uiElementList", uiElementList.toString());
        if (uiElementList.size() > 0) {
            Random random = new Random();
            int randomIndex = random.nextInt(uiElementList.size() - 1);
            return uiElementList.get(randomIndex);
        } else {
            return null;
        }
    }


    public UiElement waitXpSelect(String xpath, boolean needVisible) {
        return Wait.waitElementXpSelect(xpath, optionTimeout, optionTimeSpan, needVisible);
    }

    public UiElement waitXpSelect(String xpath) {
        return Wait.waitElementXpSelect(xpath, optionTimeout, optionTimeSpan);
    }

    public UiElement waitXpSelect(String xpath, int timeout, boolean needVisible) {
        return Wait.waitElementXpSelect(xpath, timeout, optionTimeSpan, needVisible);
    }

    public UiElement waitXpSelect(String xpath, int timeout) {
        return Wait.waitElementXpSelect(xpath, timeout, optionTimeSpan);
    }

    public LuaTable waitXpSelects(String xpath, boolean needVisible) {
        return LuaUtils.listToTable(Wait.waitElementsXpSelect(xpath, optionTimeout, optionTimeSpan, needVisible));
    }

    public LuaTable waitXpSelects(String xpath) {
        return LuaUtils.listToTable(Wait.waitElementsXpSelect(xpath, optionTimeout, optionTimeSpan));
    }

    public LuaTable waitsXpSelects(String xpath, int timeout, boolean needVisible) {
        return LuaUtils.listToTable(Wait.waitElementsXpSelect(xpath, timeout, optionTimeSpan, needVisible));
    }

    public LuaTable waitsXpSelects(String xpath, int timeout) {
        return LuaUtils.listToTable(Wait.waitElementsXpSelect(xpath, timeout, optionTimeSpan));
    }

    public UiElement waitXpSelectsRandom(String xpath, boolean needVisible) {
        List<UiElement> uiElementList = Wait.waitElementsXpSelect(xpath, optionTimeout, optionTimeSpan, needVisible);
        logger.d("uiElementList", uiElementList.toString());
        if (uiElementList.size() > 0) {
            Random random = new Random();
            int randomIndex = random.nextInt(uiElementList.size() - 1);
            return uiElementList.get(randomIndex);
        } else {
            return null;
        }
    }

    // Accessibility遍历RootView来获取无法获取准确view，以下方法暂时弃用
//    public UiElement findElementById(String id) {
//        return FindElement.findElementMatched(By.id(id));
//    }
//
//    public UiElement findElementByDesc(String desc) {
//        return FindElement.findElementMatched(By.desc(desc));
//    }
//
//    public UiElement findElementByClass(String clazz) {
//        return FindElement.findElementMatched(By.clazz(clazz));
//    }
//
//    public UiElement findElementByText(String text) {
//        return FindElement.findElementMatched(By.text(text));
//    }
//
//    public UiElement findElementByIdAllWindows(String id) {
//        return FindElement.findElementAllWindows(By.id(id));
//    }
//
//    public UiElement findElementByDescAllWindows(String desc) {
//        return FindElement.findElementAllWindows(By.desc(desc));
//    }
//
//    public UiElement findElementByClassAllWindows(String clazz) {
//        return FindElement.findElementAllWindows(By.clazz(clazz));
//    }
//
//    public UiElement findElementByTextAllWindows(String text) {
//        return FindElement.findElementAllWindows(By.text(text));
//    }

    /**
     * 通过shell "dumpsys window|grep mCurrentFocus" 获取当前activity完整名称
     *
     * @return activity完整名称
     */
    public String getCurrentActivity() {
        return ActActivityManager.getInstance().getCurrentActivity();
    }

    // ============================ wait relate ==============================

    public boolean sleep() {
        return Wait.waits(optionTimeout);
    }

    /**
     * 线程休眠
     *
     * @param time 休眠时间，ms
     * @return 无异常返回true，被中断等异常返回false
     */
    public boolean sleep(int time) {
        return Wait.waits(time);
    }

    public boolean randomSleep(int timeLength) {
        int min = timeLength - 100;
        int max = timeLength + 100;
        Random random = new Random();
        int randomNumber = random.nextInt(max - min + 1) + min;
        logger.d(TAG, "randomSleep: min=" + min + ", max=" + max + ", timeLength=" + timeLength + "" + ", randomNumber=" + randomNumber);

        return sleep(randomNumber);
    }

    public boolean randomSleep(int timeLength, int randomFactor) {
        int min = timeLength - randomFactor;
        int max = timeLength + randomFactor;
        Random random = new Random();
        int randomNumber = random.nextInt(max - min + 1) + min;
        logger.d(TAG, "randomSleep: min=" + min + ", max=" + max + ", timeLength=" + timeLength + "" + ", randomNumber=" + randomNumber);
        return sleep(randomNumber);
    }

    /**
     * 等待直到当前Activity为'activityName'。默认超时时间。
     *
     * @param activityName activity完整名称
     * @return 成功true，超时false
     */
    public boolean waitActivity(String activityName) {
        return Wait.waitActivity(activityName, optionTimeout, optionTimeSpan);
    }

    /**
     * 等待直到当前Activity为'activityName'。'timeout'超时时间，ms。
     *
     * @param activityName activity完整名称
     * @return 成功true，超时false
     */
    public boolean waitActivity(String activityName, int timeout) {
        return Wait.waitActivity(activityName, timeout, optionTimeSpan);
    }

    /**
     * 等待直到当前Activity不再是'activityName'。默认超时时间。
     *
     * @param activityName activity完整名称
     * @return 成功true，超时false
     */
    public boolean waitActivityDisappear(String activityName) {
        return Wait.waitActivityDisappear(activityName, optionTimeout, optionTimeSpan);
    }

    /**
     * 等待直到当前Activity不再是'activityName'。'timeout'超时时间，ms。
     *
     * @param activityName activity完整名称
     * @return 成功true，超时false
     */
    public boolean waitActivityDisappear(String activityName, int timeout) {
        return Wait.waitActivityDisappear(activityName, timeout, optionTimeSpan);
    }

    /**
     * 等待直到找到一个元素。'timeout'超时时间。
     * <p>
     * 接收Luatable,可以传入多个条件进行判断，同时满足一个或多个条件
     *
     * @param cond 查找條件
     * @return 找不到返回null，在Lua里面为nil
     */
    public UiElement waitElement(LuaTable cond, int timeout) {
        List<UiElement> list = Wait.waitElement(cond, timeout, optionTimeSpan);
        return list.size() > 0 ? list.get(0) : null;
    }

    /**
     * 等待直到找到一个元素。默认超时时间。
     *
     * @param cond 查找條件
     * @return 找不到返回null，在Lua里面为nil
     */
    public UiElement waitElement(LuaTable cond) {
        List<UiElement> list = Wait.waitElement(cond, optionTimeout, optionTimeSpan);
        return list.size() > 0 ? list.get(0) : null;
    }

    /**
     * 等待直到找到符合的元素列表。默认超时时间。
     *
     * @param cond 查找條件
     * @return 找不到返回空table，在Lua里面为nil
     */
    public LuaTable waitElements(LuaTable cond) {
        return LuaUtils.listToTable(Wait.waitElement(cond, optionTimeout, optionTimeSpan));
    }

    /**
     * 等待直到找到符合的元素列表。'timeout'超时时间，ms。
     *
     * @param cond 查找條件
     * @return 找不到返回null，在Lua里面为nil
     */
    public LuaTable waitElements(LuaTable cond, int timeout) {
        return LuaUtils.listToTable(Wait.waitElement(cond, timeout, optionTimeSpan));
    }

    /**
     * 等待直到找到元素。'timeout'超时时间，ms。
     *
     * @param xpath 查找條件
     * @return 找不到返回null，在Lua里面为nil
     */
    public UiElement waitElementByXpath(String xpath, int timeout) {
        return Wait.waitElementByXpath(xpath, optionTimeout, optionTimeSpan);
    }

    /**
     * 等待直到找到元素。默认超时时间。
     *
     * @param xpath 查找條件
     * @return 找不到返回null，在Lua里面为nil
     */
    public UiElement waitElementByXpath(String xpath) {
        return Wait.waitElementByXpath(xpath, optionTimeout, optionTimeSpan);
    }

    /**
     * 接收Luatalbe,传入一个标签,携带多个条件,满足当前标签下的任意一个条件即可
     *
     * @param cond 查找條件
     * @return 找到了返回List, 找不到返回null，在Lua里面为nil
     */
    public static UiElement findElementOr(LuaTable cond) throws JSONException {
        return FindElement.findElementOr(cond);
    }

    /**
     * 等待直到找到一个元素。'timeout'超时时间，ms。
     * 接收Luatalbe,传入一个标签,携带多个条件,满足当前标签下的任意一个条件即可
     *
     * @param cond viewResourceId，元素资源id，匹配方式为 '=='
     * @return 找不到返回null，在Lua里面为nil
     */
    public UiElement waitElementOr(LuaTable cond, int timeout) throws JSONException {
        return Wait.waitElementOr(cond, timeout, optionTimeSpan);
    }

    /**
     * 等待直到找到一个元素。默认超时时间。
     *
     * @param cond 查找條件
     * @return 找不到返回null，在Lua里面为nil
     */
    public UiElement waitElementOr(LuaTable cond) throws JSONException {
        return Wait.waitElementOr(cond, optionTimeout, optionTimeSpan);
    }

    /**
     * 等待直到元素消失。默认等待时间。
     *
     * @param ele 元素对象
     * @return 成功true，超时false
     */
    public boolean waitElementDisappear(UiElement ele) {
        return Wait.waitElementDisappear(ele, optionTimeout, optionTimeSpan);
    }

    /**
     * 等待直到元素消失。'timeout'超时时间，ms。
     *
     * @param ele 元素对象
     * @return 成功true，超时false
     */
    public boolean waitElementDisappear(UiElement ele, int timeout) {
        return Wait.waitElementDisappear(ele, timeout, optionTimeSpan);
    }

    public void printNodes() {
        AccessibilityHelper.printNodes();
    }

    // ============================ motion ==============================

    /**
     * 点击元素
     *
     * @param ele 元素对象
     */
    public void click(UiElement ele) throws Exception {
        Click.click(ele);
        sleep(optionTimeSpan);
    }

    public void click(int x, int y) {
        Click.click(x, y);
        sleep(optionTimeSpan);
    }

    public void randomClick(UiElement ele) throws Exception {
        Point randomX = ele.getRandomPoint();
        Click.click(randomX.x, randomX.y);
        Logger.d(TAG, "randomClick: x = " + randomX.x + ", y = " + randomX.y);
        sleep(optionTimeSpan);
    }

    public void randomClick(int x, int y) {
        int randomX = x + (int) (Math.random() * 10);
        int randomY = y + (int) (Math.random() * 10);
        Logger.d(TAG, "randomClick: x = " + randomX + ", y = " + randomY);
        Click.click(randomX, randomX);
        sleep(optionTimeSpan);
    }

    /**
     * 长按
     *
     * @param ele 元素对象
     */
    public void longClick(UiElement ele) throws Exception {
        Click.longClick(ele);
        sleep(optionTimeSpan);
    }

    public void longClick(int x, int y) {
        Click.longClick(x, y);
        sleep(optionTimeSpan);
    }

    /**
     * 输入文本
     *
     * @param text 输入的文本
     */
    public void inputText(String text) throws Exception {
        NewStage.instance().inputText(text);
        sleep(optionTimeSpan);
    }

    public void inputActionGo() {
        NewStage.instance().inputAction(EditorInfo.IME_ACTION_GO);
        sleep(optionTimeSpan);
    }

    public void inputActionSearch() {
        NewStage.instance().inputAction(EditorInfo.IME_ACTION_SEARCH);
        sleep(optionTimeSpan);
    }

    public void inputActionSend() {
        NewStage.instance().inputAction(EditorInfo.IME_ACTION_SEND);
        sleep(optionTimeSpan);
    }

    public void inputActionDone() {
        NewStage.instance().inputAction(EditorInfo.IME_ACTION_DONE);
        sleep(optionTimeSpan);
    }

    /**
     * keyevent 事件
     */

    public void keyevent(int keyevent) {
        InputKeyCode.inputKeyCode(keyevent);
        sleep(optionTimeSpan);
    }

    /**
     * 点击返回键
     */
    public void pressBack() {
        InputKeyCode.pressBack();
        sleep(optionTimeSpan);
    }

    /**
     * 点击Home键
     */
    public void pressHome() {
        InputKeyCode.pressHome();
        sleep(optionTimeSpan);
    }

    /**
     * 水平的滑动
     */
    public void swipeHorizontal(int fromX, int fromY, int toX, int toY) {
        swipeHorizontal(fromX, fromY, toX, toY, Swipe.DEFAULT_SWIPE_COUNT, Swipe.DEFAULT_SWIPE_TYPE);
    }

    public void swipeHorizontal(int fromX, int fromY, int toX, int toY, int count) {
        swipeHorizontal(fromX, fromY, toX, toY, count, Swipe.DEFAULT_SWIPE_TYPE);
    }

    public void swipeHorizontal(int fromX, int fromY, int toX, int toY, int count, int type) {
        Swipe.swipeHorizontal(fromX, fromY, toX, toY, count, type);
        sleep(optionTimeSpan);
    }

    /**
     * 垂直的滑动
     */
    public void swipeVertical(int fromX, int fromY, int toX, int toY) {
        swipeVertical(fromX, fromY, toX, toY, Swipe.DEFAULT_SWIPE_COUNT, Swipe.DEFAULT_SWIPE_TYPE);
    }

    public void swipeVertical(int fromX, int fromY, int toX, int toY, int count) {
        swipeVertical(fromX, fromY, toX, toY, count, Swipe.DEFAULT_SWIPE_TYPE);
    }

    public void swipeVertical(int fromX, int fromY, int toX, int toY, int count, int type) {
        Swipe.swipeVertical(fromX, fromY, toX, toY, count, type);
        sleep(optionTimeSpan);
    }

    /**
     * 向上滑
     */
    public void swipeUp() {
        swipeUp(Swipe.DEFAULT_SWIPE_TYPE);
    }

    public void swipeUp(int type) {
        Swipe.swipeUp(type);
    }

    /**
     * 向下滑
     */
    public void swipeDown() {
        swipeDown(Swipe.DEFAULT_SWIPE_TYPE);
    }

    public void swipeDown(int type) {
        Swipe.swipeDown(type);
        sleep(optionTimeSpan);
    }

    /**
     * 从顶部往下滑
     */
    public void swipeDownFromTop() {
        swipeDownFromTop(Swipe.DEFAULT_SWIPE_TYPE);
    }

    public void swipeDownFromTop(int type) {
        Swipe.swipeDownFromTop(type);
        sleep(optionTimeSpan);
    }

    /**
     * 向左滑
     */
    public void swipeLeft() {
        swipeLeft(Swipe.DEFAULT_SWIPE_TYPE);
    }

    public void swipeLeft(int type) {
        Swipe.swipeLeft(type);
        sleep(optionTimeSpan);
    }

    /**
     * 向右滑，用sentevent实现
     */
    public void swipeRight() {
        swipeRight(Swipe.DEFAULT_SWIPE_TYPE);
    }

    public void swipeRight(int type) {
        Swipe.swipeRight(type);
        sleep(optionTimeSpan);
    }

    public MotionEventOnce motionEventDown(int fromX, int fromY) {
        MotionEventOnce once = MotionEventOnce.create();
        once.down(fromX, fromY);
        return once;
    }

    /**
     * 自定义轨迹滑
     * lua例子：
     * trace = g_driver:initTrace()
     * trace:press(1,2):dragTo(2,3):dragTo(3,4):release():perform()
     */
    public Trace initTrace() {
        return new Trace();
    }

    /**
     * 自定义轨迹滑
     * lua例子：
     * x_list = {1, 2, 3, 4, 5}
     * y_list = {1, 2, 3, 4, 5}
     * g_driver:swipeTrace(x_list, y_list)
     *
     * @param xList 轨迹数组
     * @param yList 轨迹数组
     */
    public void swipeTrace(LuaTable xList, LuaTable yList) {
        Trace.swipeWithList(xList, yList);
        sleep(optionTimeSpan);
    }

    /**
     * 从文件获取轨迹
     *
     * @param pkgName app名
     * @param prefix  前缀
     * @return 返回随机的轨迹
     */
    public String readRandomTrace(String pkgName, String prefix) {
        try {
            return TraceUtils.readRandomTrace(pkgName, prefix);
        } catch (IOException e) {
            logger.e("readTraceFromFile", e.toString());
            e.printStackTrace();
            return null;
        }
    }

    public void inputTraceAndSwipe(int x, int y, String trace) {
        try {
            TraceUtils.inputToSwipe(x, y, trace);
        } catch (Exception e) {
            logger.e("inputTraceAndSwipe", e.toString());
            e.printStackTrace();
        }
    }

    // ============================ xposed 服务 ==============================

    /**
     * 等待直到app的aidl可用
     *
     * @param app app包名
     */

    public boolean waitAidl(String app) {
        return waitAidl(app, optionTimeout);
    }

    public boolean waitAidl(String app, int timeout) {
        return Wait.waitAidl(app, timeout, optionTimeSpan);
    }

    /**
     * 调用xposed模块，立即返回结果
     *
     * @param app    app进程名字
     * @param action xposed插件功能名称
     * @param body   请求参数json字符串
     * @return 无服务时返回null
     */
    public LuaTable aidlInvoke(String app, String action, String body) {
        RemoteServiceConnection conn = RemoteServiceManager.getInstance().getConnection(app);
        if (conn != null) {
            try {
                RequestV0 request = ParcelCreator.buildRequestV0(app, RequestV0.TYPE_ACTION, action, body);
                ResponseV0 response = conn.requestAppAction(request);
                LuaTable table = new LuaTable();
                table.set("ok", response.getSuccess() ? LuaValue.TRUE : LuaValue.FALSE);
                if (response.getReason() == null) {
                    table.set("reason", LuaValue.NIL);
                } else {
                    table.set("reason", response.getReason());
                }
                String ret = response.getBody();
                if (ret == null) {
                    table.set("body", LuaValue.NIL);
                } else {
                    table.set("body", ret);
                }
                return table;
            } catch (Exception e) {
                logger.e(TAG, e.toString(), e);
            }
        } else {
            logger.e(TAG, "Service of " + app + " is unavailable.");
        }
        return null;
    }

    /**
     * 调用xposed模块，回调返回结果
     *
     * @param app      app进程名字
     * @param action   xposed插件功能名称
     * @param body     请求参数json字符串
     * @param function 回调函数，参数为ResponseV0
     */
    public void aidlCall(String app, String action, String body, LuaClosure function) {
        /*RemoteService service = RemoteServiceManager.getInstance().getRemoteService(app);
        if (service != null) {
            try {
                IRemoteCallback callback = new RemoteCallbackImpl(function);
                service.call(app, action, body, callback);
            } catch (Exception e) {
                e.printStackTrace();
                logger.e(TAG, e.toString());
            }
        }*/
    }

    // ============================ 截图服务 ==============================

    /**
     * 全屏截图
     *
     * @return 截图字节数组
     */
    public byte[] takeShot() {
        return Screenshot.take();
    }

    /**
     * 区域截图
     */
    public byte[] takeShot(int fromX, int fromY, int toX, int toY) {
        return Screenshot.take(fromX, fromY, toX - fromX, toY - fromX);
    }

    /**
     * 元素截图
     */
    public byte[] takeShot(UiElement ele) {
        return ele == null ? null : ele.takeShot();
    }

    public void takeJPG(int quality, float ratio) {
        LongShot.takeJpg(quality, ratio);
    }

    /**
     * 长截图
     */
    public LongScreenshot newLongShotInstance() {
        return LongScreenshot.newInstance();
    }

    public byte[] longScreenshot(int count) {
        return LongShot.takeLongShot(count);
    }

    /*＊
     * 点亮屏幕
     */
    public void setDisplayOn(boolean on) {
        BrightnessController.instance().setDisplayOn(on);
    }

    public boolean a4HasMoveData() {
        return A4Watcher.check();
    }

    public void putSPString(String moduleName, String key, String value) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        SPUtils.putString(moduleFile, key, value);
        logger.i("putSPString", "moduleName = " + moduleName + ", key = " + key + ", value = " + value);
    }

    public String getSPString(String moduleName, String key, String defaultValue) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        return SPUtils.getString(moduleFile, key, defaultValue);
    }

    public String getSPString(String moduleName, String key) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        return SPUtils.getString(moduleFile, key);
    }

    public void putSPInt(String moduleName, String key, int value) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        SPUtils.putInt(moduleFile, key, value);
        logger.i("putSPInt", "moduleName = " + moduleName + ", key = " + key + ", value = " + value);
    }

    public int getSPInt(String moduleName, String key, int defaultValue) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        return SPUtils.getInt(moduleFile, key, defaultValue);
    }

    public int getSPInt(String moduleName, String key) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        return SPUtils.getInt(moduleFile, key);
    }

    public void putSPStringMap(String moduleName, String value) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        Map<String, String> resultMap = JSON.parseObject(value, new TypeReference<Map<String, String>>() {
        });
        SPUtils.putStringMap(moduleFile, resultMap);
        logger.i("putSPStringMap", "moduleName = " + moduleName + ", value = " + value);
    }

    public String getSPAll(String moduleName) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        return SPUtils.getAll(moduleFile).toString();
    }

    public void removeSPKey(String moduleName, String key) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        SPUtils.removeKey(moduleFile, key);
        logger.i("removeSPKey", "moduleName = " + moduleName + ", key = " + key);
    }

    public boolean newChromeTab(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        intent.setPackage(RocketComponent.PKG_CHROMIUM);
        String msg = ActActivityManager.getInstance().startActivityAndWait(intent);
        if (msg != null) {
            logger.e(TAG, "fail to start chrome: " + msg);
            return false;
        } else {
            sleep(5000);
            return true;
        }
    }

    public String randomStr(int length) {
        String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < length; ++i) {
            int number = random.nextInt(str.length());
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }

    public String randomEmailSuffix() {
        String[] MAIL_SUFFIX = {"@gmail.com", "@hotmail.com", "@msn.com", "@yahoo.com", "@gmail.com", "@aim.com", "@aol.com", "@mail.com", "@walla.com", "@inbox.com", "@outlook.com", "@icloud.com", "@live.com", "@protonmail.com", "@zoho.com", "@gmx.com", "@aim.com", "@fastmail.com", "@tutanota.com", "@rediffmail.com", "@hushmail.com"};
        return MAIL_SUFFIX[new Random().nextInt(MAIL_SUFFIX.length)];
    }


    public String generateEnglishName(boolean isBoy) {
        String[] LAST_NAMES_BOY = {
                "James", "John", "Robert", "Michael", "William", "David", "Richard", "Joseph", "Charles", "Thomas", "Daniel", "Matthew", "Anthony", "Donald", "Mark", "Paul", "Steven", "Andrew", "Kenneth", "George", "Joshua", "Kevin", "Brian", "Edward", "Ronald", "Timothy", "Jason", "Jeffrey", "Ryan", "Gary", "Nicholas", "Eric", "Stephen", "Jacob", "Larry", "Frank", "Jonathan", "Scott", "Justin", "Brandon", "Raymond", "Gregory", "Samuel", "Benjamin", "Patrick", "Jack", "Henry", "Walter", "Dennis", "Jerry", "Alexander", "Peter", "Tyler", "Douglas", "Harold", "Aaron", "Jose", "Adam", "Arthur", "Zachary", "Carl", "Nathan", "Albert", "Kyle", "Lawrence", "Joe", "Willie", "Gerald", "Roger", "Keith", "Larry", "Jeremy", "Terry", "Harry", "Ralph", "Sean", "Jesse", "Roy", "Louis", "Billy", "Austin", "Bruce", "Eugene", "Christian", "Bryan", "Wayne", "Russell", "Howard", "Fred", "Ethan", "Jordan", "Phillip", "Alan", "Juan", "Randy", "Vincent", "Bobby", "Johnny", "Bradley", "Martin"
                // 继续添加更多的男性名字
        };
        String[] LAST_NAMES_GIRL = {
                "Mary", "Jennifer", "Linda", "Patricia", "Elizabeth", "Susan", "Jessica", "Sarah", "Karen", "Nancy", "Lisa", "Betty", "Margaret", "Dorothy", "Sandra", "Ashley", "Kimberly", "Donna", "Emily", "Carol", "Michelle", "Amanda", "Helen", "Melissa", "Deborah", "Stephanie", "Rebecca", "Laura", "Sharon", "Cynthia", "Kathleen", "Amy", "Shirley", "Anna", "Angela", "Ruth", "Brenda", "Pamela", "Nicole", "Katherine", "Virginia", "Catherine", "Christine", "Samantha", "Debra", "Janet", "Carolyn", "Rachel", "Heather", "Maria", "Diane", "Frances", "Joyce", "Evelyn", "Joan", "Christina", "Kelly", "Victoria", "Lauren", "Martha", "Judith", "Cheryl", "Megan", "Andrea", "Olivia", "Ann", "Jean", "Alice", "Jacqueline", "Hannah", "Teresa", "Sara", "Janice", "Doris", "Gloria", "Eleanor", "Shannon", "Carrie", "Marie", "Charlotte", "Theresa", "Irene", "Julie", "Rose", "Julia", "Grace", "Wanda", "Beverly", "Denise", "Marilyn", "Amber", "Danielle", "Brittany", "Tonya", "Geraldine", "Peggy", "Tina", "Renee", "Catherine", "Alice", "Lori", "Tracy"
                // 继续添加更多的女性名字
        };
        String[] FIRST_NAMES = {
                "Smith", "Johnson", "Brown", "Davis", "Wilson", "Moore", "Taylor", "Anderson", "White", "Harris", "Martin", "Thompson", "Garcia", "Martinez", "Robinson", "Clark", "Rodriguez", "Lewis", "Lee", "Walker", "Hall", "Allen", "Young", "Hernandez", "King", "Wright", "Lopez", "Hill", "Scott", "Green", "Adams", "Baker", "Gonzalez", "Nelson", "Carter", "Mitchell", "Perez", "Roberts", "Turner", "Phillips", "Campbell", "Parker", "Evans", "Edwards", "Collins", "Stewart", "Sanchez", "Morris", "Rogers", "Reed", "Cook", "Morgan", "Bell", "Murphy", "Bailey", "Rivera", "Cooper", "Richardson", "Cox", "Howard", "Ward", "Torres", "Peterson", "Gray", "Ramirez", "James", "Watson", "Brooks", "Kelly", "Sanders", "Price", "Bennett", "Wood", "Barnes", "Ross", "Henderson", "Coleman", "Jenkins", "Perry", "Powell", "Long", "Patterson", "Hughes", "Flores", "Washington", "Butler", "Simmons", "Foster", "Gonzales", "Bryant", "Alexander", "Russell", "Griffin", "Diaz", "Hayes", "Myers", "Ford", "Hamilton", "Graham", "Sullivan"
                // 继续添加更多的名字
        };
        Random random = new Random();
        String randomLastName;
        if (isBoy) {
            logger.d(TAG, "generate boy's English name ,  isBoy is " + isBoy);
            randomLastName = LAST_NAMES_BOY[random.nextInt(LAST_NAMES_BOY.length)];
        } else {
            logger.d(TAG, "generate girl's English name : isBoy is " + isBoy);
            randomLastName = LAST_NAMES_GIRL[random.nextInt(LAST_NAMES_GIRL.length)];
        }
        String randomFirstName = FIRST_NAMES[random.nextInt(FIRST_NAMES.length)];
        return randomFirstName + " " + randomLastName;
    }

    @SuppressLint("DefaultLocale")
    public UiElement swipeHorizontalUtilFindXpath(int fromX, int fromY, int toX, int toY, int count, String xpath) {
        logger.d(TAG, "swipeVerticalUtilFindXpath: fromX=" + fromX + ", fromY=" + fromY + ", toX=" + toX + ", toY=" + toY + ", count=" + count + ", xpath=" + xpath);
        for (int i = 0; i < count; i++) {
            UiElement uiElement = xpSelect(xpath, true);
            if (uiElement != null ) {
                logger.d(TAG, "swipeHorizontalUtilFindXpath: find element for swipe, return" + uiElement);
                swipeHorizontal(fromX, fromY, toX, toY);
                uiElement = xpSelect(xpath, true);
                return uiElement;
            }
            swipeHorizontal(fromX, fromY, toX, toY);
            randomSleep(1000);
        }
        logger.i(TAG, String.format("swipeHorizontalUtilFindXpath: can not find element for swipe %d times, return null", count));
        return null;
    }

    @SuppressLint("DefaultLocale")
    public UiElement swipeVerticalUtilFindXpath(int fromX, int fromY, int toX, int toY, int count, String xpath) {
        logger.d(TAG, "swipeVerticalUtilFindXpath: fromX=" + fromX + ", fromY=" + fromY + ", toX=" + toX + ", toY=" + toY + ", count=" + count + ", xpath=" + xpath);
        for (int i = 0; i < count; i++) {
            UiElement uiElement = xpSelect(xpath, true);
            if (uiElement != null) {
                logger.d(TAG, "swipeVerticalUtilFindXpath: find element for swipe, return" + uiElement);
                swipeVertical(fromX, fromY, toX, toY);
                uiElement = xpSelect(xpath, true);
                return uiElement;
            }
            swipeVertical(fromX, fromY, toX, toY);
            randomSleep(1000);
        }
        logger.i(TAG, String.format("swipeVerticalUtilFindXpath: can not find element for swipe %d times, return null", count));
        return null;
    }
}