package com.android.actor.control.by;

public class ByText extends By {
    private static final String SELECTOR_TEXT = "text";
    private final String text;

    public ByText(String text) {
        this.text = text;
    }

    @Override
    public String getElementLocator() {
        return text;
    }

    @Override
    public String getElementStrategy() {
        return SELECTOR_TEXT;
    }

    @Override
    public String toString() {
        return "By.text: " + text;
    }
}