package com.android.actor.control.api;

import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;

import com.android.actor.ActAccessibility;
import com.android.actor.control.AccessibilityHelper;
import com.android.actor.control.UiElement;
import com.android.actor.control.by.By;
import com.android.actor.script.lua.LuaUtils;
import com.android.actor.monitor.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.android.actor.control.AccessibilityHelper.findElementByPath;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

public class FindElement {
    private final static String TAG = FindElement.class.getSimpleName();

    public static UiElement findInputFocus() {
        long start = System.currentTimeMillis();
        AccessibilityNodeInfo node = AccessibilityHelper.findInputFocus();
        Logger.d(TAG, "Using time " + (System.currentTimeMillis() - start) + " ms, get " + node);
        if (node != null) {
            return new UiElement(node);
        }
        return null;
    }

    public static boolean checkElementExist(UiElement ele) {
        Logger.d(TAG, "checkElementExist: " + ele);
        assert ActAccessibility.getInstance() != null;
        AccessibilityNodeInfo root = ActAccessibility.getInstance().getRootInActiveWindow();
        if (ele == null) {
            return false;
        }
        return AccessibilityHelper.checkExist(root, ele.getNode());
    }

    public static UiElement findElementByText(String text) {
        AccessibilityNodeInfo root = ActAccessibility.getInstance().getRootInActiveWindow();
        List<AccessibilityNodeInfo> textList = AccessibilityHelper.findElementsByText(root, text);
        if (textList.size() > 0) {
            for (AccessibilityNodeInfo node : textList) {
                return new UiElement(node);
            }
        }
        return null;
    }


    @NonNull
    public static List<UiElement> findElementMatched(LuaTable cond) {
        assert ActAccessibility.getInstance() != null;
        return findElementMatched(ActAccessibility.getInstance().getRootInActiveWindow(), cond);
    }

    @NonNull
    public static List<UiElement> findElementMatched(AccessibilityNodeInfo root, LuaTable cond) {
        List<UiElement> nodeList = new ArrayList<>();
        Map<String, String> condMap = new HashMap<>();
        for (LuaValue key : cond.keys()) {
            condMap.put(key.toString(), cond.get(key).toString());
        }
        if(!AccessibilityHelper.checkCond(condMap)){
            return nodeList;
        }
        Logger.d(TAG, "findElementMatched  cond: " + condMap);


        if(root == null){
            Logger.e(TAG, "findElementMatched root is null");
            return nodeList;
        }

        List<AccessibilityNodeInfo> viewList = AccessibilityHelper.findElements(root, condMap);
        if (viewList.size() > 0) {
            for (AccessibilityNodeInfo viewNode : viewList) {
                nodeList.add(new UiElement(viewNode));
            }
        }
        return nodeList;
    }

    public static UiElement findElementByXpath(String xpath) {
        assert ActAccessibility.getInstance() != null;
        AccessibilityNodeInfo root = ActAccessibility.getInstance().getRootInActiveWindow();
        Logger.d(TAG, "FindElement xpath: " + xpath);

        String[] ceils = xpath.split("(?<!id)/");
        List<String> pathList = new ArrayList<>(Arrays.asList(ceils));
        AccessibilityNodeInfo node = null;
        String path = pathList.get(1);
        if (path.equals("hierarchy")) {
            String first = pathList.get(2).split("\\[")[0];
            if (root.getClassName().equals(first)) {
                node = findElementByPath(root, pathList, 3);
            }
        }
        if (node != null) {
            return new UiElement(node);
        }
        return null;
    }

    public static UiElement findElementByXpath(AccessibilityNodeInfo root, String xpath) {
        Logger.d(TAG, "FindElement xpath: " + xpath);
        String[] ceils = xpath.split("(?<!id)/");
        List<String> pathList = new ArrayList<>(Arrays.asList(ceils));
        AccessibilityNodeInfo node = findElementByPath(root, pathList, 2);
        if (node != null) {
            return new UiElement(node);
        }
        return null;
    }

    public static UiElement findElementOr(LuaTable cond) throws JSONException {
        JSONObject result = (JSONObject) LuaUtils.tableToJson(cond);
        Iterator<String> it = result.keys();
        while(it.hasNext()){
            String key = it.next();
            JSONArray arr = result.getJSONArray(key);
            for (int i=0; i < arr.length(); i++){
                LuaTable subCond = new LuaTable();
                subCond.set(key, arr.getString(i));
                List<UiElement> ele = FindElement.findElementMatched(subCond);
                if(ele.size()>0){
                    return ele.get(0);
                }
            }
        }
        return null;
    }

