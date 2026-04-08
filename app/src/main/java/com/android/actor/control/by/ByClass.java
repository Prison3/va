package com.android.actor.control.by;

public class ByClass extends By {
    private static final String SELECTOR_CLASS = "class";
    private final String clazz;

    public ByClass(String clazz) {
        this.clazz = clazz;
    }

    @Override
    public String getElementLocator() {
        return clazz;
    }

    @Override
    public String getElementStrategy() {
        return SELECTOR_CLASS;
    }

    @Override
    public String toString() {
        return "By.clazz: " + clazz;
    }
}