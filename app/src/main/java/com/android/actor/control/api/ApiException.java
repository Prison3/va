package com.android.actor.control.api;

public class ApiException {
    public static class NullElementException extends Exception {
        public NullElementException(String msg) {
            super(msg);
        }
    }
}
