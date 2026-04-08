package com.android.internal.os;

interface IInputMethodCallback {
    void onInputText(String text);
    void onInputAction(int action);
}