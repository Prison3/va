package com.android.actor.control.by;

public abstract class By {
    /**
     * @param id The value of the "id" attribute to search for
     * @return a By which locates elements by the value of the "id" attribute.
     */
    public static By id(final String id) {
        if (id == null) {
            throw new IllegalArgumentException("Cannot find elements with a null id attribute.");
        }
        return new ById(id);
    }

    public static By desc(final String text) {
        if (text == null) {
            throw new IllegalArgumentException("Cannot find elements when text is null.");
        }
        return new ByDesc(text);
    }

    public static By xpath(String xpathExpression) {
        if (xpathExpression == null) {
            throw new IllegalArgumentException("Cannot find elements when xpath is null.");
        }
        return new ByXPath(xpathExpression);
    }

    public static By clazz(String className) {
        if (className == null) {
            throw new IllegalArgumentException("Cannot find elements when className is null.");
        }
        return new ByClass(className);
    }

//    public static By androidUiAutomator(String expression) {
//        if (expression == null) {
//            throw new IllegalArgumentException(
//                    "Cannot find elements when '-android uiautomator' is null.");
//        }
//        return new ByAndroidUiAutomator(expression);
//    }

    public static By text(final String text) {
        if (text == null) {
            throw new IllegalArgumentException("Cannot find elements when text is null.");
        }

        return new ByText(text);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        By by = (By) o;

        return toString().equals(by.toString());
    }

    public abstract String getElementLocator();

    public abstract String getElementStrategy();

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        // A stub to prevent endless recursion in hashCode()
        return "[unknown locator]";
    }
}