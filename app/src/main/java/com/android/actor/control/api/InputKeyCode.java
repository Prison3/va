package com.android.actor.control.api;


import android.view.KeyEvent;

import com.android.actor.utils.shell.Shell;

public class InputKeyCode {
    /**
     * Input the keyCode defined in android.view.KeyEvent to the focus view.
     */
    public static void inputKeyCode(int keyCode) {
        Shell.inputKeyEvent(keyCode);
    }

    public static void pressBack() {
        inputKeyCode(KeyEvent.KEYCODE_BACK);
    }

    public static void pressMenu() {
        inputKeyCode(KeyEvent.KEYCODE_MENU);
    }

    public static void pressHome() {
        inputKeyCode(KeyEvent.KEYCODE_HOME);
    }

    public static void pressPower(){
        inputKeyCode(KeyEvent.KEYCODE_POWER);
    }
}