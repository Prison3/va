package com.android.internal.ds;

import com.android.actor.control.UiElement;
import com.android.actor.control.api.MotionEventOnce;
import com.android.internal.aidl.service.RequestV0;
import com.android.internal.aidl.service.ResponseV0;

interface IDexDriver {

    String number();

    void click(int x, int y, int holdCount);

    void swipe(int fromX, int fromY, int toX, int toY, int count, boolean vertical, int type);

    void swipeExact(int fromX, int fromY, int toX, int toY, int count);

    UiElement findElementByText(String text);

    UiElement xpSelect(in AccessibilityNodeInfo root, String xpath, boolean needVisible);

    List<UiElement> xpSelects(in AccessibilityNodeInfo root, String xpath, boolean needVisible);

    void printNodes();

    List<UiElement> getChildElements(in AccessibilityNodeInfo root,String className);
    UiElement getParent(in AccessibilityNodeInfo root);
    UiElement getRandomChild(in AccessibilityNodeInfo root,String className);
    String getAppProxy(in String pkdName,int profileId);
    void inputText(String text);

    void inputAction(int action);

    void inputKeyCode(int keyCode);

    MotionEventOnce motionEventDown(in MotionEventOnce once, int x, int y);
    MotionEventOnce motionEventMove(in MotionEventOnce once, int x, int y, int count, int type);
    void motionEventUp(in MotionEventOnce once);

    byte[] longScreenshot(int count);

    boolean a4HasMoveData();
    void putSPString(String moduleName, String key, String value);
    String getSPString(String moduleName, String key);
    void putSPInt(String moduleName, String key, int value);
    int getSPInt(String moduleName, String key);
    void putSPLong(String moduleName, String key, long value);
    long getSPLong(String moduleName, String key);
    void putSPStringMap(String moduleName,in Map<String, String> map);
    String getSPAll(String moduleName);
    void removeSPKey(String moduleName, String key);

    ResponseV0 requestAppAction(in RequestV0 request);

    void deleteSurroundingText(int beforeLength, int afterLength);
}