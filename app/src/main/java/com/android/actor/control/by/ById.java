package com.android.actor.control.by;

public class ById extends By {
    private static final String SELECTOR_NATIVE_ID = "id";
    private final String id;

    public ById(String id) {
        this.id = id;
    }

    @Override
    public String getElementLocator() {
        return id;
    }

    @Override
    public String getElementStrategy() {
        return SELECTOR_NATIVE_ID;
    }

    @Override
    public String toString() {
        return "By.id: " + id;
    }
}