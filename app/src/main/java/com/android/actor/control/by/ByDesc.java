package com.android.actor.control.by;

public class ByDesc extends By {
    private static final String SELECTOR_ACCESSIBILITY_ID = "desc_id";
    private final String desc;

    public ByDesc(String desc) {
        this.desc = desc;
    }

    @Override
    public String getElementLocator() {
        return desc;
    }

    @Override
    public String getElementStrategy() {
        return SELECTOR_ACCESSIBILITY_ID;
    }

    @Override
    public String toString() {
        return "By.desc: " + desc;
    }
}