    @Deprecated
    public static UiElement findElementMatched(By by) {
        long start = System.currentTimeMillis();
        Logger.d(TAG, "FindElement by: " + by);
        AccessibilityNodeInfo node = AccessibilityHelper.findElement(by);
        if (node != null) {
            Logger.d(TAG, "Using time " + (System.currentTimeMillis() - start) + " ms, get " + node.getClassName());
            return new UiElement(node);
        }
        return null;
    }

    @Deprecated
    public static UiElement findElementAllMatch(By... by) {
        long start = System.currentTimeMillis();
        Logger.d(TAG, "FindElement by " + Arrays.toString(by));
        AccessibilityNodeInfo node = AccessibilityHelper.findElement(true, by);
        Logger.d(TAG, "Using time " + (System.currentTimeMillis() - start) + " ms, get " + node);
        if (node != null) {
            return new UiElement(node);
        }
        return null;
    }

    @Deprecated
    public static UiElement findElementMatchOne(By... by) {
        long start = System.currentTimeMillis();
        AccessibilityNodeInfo node = AccessibilityHelper.findElement(false, by);
        Logger.d(TAG, "Using time " + (System.currentTimeMillis() - start) + " ms, get " + node);
        if (node != null) {
            return new UiElement(node);
        }
        return null;
    }

    @Deprecated
    public static UiElement findElementAllWindows(By by) {
        long start = System.currentTimeMillis();
        AccessibilityNodeInfo node = AccessibilityHelper.findElementAllWindows(by);
        if (node != null) {
            Logger.d(TAG, "Using time " + (System.currentTimeMillis() - start) + " ms, get " + node.getClassName());
            return new UiElement(node);
        }
        return null;
    }

    @Deprecated
    public static UiElement findElementAllMatchAllWindows(By... by) {
        long start = System.currentTimeMillis();
        AccessibilityNodeInfo node = AccessibilityHelper.findElementAllWindows(true, by);
        Logger.d(TAG, "Using time " + (System.currentTimeMillis() - start) + " ms, get " + node);
        if (node != null) {
            return new UiElement(node);
        }
        return null;
    }

    @Deprecated
    public static UiElement findElementMatchOneAllWindows(By... by) {
        long start = System.currentTimeMillis();
        AccessibilityNodeInfo node = AccessibilityHelper.findElementAllWindows(false, by);
        Logger.d(TAG, "Using time " + (System.currentTimeMillis() - start) + " ms, get " + node);
        if (node != null) {
            return new UiElement(node);
        }
        return null;
    }

    public static UiElement xpSelect(String xpath, boolean needVisible) {
        return xpSelect(null, xpath, needVisible);
    }

    public static UiElement xpSelect(AccessibilityNodeInfo root, String xpath, boolean needVisible) {
        List<UiElement> list = XPathSelector.find(root, xpath);
        if (needVisible) {
            List<UiElement> filtList = filtVisible(list);
            if (filtList != null && filtList.size() > 0) {
                return filtList.get(0);
            }
        } else {
            if (list != null && list.size() > 0) {
                return list.get(0);
            }
        }
        return null;
    }

    public static UiElement xpSelect(String xpath) {
        return xpSelect(null, xpath);
    }

    public static UiElement xpSelect(AccessibilityNodeInfo root, String xpath) {
        return xpSelect(root, xpath, false);
    }

    public static List<UiElement> xpSelects(String xpath, boolean needVisible) {
        return xpSelects(null, xpath, needVisible);
    }

    public static List<UiElement> xpSelects(AccessibilityNodeInfo root, String xpath, boolean needVisible) {
        List<UiElement> list = XPathSelector.find(root, xpath);
        return needVisible ? filtVisible(list) : list;
    }

    public static List<UiElement> xpSelects(String xpath) {
        return xpSelects(null, xpath);
    }

    public static List<UiElement> xpSelects(AccessibilityNodeInfo root, String xpath) {
        return xpSelects(root, xpath, false);
    }

    public static List<UiElement> filtVisible(List<UiElement> list) {
        List<UiElement> filtList = new ArrayList<>();
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                UiElement e = (UiElement) list.get(i);
                if (e.visible) {
                    filtList.add(e);
                }
            }
            return filtList;
        } else {
            return null;
        }
    }
}