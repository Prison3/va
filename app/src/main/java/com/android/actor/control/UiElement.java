package com.android.actor.control;

import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.actor.control.api.ApiException;
import com.android.actor.control.api.Click;
import com.android.actor.control.api.FindElement;
import com.android.actor.control.api.Swipe;
import com.android.actor.control.api.Trace;
import com.android.actor.control.api.Wait;
import com.android.actor.script.lua.LuaScript;
import com.android.actor.script.lua.LuaUtils;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.screen.Screenshot;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import java.util.List;
import java.util.Random;


public class UiElement implements Parcelable {
    private final static String TAG = UiElement.class.getSimpleName();
    private AccessibilityNodeInfo node;
    public String className;
    public String resourceId;
    public String text;
    public String desc;
    public boolean visible;
    public Rect rect = new Rect();
    public Rect rectInParent = new Rect();

    public UiElement(AccessibilityNodeInfo node) {
        this.node = node;
        className = node.getClassName() != null ? node.getClassName().toString() : null;
        resourceId = node.getViewIdResourceName();
        text = node.getText() != null ? node.getText().toString() : null;
        desc = node.getContentDescription() != null ? node.getContentDescription().toString() : null;
        node.getBoundsInScreen(rect);
        node.getBoundsInScreen(rectInParent);
        visible = node.isVisibleToUser();
    }

    protected UiElement(Parcel in) {
        node = in.readParcelable(AccessibilityNodeInfo.class.getClassLoader());
        className = in.readString();
        resourceId = in.readString();
        text = in.readString();
        desc = in.readString();
        visible = in.readBoolean();
        rect = in.readParcelable(Rect.class.getClassLoader());
        rectInParent = in.readParcelable(Rect.class.getClassLoader());
    }

    public static final Creator<UiElement> CREATOR = new Creator<UiElement>() {
        @Override
        public UiElement createFromParcel(Parcel in) {
            return new UiElement(in);
        }

        @Override
        public UiElement[] newArray(int size) {
            return new UiElement[size];
        }
    };

    @Nullable
    public AccessibilityNodeInfo getNode() {
        return this.node;
    }

    public void click() throws ApiException.NullElementException {
        Logger.d(TAG, "click " + this);
        // sometime not useful
//        node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        Click.click(this);
        Wait.waits(LuaScript.optionTimeSpan);
    }

    public void longClick() throws ApiException.NullElementException {
        Logger.d(TAG, "longClick " + this);
//        node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
        Click.click(this);
        Wait.waits(LuaScript.optionTimeSpan);
    }

    public void swipeUp() {
        swipeUp(Swipe.DEFAULT_SWIPE_TYPE);
    }

    public void swipeUp(int type) {
        Swipe.swipeUp(rect, type);
        Wait.waits(LuaScript.optionTimeSpan);
    }

    public void swipeDown() {
        swipeDown(Swipe.DEFAULT_SWIPE_TYPE);
    }

    public void swipeDown(int type) {
        Swipe.swipeDown(rect, type);
        Wait.waits(LuaScript.optionTimeSpan);
    }

    public void swipeLeft() {
        swipeLeft(Swipe.DEFAULT_SWIPE_TYPE);
    }

    public void swipeLeft(int type) {
        Swipe.swipeLeft(rect, type);
        Wait.waits(LuaScript.optionTimeSpan);
    }

    public void swipeRight() {
        swipeRight(Swipe.DEFAULT_SWIPE_TYPE);
    }

    public void swipeRight(int type) {
        Swipe.swipeRight(rect, type);
        Wait.waits(LuaScript.optionTimeSpan);
    }

    public void swipeHorizontal(int fromX, int fromY, int toX, int toY) {
        swipeHorizontal(fromX, fromY, toX, toY, Swipe.DEFAULT_SWIPE_COUNT);
    }

    public void swipeHorizontal(int fromX, int fromY, int toX, int toY, int count) {
        swipeHorizontal(fromX, fromY, toX, toY, count, Swipe.DEFAULT_SWIPE_TYPE);
    }

    public void swipeHorizontal(int fromX, int fromY, int toX, int toY, int count, int type) {
        Swipe.swipeHorizontal(fromX + rect.left, fromY + rect.top, toX + rect.left, toY + rect.top, count, type);
        Wait.waits(LuaScript.optionTimeSpan);
    }

    public void swipeVertical(int fromX, int fromY, int toX, int toY) {
        swipeVertical(fromX, fromY, toX, toY, Swipe.DEFAULT_SWIPE_COUNT);
    }

    public void swipeVertical(int fromX, int fromY, int toX, int toY, int count) {
        swipeVertical(fromX, fromY, toX, toY, count, Swipe.DEFAULT_SWIPE_COUNT);
    }

    public void swipeVertical(int fromX, int fromY, int toX, int toY, int count, int type) {
        Swipe.swipeVertical(fromX + rect.left, fromY + rect.top, toX + rect.left, toY + rect.top, count, type);
        Wait.waits(LuaScript.optionTimeSpan);
    }


