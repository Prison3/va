package com.android.actor.control.by;

public class ByXPath extends By {
    private static final String SELECTOR_XPATH = "xpath";
    private final String xpathExpression;

    public ByXPath(String xpathExpression) {
        this.xpathExpression = xpathExpression;
    }

    @Override
    public String getElementLocator() {
        return xpathExpression;
    }

    @Override
    public String getElementStrategy() {
        return SELECTOR_XPATH;
    }

    @Override
    public String toString() {
        return "By.xpath: " + xpathExpression;
    }
}