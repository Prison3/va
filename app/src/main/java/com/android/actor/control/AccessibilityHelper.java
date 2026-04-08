package com.android.actor.control;

import static com.android.actor.utils.ActStringUtils.safeCharSeqToString;

import android.annotation.SuppressLint;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.actor.ActAccessibility;
import com.android.actor.control.by.By;
import com.android.actor.control.by.ByDesc;
import com.android.actor.control.by.ByClass;
import com.android.actor.control.by.ById;
import com.android.actor.control.by.ByText;
import com.android.actor.monitor.Logger;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccessibilityHelper {
    private final static String TAG = AccessibilityHelper.class.getSimpleName();

    private final static String PKG_NAME    = "pkg";
    private final static String TEXT_NAME   = "text";
    private final static String CLASS_NAME  = "cls";
    private final static String DESC_NAME   = "desc";
    private final static String RES_ID      = "rid";
    private static List<String> keys = Arrays.asList(PKG_NAME, TEXT_NAME, CLASS_NAME, DESC_NAME, RES_ID);

    public static AccessibilityNodeInfo findInputFocus() {
        if (ActAccessibility.getInstance() != null && ActAccessibility.isStart()) {
            return ActAccessibility.getInstance().getRootInActiveWindow().findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        }
        return null;
    }

    public static boolean checkCond(Map<String, String> cond) {
        for (Map.Entry<String, String> entry : cond.entrySet()) {
            String  key = entry.getKey();
            if(!keys.contains(key)){
                Logger.w(TAG, "CheckCond Failed error: not exist key{"+ key+"}");
                return false;
            }
        }
        return true;
    }

    public static boolean checkExist(AccessibilityNodeInfo root, AccessibilityNodeInfo node){
        if (node == null) {
            return false;
        }
        HashMap<String, String> cond = new HashMap<>();
        CharSequence rid = node.getViewIdResourceName();
        if(rid != null) {
            cond.put(AccessibilityHelper.RES_ID, node.getViewIdResourceName());
        }
        CharSequence text  = node.getText();
        if(text != null){
            cond.put(AccessibilityHelper.TEXT_NAME, text.toString());
        }
        CharSequence desc = node.getContentDescription();
        if(desc != null){
            cond.put(AccessibilityHelper.DESC_NAME, desc.toString());
        }
        return findElements(root, cond).size() > 0 ;
    }
    public static boolean matchCond(AccessibilityNodeInfo node, Map<String, String> cond){

        for (Map.Entry<String, String> entry : cond.entrySet()) {
            String  key = entry.getKey();
            String  value = entry.getValue();
            switch (key) {
                case TEXT_NAME:
                    if (!value.equals(safeCharSeqToString(node.getText()))) {
                        return false;
                    }
                    break;
                case PKG_NAME:
                    if (!value.equals(safeCharSeqToString(node.getPackageName()))) {
                        return false;
                    }
                    break;
                case CLASS_NAME:
                    if (!value.equals(safeCharSeqToString(node.getClassName()))) {
                        return false;
                    }
                    break;
                case DESC_NAME:
                    if (!value.equals(safeCharSeqToString(node.getContentDescription()))) {
                        return false;
                    }
                    break;
                case RES_ID:
                    if (!value.equals(safeCharSeqToString(node.getViewIdResourceName()))) {
                        return false;
                    }
                    break;
            }
        }
        return true;
    }

    public static void collectElements(AccessibilityNodeInfo node, Map<String, String> cond, List<AccessibilityNodeInfo>  infos){
        if(node == null || !node.isVisibleToUser()){
            return;
        }
        if(matchCond(node, cond)){
            infos.add(node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectElements(node.getChild(i), cond, infos);
        }
    }

    @NonNull
    public static List<AccessibilityNodeInfo> findElements(AccessibilityNodeInfo root, Map<String, String> cond) {
        List<AccessibilityNodeInfo>  info = new ArrayList<>();
        collectElements(root, cond, info);
        return info;
    }

    @SuppressLint("DefaultLocale")
    public static AccessibilityNodeInfo findElementByPath(AccessibilityNodeInfo root, List<String> pathList, int i) {
        if (root != null) {
            if (i >= pathList.size()) {
                Logger.d(TAG, "Find node class:" + root.getClassName() + ", id:" + root.getViewIdResourceName() + ", text:" + root.getText() + ", desc:" + root.getContentDescription());
                return root;
            }
            String path = pathList.get(i);
            if (path != null) {
                String[] ceil = path.split("\\[");
                String className = ceil[0];
                HashMap<String, Integer> childClassMap = new HashMap<>();
                for (int k = 0; k < root.getChildCount(); k++) {
                    AccessibilityNodeInfo node = root.getChild(k);
                    if (node == null) {
                        continue;
                    }
                    // record to map
                    if (node.getClassName() != null) {
                        String childClassName = node.getClassName().toString();
                        Integer childClassIndex = childClassMap.get(childClassName);
                        childClassIndex = childClassIndex == null ? 0 : childClassIndex;
                        childClassMap.put(node.getClassName().toString(), childClassIndex + 1);
                        Logger.d(TAG, String.format("%2d-%d: %s  %s[%d] %s", i - 1, k + 1, path, childClassName, childClassIndex + 1, node.isVisibleToUser() ? "visible" : "invisible"));
                    }
                    if (className.equals("*") || AccessibilityHelper.nEqual(node.getClassName(), className)) {
                        if (ceil.length == 1) { // 没有[]包住的额外判断条件
                            return findElementByPath(node, pathList, i + 1);
                        } else { // 有额外判断条件
                            String extra = ceil[1].substring(0, ceil[1].length() - 1);
                            if (extra.startsWith("@")) {
                                switch (extra.split("=")[0]) {
                                    case "@resource-id":
                                        if (AccessibilityHelper.nEqual(node.getViewIdResourceName(), extra.split("=")[1].replace("\"", ""))) {
                                            return findElementByPath(node, pathList, i + 1);
                                        }
                                        break;
                                    case "@text":
                                        if (AccessibilityHelper.nContains(node.getText(), extra.split("=")[1].replace("\"", ""))) {
                                            return findElementByPath(node, pathList, i + 1);
                                        }
                                        break;
                                    case "@content-desc":
                                        if (AccessibilityHelper.nEqual(node.getContentDescription(), extra.split("=")[1].replace("\"", ""))) {
                                            return findElementByPath(node, pathList, i + 1);
                                        }
                                }
                            } else if (extra.matches("\\d+")) {
                                // w3c标准里从1开始计数
                                if (Integer.parseInt(extra) == childClassMap.get(className)) {
                                    return findElementByPath(node, pathList, i + 1);
                                }
                            }
                        }
                    }
                }
            }
        }
        Logger.d(TAG, String.format("%2d-?: %s  not found", i - 1, pathList.get(i)));
        return null;
    }

    public static List<AccessibilityNodeInfo> getChildElements(AccessibilityNodeInfo root, String className) {
        List<AccessibilityNodeInfo> nodeList = new ArrayList<>();
        if (root != null) {
            for (int i = 0; i < root.getChildCount(); i++) {
                AccessibilityNodeInfo node = root.getChild(i);
                if (node != null && node.isVisibleToUser()) {
                    if (className == null || node.getClassName().equals(className)) {
                        nodeList.add(node);
                    }
                }
            }
        }
        return nodeList;
    }

    @Deprecated
    public static AccessibilityNodeInfo findElement(@NonNull By by) {
        if (ActAccessibility.getInstance() != null && ActAccessibility.isStart()) {
            return findNodeWithDFS(ActAccessibility.getInstance().getRootInActiveWindow(), false, by);
        }
        return null;
    }

    @Deprecated
    public static AccessibilityNodeInfo findElement(boolean matchAll, @NonNull By... byList) {
        if (ActAccessibility.getInstance() != null && ActAccessibility.isStart()) {
            return findNodeWithDFS(ActAccessibility.getInstance().getRootInActiveWindow(), true, byList);
        }
        return null;
    }

    @Deprecated
    public static AccessibilityNodeInfo findElementAllWindows(@NonNull By by) {
        for (AccessibilityNodeInfo root : getAccessibilityWindowsRoot()) {
            AccessibilityNodeInfo node = findNodeWithDFS(root, false, by);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    @Deprecated
    public static AccessibilityNodeInfo findElementAllWindows(boolean matchAll, @NonNull By... byList) {
        for (AccessibilityNodeInfo root : getAccessibilityWindowsRoot()) {
            AccessibilityNodeInfo node = findNodeWithDFS(root, matchAll, byList);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    public static List<AccessibilityNodeInfo> getAccessibilityWindowsRoot() {
        List<AccessibilityNodeInfo> roots = new ArrayList<>();
        if (ActAccessibility.getInstance() != null && ActAccessibility.isStart()) {
//            roots.add(ActAccessibility.getInstance().getRootInActiveWindow());
            for (AccessibilityWindowInfo windowInfo : ActAccessibility.getInstance().getWindows()) {
                roots.add(windowInfo.getRoot());
                Logger.d(TAG, "get root: " + windowInfo.getRoot().getPackageName());
            }
        }
        return roots;
    }

    public static boolean matchAllCase(AccessibilityNodeInfo node, @Nullable By... byList) {
        if (node == null) {
            return false;
        }
        for (By by : byList) {
            String selector = by.getElementLocator();
//            Logger.d(TAG, selector + " " + node.getViewIdResourceName() + " " + node.getContentDescription() + " " + node.getClassName() + " " + node.getText());
            if (by instanceof ById) {
                if (!nEqual(node.getViewIdResourceName(), selector)) {
                    return false;
                }
            } else if (by instanceof ByDesc) {
                if (!nEqual(node.getContentDescription(), selector)) {
                    return false;
                }
            } else if (by instanceof ByClass) {
                if (!nEqual(node.getClassName(), selector)) {
                    return false;
                }
            } else if (by instanceof ByText) {
                if (!nContains(node.getText(), selector)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean matchOneCase(AccessibilityNodeInfo node, @Nullable By... byList) {
        if (node == null) {
            return false;
        }
        if (!node.isVisibleToUser()) {
            return false;
        }
        for (By by : byList) {
            String selector = by.getElementLocator();
            //Logger.d(TAG, " matchOneCase0 " + node.getViewIdResourceName() + " " + node.getContentDescription() + " " + node.getClassName() + " " + node.getText());
            if (by instanceof ById) {
                if (nEqual(node.getViewIdResourceName(), selector)) {
                    return true;
                }
            } else if (by instanceof ByDesc) {
                if (nEqual(node.getContentDescription(), selector)) {
                    return true;
                }
            } else if (by instanceof ByClass) {
                if (nEqual(node.getClassName(), selector)) {
                    return true;
                }
            } else if (by instanceof ByText) {
                //Logger.d(TAG, " matchOneCase1 ByText " + node.getText() + " desc: " + node.getContentDescription());
                if (nContains(node.getText(), selector) || nContains(node.getContentDescription(), selector)) {
                    //Logger.d(TAG, "matchOneCase2 " + node);
                    return true;
                }
            }
        }
        return false;
    }

    public static List<AccessibilityNodeInfo> findElementsByText(AccessibilityNodeInfo root, String text) {
        if (root != null) {
            List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(text);
            if (list.size() == 0) {
                // for webView, try to iterate
                return dumpNodeWithDFS(root, false, By.text(text));
            } else {
                return list;
            }
        }
        return null;
    }

    public static List<AccessibilityNodeInfo> dumpNodeWithDFS(AccessibilityNodeInfo node, boolean matchAll, @NonNull By... by) {
        List<AccessibilityNodeInfo> list = new ArrayList<>();
        long start = System.currentTimeMillis();
        node.refresh();
        findNodeWithDFS(node, list, matchAll, by);
        long waste = System.currentTimeMillis() - start;
        Logger.w(TAG, "dumpNodeWithDFS using time " + waste + "ms, by: " + Arrays.toString(by));
        return list;
    }

    private static void findNodeWithDFS(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> list, boolean matchAll, @NonNull By... by) {
        if (node == null) {
            return;
        }
        if (matchAll) {
            if (matchAllCase(node, by)) {
                list.add(AccessibilityNodeInfo.obtain(node));
            }
        } else {
            if (matchOneCase(node, by)) {
                list.add(AccessibilityNodeInfo.obtain(node));
                return;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            findNodeWithDFS(node.getChild(i), list, matchAll, by);
            if (!matchAll && list.size() > 0) {
                return;
            }
        }
    }


    /**
     * depth-first traversal
     */
    public static AccessibilityNodeInfo findNodeWithDFS(AccessibilityNodeInfo node, boolean matchAll, @NonNull By... by) {
        if (node == null || node.getChildCount() == 0) {
            return null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (matchAll) {
                if (matchAllCase(node, by)) {
                    return AccessibilityNodeInfo.obtain(node);
                }
            } else {
                if (matchOneCase(node, by)) {
                    return AccessibilityNodeInfo.obtain(node);
                }
            }
            AccessibilityNodeInfo childNode = node.getChild(i);
            AccessibilityNodeInfo childSearch = findNodeWithDFS(childNode, matchAll, by);
            if (childSearch != null) {
                return childSearch;
            }
        }
        // release source
        node.recycle();
        return null;
    }

    public static void printNodes() {
        int count = printNodes(ActAccessibility.getInstance().getRootInActiveWindow(), 0);
        Logger.d(TAG, "Print total " + count);
    }

    public static int printNodes(AccessibilityNodeInfo rootNode, int deep) {
        if (rootNode == null) {
            Logger.w(TAG, "Print null node?");
            return 0;
        }
        Logger.d(TAG, "Print " + StringUtils.repeat(' ', deep + 1) + deep
                + " " + rootNode.getClassName() + ", " + rootNode.getViewIdResourceName()
                + ", " + rootNode.getText() + ", " + rootNode.getContentDescription());
        int count = 1;
        for (int i = 0; i < rootNode.getChildCount(); ++i) {
            AccessibilityNodeInfo node = rootNode.getChild(i);
            count += printNodes(node, deep + 1);
        }
        return count;
    }

    public static boolean nEqual(String s1, String s2) {
        return s1 != null && s1.equals(s2);
    }

    public static boolean nEqual(CharSequence s1, String s2) {
        return s1 != null && s1.toString().equals(s2);
    }

    public static boolean nContains(CharSequence s1, String s2) {
        return s1 != null && s1.toString().contains(s2);
    }
}