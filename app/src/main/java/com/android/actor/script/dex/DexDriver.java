package com.android.actor.script.dex;

import android.graphics.Rect;
import android.os.RemoteException;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import com.alibaba.fastjson.JSON;
import com.android.actor.control.AccessibilityHelper;
import com.android.actor.control.UiElement;
import com.android.actor.control.api.Click;
import com.android.actor.control.api.FindElement;
import com.android.actor.control.api.InputKeyCode;
import com.android.actor.control.api.MotionEventOnce;
import com.android.actor.control.api.Swipe;
import com.android.actor.device.DeviceInfoManager;
import com.android.actor.device.DeviceNumber;
import com.android.actor.device.NewStage;
import com.android.actor.monitor.Logger;
import com.android.actor.remote.RemoteServiceConnection;
import com.android.actor.remote.RemoteServiceManager;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.proxy.ProxyManager;
import com.android.actor.utils.screen.LongShot;
import com.android.actor.utils.temu.A4Watcher;
import com.android.internal.aidl.service.RequestV0;
import com.android.internal.aidl.service.ResponseV0;
import com.android.internal.ds.IDexDriver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class DexDriver extends IDexDriver.Stub {

    private final static String TAG = DexDriver.class.getSimpleName();
    public static int optionTimeout;
    public static int optionTimeSpan;
    public static Rect clickBound;

    public DexDriver() {
        optionTimeout = 10 * 1000;
        optionTimeSpan = 1000;
        clickBound = new Rect(0,
                0,
                DeviceInfoManager.getInstance().width,
                DeviceInfoManager.getInstance().height);
        Log.d(TAG, "Init DexScript with actionBound: " + clickBound);
    }

    @Override
    public String number() throws RemoteException {
        return DeviceNumber.get();
    }

    @Override
    public void click(int x, int y, int holdCount) throws RemoteException {
        Click.click(x, y, holdCount);
    }

    @Override
    public void swipe(int fromX, int fromY, int toX, int toY, int count, boolean vertical, int type) throws RemoteException {
        Swipe.swipe(fromX, fromY, toX, toY, count, new Random(), vertical, type);
    }

    @Override
    public void swipeExact(int fromX, int fromY, int toX, int toY, int count) throws RemoteException {
        Swipe.swipeExact(fromX, fromY, toX, toY, count);
    }

    @Override
    public UiElement findElementByText(String text) throws RemoteException {
        return FindElement.findElementByText(text);
    }

    @Override
    public UiElement xpSelect(AccessibilityNodeInfo root, String xpath, boolean needVisible) throws RemoteException {
        return FindElement.xpSelect(root, xpath, needVisible);
    }

    @Override
    public List<UiElement> xpSelects(AccessibilityNodeInfo root, String xpath, boolean needVisible) throws RemoteException {
        return FindElement.xpSelects(root, xpath, needVisible);
    }

    @Override
    public void printNodes() throws RemoteException {
        AccessibilityHelper.printNodes();
    }

    @Override
    public List<UiElement> getChildElements(AccessibilityNodeInfo root, String className) throws RemoteException {
        List<AccessibilityNodeInfo> nodeList=AccessibilityHelper.getChildElements(root, className);
        if ( nodeList.size()==0){
            return null;
        }
        List<UiElement> uiElements = new ArrayList<>();
        for (AccessibilityNodeInfo nodeInfo : nodeList) {
            uiElements.add(new UiElement(nodeInfo));
        }
        return uiElements;
    }

    public UiElement getParent(AccessibilityNodeInfo node)throws RemoteException{
        AccessibilityNodeInfo parent = node.getParent();
        Logger.d(TAG, "getParent: " + parent);
        if (parent != null) {
            return new UiElement(parent);
        }
        return null;
    }

    @Override
    public UiElement getRandomChild(AccessibilityNodeInfo node ,String className)throws RemoteException{
        List<AccessibilityNodeInfo> nodeList=AccessibilityHelper.getChildElements(node, className);
        if ( nodeList.size()==0){
            return null;
        }
        Random random = new Random();
        return new UiElement(nodeList.get(random.nextInt(nodeList.size())));
    }

    @Override
    public String getAppProxy(String pkgName, int profileId) throws RemoteException {
        return ProxyManager.getInstance().getAppProxy(pkgName, profileId);
    }

    @Override
    public void inputText(String text) throws RemoteException {
        NewStage.instance().inputText(text);
    }

    @Override
    public void inputAction(int action) throws RemoteException {
        NewStage.instance().inputAction(action);
    }

    @Override
    public void deleteSurroundingText(int beforeLength, int afterLength) throws RemoteException {
        NewStage.instance().deleteSurroundingText(beforeLength, afterLength);
    }

    @Override
    public void inputKeyCode(int keyCode) throws RemoteException {
        InputKeyCode.inputKeyCode(keyCode);
    }

    @Override
    public MotionEventOnce motionEventDown(MotionEventOnce once, int x, int y) throws RemoteException {
        once.down(x, y);
        return once;
    }

    @Override
    public MotionEventOnce motionEventMove(MotionEventOnce once, int x, int y, int count, int type) throws RemoteException {
        once.move(x, y, count, type);
        return once;
    }

    @Override
    public void motionEventUp(MotionEventOnce once) throws RemoteException {
        once.up();
    }

    @Override
    public byte[] longScreenshot(int count) throws RemoteException {
        return LongShot.takeLongShot(count);
    }

    @Override
    public boolean a4HasMoveData() throws RemoteException {
        return A4Watcher.check();
    }

    @Override
    public void putSPString(String moduleName, String key, String value) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        SPUtils.putString(moduleFile, key, value);
    }

    @Override
    public String getSPString(String moduleName, String key) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        return SPUtils.getString(moduleFile, key);
    }

    @Override
    public void putSPInt(String moduleName, String key, int value) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        SPUtils.putInt(moduleFile, key, value);
    }

    @Override
    public int getSPInt(String moduleName, String key) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        return SPUtils.getInt(moduleFile, key);
    }

    @Override
    public void putSPStringMap(String moduleName, Map<String, String> map) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        SPUtils.putStringMap(moduleFile, map);
    }

    @Override
    public String getSPAll(String moduleName) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        Map<String, ?> resultMap = SPUtils.getAll(moduleFile);
        return JSON.toJSONString(resultMap);
    }

    @Override
    public void putSPLong(String moduleName, String key, long value) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        SPUtils.putLong(moduleFile, key, value);
    }
    @Override
    public long getSPLong(String moduleName,String key) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        return SPUtils.getLong(moduleFile, key);
    }

    @Override
    public void removeSPKey(String moduleName, String key) {
        SPUtils.ModuleFile moduleFile = SPUtils.ModuleFile.valueOf(moduleName);
        SPUtils.removeKey(moduleFile, key);
    }

    @Override
    public ResponseV0 requestAppAction(RequestV0 request) throws RemoteException {
        RemoteServiceConnection conn = RemoteServiceManager.getInstance().getConnection(request.getApp());
        if (conn != null) {
            return conn.requestAppAction(request);
        }
        return null;
    }
}
