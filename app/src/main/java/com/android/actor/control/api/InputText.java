package com.android.actor.control.api;

import com.android.actor.control.UiElement;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.shell.Shell;

public class InputText {
    // 废弃，采用 newstage 里面的接口去输入
//    private final static String TAG = InputText.class.getSimpleName();
//
//    /**
//     * Input text to the focus view or specific element.
//     */
//    public static void inputText(String text) {
//        Logger.d(TAG, "input text: " + text);
//        Shell.inputText(text);
//    }
//
//    public static void inputText(UiElement ele, String text) throws ApiException.NullElementException {
//        Logger.d(TAG, "input text: " + text + " to element: " + ele);
//        if (ele != null) {
//            ele.inputText(text);
//        } else {
//            throw new ApiException.NullElementException("Attempt to invoke input text with null.");
//        }
//    }
}