    public void swipeTrace(LuaTable xList, LuaTable yList) {
        Trace.swipeWithList(xList, yList, rect.left, rect.top);
        Wait.waits(LuaScript.optionTimeSpan);
    }

    public Trace initTrace() {
        return new Trace(rect.left, rect.top);
    }

    public Point getRandomPoint() {
        Rect rect = this.rect;
        Random random = new Random();
        int rangeX = rect.right - rect.left;
        int rangeY = rect.bottom - rect.top;
        int x = (int) (rect.left + 0.2 * (rangeX) + 0.6 * random.nextInt(rangeX));
        int y = (int) (rect.top + 0.2 * rangeY + 0.6 * random.nextInt(rangeY));
        return new Point(x, y);
    }

    /**
     * 根据描述查找该元素的子view
     *
     * @param cond     * @return 找不到返回null，在Lua里面为nil
     */
    public UiElement findElement(LuaTable cond) {
        return FindElement.findElementMatched(this.node, cond).get(0);
    }

    public LuaTable findElements(LuaTable cond) {
        return LuaUtils.listToTable(FindElement.findElementMatched(this.node, cond));
    }

    public UiElement waitElement(LuaTable cond) {
        List<UiElement> list = Wait.waitElement(this, cond, LuaScript.optionTimeout, LuaScript.optionTimeSpan);
        return list.size() > 0 ? list.get(0) : null;
    }

    public UiElement waitElement(LuaTable cond, int timeout) {
        List<UiElement> list = Wait.waitElement(this, cond,  timeout, LuaScript.optionTimeSpan);
        return list.size() > 0 ? list.get(0) : null;
    }

    public LuaTable waitElements(LuaTable cond) {
        return LuaUtils.listToTable(Wait.waitElement(this, cond, LuaScript.optionTimeout, LuaScript.optionTimeSpan));
    }

    public LuaTable waitElements(LuaTable cond,  int timeout) {
        return LuaUtils.listToTable(Wait.waitElement(this, cond, timeout, LuaScript.optionTimeSpan));
    }

    /**
     * 根据从该元素开始的xpath路径查找元素
     *
     * @param xpath //android.widget.FrameLayout/...
     * @return 找不到返回null，在Lua里面为nil
     */
    public UiElement findElementByXpath(String xpath) {
        return FindElement.findElementByXpath(this.node, xpath);
    }

    public UiElement waitElementByXpath(String xpath) {
        return Wait.waitElementByXpath(this, xpath, LuaScript.optionTimeout, LuaScript.optionTimeSpan);
    }

    public UiElement waitElementByXpath(String xpath, int timeout) {
        return Wait.waitElementByXpath(this, xpath, timeout, LuaScript.optionTimeSpan);
    }


    /**
     * 获取父元素
     *
     * @return 返回父元素，找不到返回空
     */
    public UiElement getParent() {
        Logger.d(TAG, "getParent of: " + this);
        AccessibilityNodeInfo parent = this.node.getParent();
        if (parent != null) {
            return new UiElement(parent);
        }
        return null;
    }

    /**
     * 获取第一层子元素
     *
     * @param className 指定特定的class，如果className为空则不指定
     * @return 返回元素列表
     */
    public LuaTable getChildElements(String className) {
        Logger.d(TAG, "getChildElements of: " + this);
        List<AccessibilityNodeInfo> nodeList = AccessibilityHelper.getChildElements(this.node, className);
        if (nodeList.size() == 0) {
            return null;
        }
        LuaTable table = LuaValue.tableOf();
        for (int i = 0; i < nodeList.size(); i++) {
            table.insert(i + 1, CoerceJavaToLua.coerce(new UiElement(nodeList.get(i))));
        }
        return table;
    }

    public UiElement getRandomChild(String className) {
        Logger.d(TAG, "getRandomChild of: " + this);
        List<AccessibilityNodeInfo> nodeList = AccessibilityHelper.getChildElements(this.node, className);
        if (nodeList.size() == 0) {
            return null;
        }
        Random random = new Random();
        return new UiElement(nodeList.get(random.nextInt(nodeList.size())));
    }
    // api废弃 , 采用 newstage 里面的 inputtext，因为这个方法会被检测到
//
//    /**
//     * 对该元素输入文本
//     *
//     * @param text 文本
//     */
//    public void inputText(String text) {
//        Bundle arguments = new Bundle();
//        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
//        this.node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
//    }

    /**
     * 截取当前所在区块的图，如果被遮挡，会返回顶层图
     *
     * @return 截图字节数组
     */
    public byte[] takeShot() {
        return Screenshot.take(rect);
    }

    @Override
    public String toString() {
        if (node == null) {
            return "Node null";
        }
        return "Node id: " + resourceId + ", text: " + text + ", desc: " + desc + ", class: " + className + ", " + rect + ", " + (visible ? "visible" : "invisible");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(node, flags);
        dest.writeString(className);
        dest.writeString(resourceId);
        dest.writeString(text);
        dest.writeString(desc);
        dest.writeBoolean(visible);
        dest.writeParcelable(rect, flags);
        dest.writeParcelable(rectInParent, flags);
    }
}