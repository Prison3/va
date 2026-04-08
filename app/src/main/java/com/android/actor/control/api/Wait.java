package com.android.actor.control.api;

import androidx.annotation.NonNull;

import com.android.actor.ActAccessibility;
import com.android.actor.control.UiElement;
import com.android.actor.monitor.Logger;
import com.android.actor.remote.RemoteServiceManager;

import org.json.JSONException;
import org.luaj.vm2.LuaTable;

import java.util.ArrayList;
import java.util.List;

public class Wait {
    private final static String TAG = Wait.class.getSimpleName();

    public static boolean waits(int time) {
        try {
            Thread.sleep(time);
            return true;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean waitActivity(String activityName, int timeout, int span) {
        Logger.d(TAG, "waitActivity " + activityName + " " + timeout);
        for (int i = 0; i < timeout / span; i++) {
            if (Check.checkTopActivity(activityName)) {
                return true;
            }
            waits(span);
        }
        return false;
    }

    public static boolean waitActivityDisappear(String activityName, int timeout, int span) {
        Logger.d(TAG, "waitActivityDisappear " + activityName + " " + timeout);
        for (int i = 0; i < timeout / span; i++) {
            if (!Check.checkTopActivity(activityName)) {
                return true;
            }
            waits(span);
        }
        return false;
    }

    @NonNull
    public static List<UiElement> waitElement(LuaTable cond, int timeout, int span)  {
        assert ActAccessibility.getInstance() != null;
        List<UiElement> eleList = new ArrayList<>();
        for (int i = 0; i < timeout / span; i++) {
            eleList = FindElement.findElementMatched(ActAccessibility.getInstance().getRootInActiveWindow(), cond);
            if (eleList.size() != 0) {
                return eleList;
            }
            waits(span);
        }
        return eleList;
    }

    @NonNull
    public static List<UiElement> waitElement(UiElement root, LuaTable cond,   int timeout, int span)  {
        Logger.d(TAG, "waitElement root[" + root + "] "  + timeout);
        List<UiElement> eleList = new ArrayList<>();
        for (int i = 0; i < timeout / span; i++) {
            eleList = FindElement.findElementMatched(root.getNode(), cond);
            if (eleList.size() != 0) {
                return eleList;
            }
            waits(span);
        }
        return eleList;
    }

    public static UiElement waitElementByXpath(String xpath, int timeout, int span) {
        Logger.d(TAG, "waitElement " + xpath + " " + timeout);
        UiElement ele;
        for (int i = 0; i < timeout / span; i++) {
            ele = FindElement.findElementByXpath(xpath);
            if (ele != null) {
                return ele;
            }
            waits(span);
        }
        return null;
    }

    public static UiElement waitElementByXpath(UiElement root, String xpath, int timeout, int span) {
        Logger.d(TAG, "waitElement root[" + root + "] " + xpath + " " + timeout);
        UiElement ele;
        for (int i = 0; i < timeout / span; i++) {
            ele = root.findElementByXpath(xpath);
            if (ele != null) {
                return ele;
            }
            waits(span);
        }
        return null;
    }


    public static boolean waitElementDisappear(UiElement uiElement, int timeout, int span) {
        Logger.d(TAG, "waitElementDisappear " + uiElement + " " + timeout);
        for (int i = 0; i < timeout / span; i++) {
            if (!FindElement.checkElementExist(uiElement)) {
                return true;
            }
            waits(span);
        }
        return false;
    }

    public static UiElement waitElementOr(LuaTable cond, int timeout, int span) throws JSONException {
        Logger.d(TAG, "waitElementOr cond :" + cond + " " + timeout);
        for (int i = 0; i < timeout / span; i++) {
            UiElement ele = FindElement.findElementOr(cond);
            if (ele != null){
                return ele;
            }
            waits(span);
        }
        return null;
    }

    public static boolean waitAidl(String app, int timeout, int span) {
        Logger.d(TAG, "wait aidl available " + app + " " + timeout);
        for (int i = 0; i < timeout / span; i++) {
            if (app != null && RemoteServiceManager.getInstance().getConnection(app) != null) {
                return true;
            }
            waits(span);
        }
        return false;
    }

    public static UiElement waitElementXpSelect(String xpath, int timeout, int span, boolean needVisible) {
        Logger.d(TAG, "waitElementXpSelect " + xpath + " " + timeout);
        UiElement ele;
        for (int i = 0; i < timeout / span; i++) {
            ele = FindElement.xpSelect(xpath, needVisible);
            if (ele != null) {
                return ele;
            }
            waits(span);
        }
        return null;
    }

    public static UiElement waitElementXpSelect(String xpath, int timeout, int span) {
        return waitElementXpSelect(xpath, timeout, span, false);
    }

    public static List<UiElement> waitElementsXpSelect(String xpath, int timeout, int span, boolean needVisible) {
        Logger.d(TAG, "waitElementXpSelect " + xpath + " " + timeout);
        List<UiElement> ele = new ArrayList<>();
        for (int i = 0; i < timeout / span; i++) {
            ele = FindElement.xpSelects(xpath, needVisible);
            if (ele.size() != 0) {
                return ele;
            }
            waits(span);
        }
        return ele;
    }

    public static List<UiElement> waitElementsXpSelect(String xpath, int timeout, int span) {
        return waitElementsXpSelect(xpath, timeout, span, false);
    }
}
