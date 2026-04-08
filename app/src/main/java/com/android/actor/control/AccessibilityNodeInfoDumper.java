package com.android.actor.control;

import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.actor.ActAccessibility;
import com.android.actor.control.api.XPathSelector;
import com.android.actor.device.DeviceInfoManager;
import com.android.actor.monitor.Logger;

import org.w3c.dom.Element;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AccessibilityNodeInfoDumper {

    private static final String TAG = AccessibilityNodeInfoDumper.class.getSimpleName();
    private static final String[] NAF_EXCLUDED_CLASSES = new String[]{
            android.widget.GridView.class.getName(), android.widget.GridLayout.class.getName(),
            android.widget.ListView.class.getName(), android.widget.TableLayout.class.getName()
    };

    /**
     * Using {@link AccessibilityNodeInfo} this method will walk the layout hierarchy
     * and generates an xml dump to the location specified by <code>dumpFile</code>
     *
     * @param root     The root accessibility node.
     * @param rotation The rotaion of current display
     * @param width    The pixel width of current display
     * @param height   The pixel height of current display
     */
    public static String dumpWindow(AccessibilityNodeInfo root, int rotation,
                                    int width, int height) {
        if (root == null) {
            Logger.e(TAG, "Root is null, can't dump.");
            return null;
        }
        String result = null;
        final long startTime = SystemClock.uptimeMillis();
        try {
            XmlSerializer serializer = Xml.newSerializer();
            StringWriter stringWriter = new StringWriter();
            serializer.setOutput(stringWriter);
            serializer.startDocument("UTF-8", true);
            serializer.startTag("", "hierarchy");
            serializer.attribute("", "rotation", Integer.toString(rotation));
            dumpNodeRec(root, serializer, 0, width, height);
            serializer.endTag("", "hierarchy");
            serializer.endDocument();
            result = stringWriter.toString();
        } catch (IOException e) {
            Logger.e(TAG, "failed to dump window to file", e);
        }
        final long endTime = SystemClock.uptimeMillis();
        Logger.w(TAG, "Fetch time: " + (endTime - startTime) + "ms");
        return result;
    }

    private static void dumpNodeRec(AccessibilityNodeInfo node, XmlSerializer serializer, int index,
                                    int width, int height) throws IOException {
        serializer.startTag("", "node");
        if (!nafExcludedClass(node) && !nafCheck(node))
            serializer.attribute("", "NAF", Boolean.toString(true));
        serializer.attribute("", "index", Integer.toString(index));
        serializer.attribute("", "text", safeCharSeqToString(node.getText()));
        serializer.attribute("", "resource-id", safeCharSeqToString(node.getViewIdResourceName()));
        serializer.attribute("", "class", safeCharSeqToString(node.getClassName()));
        serializer.attribute("", "package", safeCharSeqToString(node.getPackageName()));
        serializer.attribute("", "content-desc", checkUTF8Char(safeCharSeqToString(node.getContentDescription())));
        serializer.attribute("", "checkable", Boolean.toString(node.isCheckable()));
        serializer.attribute("", "checked", Boolean.toString(node.isChecked()));
        serializer.attribute("", "clickable", Boolean.toString(node.isClickable()));
        serializer.attribute("", "enabled", Boolean.toString(node.isEnabled()));
        serializer.attribute("", "focusable", Boolean.toString(node.isFocusable()));
        serializer.attribute("", "focused", Boolean.toString(node.isFocused()));
        serializer.attribute("", "scrollable", Boolean.toString(node.isScrollable()));
        serializer.attribute("", "long-clickable", Boolean.toString(node.isLongClickable()));
        serializer.attribute("", "password", Boolean.toString(node.isPassword()));
        serializer.attribute("", "selected", Boolean.toString(node.isSelected()));
        serializer.attribute("", "bounds", getVisibleBoundsInScreen(
                node, width, height).toShortString());
        serializer.attribute("", "visible", Boolean.toString(node.isVisibleToUser()));
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                dumpNodeRec(child, serializer, i, width, height);
                child.recycle();
            } else {
                Logger.i(TAG, String.format("Null child %d/%d, parent: %s",
                        i, count, node.toString()));
            }
        }
        serializer.endTag("", "node");
    }

    /**
     * Returns the node's bounds clipped to the size of the display
     *
     * @param node
     * @param width  pixel width of the display
     * @param height pixel height of the display
     * @return null if node is null, else a Rect containing visible bounds
     */
    private static Rect getVisibleBoundsInScreen(AccessibilityNodeInfo node, int width, int height) {
        if (node == null) {
            return null;
        }
        // targeted node's bounds
        Rect nodeRect = new Rect();
        node.getBoundsInScreen(nodeRect);

        Rect displayRect = new Rect();
        displayRect.top = 0;
        displayRect.left = 0;
        displayRect.right = width;
        displayRect.bottom = height;

        if (nodeRect.intersect(displayRect)) {
            return nodeRect;
        } else {
            return new Rect();
        }
    }

    /**
     * The list of classes to exclude my not be complete. We're attempting to
     * only reduce noise from standard layout classes that may be falsely
     * configured to accept clicks and are also enabled.
     *
     * @param node
     * @return true if node is excluded.
     */
    private static boolean nafExcludedClass(AccessibilityNodeInfo node) {
        String className = safeCharSeqToString(node.getClassName());
        for (String excludedClassName : NAF_EXCLUDED_CLASSES) {
            if (className.endsWith(excludedClassName))
                return true;
        }
        return false;
    }

    /**
     * We're looking for UI controls that are enabled, clickable but have no
     * text nor content-description. Such controls configuration indicate an
     * interactive control is present in the UI and is most likely not
     * accessibility friendly. We refer to such controls here as NAF controls
     * (Not Accessibility Friendly)
     *
     * @param node
     * @return false if a node fails the check, true if all is OK
     */
    private static boolean nafCheck(AccessibilityNodeInfo node) {
        boolean isNaf = node.isClickable() && node.isEnabled()
                && safeCharSeqToString(node.getContentDescription()).isEmpty()
                && safeCharSeqToString(node.getText()).isEmpty();

        if (!isNaf)
            return true;

        // check children since sometimes the containing element is clickable
        // and NAF but a child's text or description is available. Will assume
        // such layout as fine.
        return childNafCheck(node);
    }

    /**
     * This should be used when it's already determined that the node is NAF and
     * a further check of its children is in order. A node maybe a container
     * such as LinerLayout and may be set to be clickable but have no text or
     * content description but it is counting on one of its children to fulfill
     * the requirement for being accessibility friendly by having one or more of
     * its children fill the text or content-description. Such a combination is
     * considered by this dumper as acceptable for accessibility.
     *
     * @param node
     * @return false if node fails the check.
     */
    private static boolean childNafCheck(AccessibilityNodeInfo node) {
        int childCount = node.getChildCount();
        for (int x = 0; x < childCount; x++) {
            AccessibilityNodeInfo childNode = node.getChild(x);

            if (childNode == null) {
                Logger.i(TAG, "check childNode is null");
                return false;
            }
            if (!safeCharSeqToString(childNode.getContentDescription()).isEmpty()
                    || !safeCharSeqToString(childNode.getText()).isEmpty())
                return true;

            if (childNafCheck(childNode))
                return true;
        }
        return false;
    }

    private static String checkUTF8Char(String str) {
        String regEx = "[<>&'\"\\x00-\\x08\\x0b-\\x0c\\x0e-\\x1f]";
        Pattern pattern = Pattern.compile(regEx);
        Matcher match = pattern.matcher(str);
        //最后转化的字符，直接消除就可以了
        return match.replaceAll("");
    }

    private static String safeCharSeqToString(CharSequence cs) {
        if (cs == null)
            return "";
        else {
            return stripInvalidXMLChars(cs);
        }
    }

    private static String stripInvalidXMLChars(CharSequence cs) {
        StringBuffer ret = new StringBuffer();
        char ch;
        /* http://www.w3.org/TR/xml11/#charsets
        [#x1-#x8], [#xB-#xC], [#xE-#x1F], [#x7F-#x84], [#x86-#x9F], [#xFDD0-#xFDDF],
        [#x1FFFE-#x1FFFF], [#x2FFFE-#x2FFFF], [#x3FFFE-#x3FFFF],
        [#x4FFFE-#x4FFFF], [#x5FFFE-#x5FFFF], [#x6FFFE-#x6FFFF],
        [#x7FFFE-#x7FFFF], [#x8FFFE-#x8FFFF], [#x9FFFE-#x9FFFF],
        [#xAFFFE-#xAFFFF], [#xBFFFE-#xBFFFF], [#xCFFFE-#xCFFFF],
        [#xDFFFE-#xDFFFF], [#xEFFFE-#xEFFFF], [#xFFFFE-#xFFFFF],
        [#x10FFFE-#x10FFFF].
         */
        for (int i = 0; i < cs.length(); i++) {
            ch = cs.charAt(i);

            if ((ch >= 0x1 && ch <= 0x8) || (ch >= 0xB && ch <= 0xC) || (ch >= 0xE && ch <= 0x1F) ||
                    (ch >= 0x7F && ch <= 0x84) || (ch >= 0x86 && ch <= 0x9f) ||
                    (ch >= 0xFDD0 && ch <= 0xFDDF) || (ch >= 0x1FFFE && ch <= 0x1FFFF) ||
                    (ch >= 0x2FFFE && ch <= 0x2FFFF) || (ch >= 0x3FFFE && ch <= 0x3FFFF) ||
                    (ch >= 0x4FFFE && ch <= 0x4FFFF) || (ch >= 0x5FFFE && ch <= 0x5FFFF) ||
                    (ch >= 0x6FFFE && ch <= 0x6FFFF) || (ch >= 0x7FFFE && ch <= 0x7FFFF) ||
                    (ch >= 0x8FFFE && ch <= 0x8FFFF) || (ch >= 0x9FFFE && ch <= 0x9FFFF) ||
                    (ch >= 0xAFFFE && ch <= 0xAFFFF) || (ch >= 0xBFFFE && ch <= 0xBFFFF) ||
                    (ch >= 0xCFFFE && ch <= 0xCFFFF) || (ch >= 0xDFFFE && ch <= 0xDFFFF) ||
                    (ch >= 0xEFFFE && ch <= 0xEFFFF) || (ch >= 0xFFFFE && ch <= 0xFFFFF) ||
                    (ch >= 0x10FFFE && ch <= 0x10FFFF))
                ret.append(".");
            else
                ret.append(ch);
        }
        return ret.toString();
    }

    public static Element dumpDomWindow() throws Exception {
        return dumpDomWindow(null);
    }

    public static Element dumpDomWindow(AccessibilityNodeInfo nodeInfo) throws Exception {
        if (nodeInfo == null) {
            nodeInfo = ActAccessibility.getInstance().getRootInActiveWindow();
        }
        if (nodeInfo == null) {
            Logger.e(TAG, "rootWindow null");
            return null;
        } else {
            Logger.d(TAG, "nodeInfo " + nodeInfo);
            Element element = XPathSelector.getDocument().createElement("hierarchy");
            Logger.d(TAG, "element " + element);
            XPathSelector.getDocument().appendChild(element); // must add node to dom
            element.appendChild(dumpDomNode(
                    nodeInfo,
                    0,
                    DeviceInfoManager.getInstance().width,
                    DeviceInfoManager.getInstance().height
            ));
            return element;
        }
    }

    public static Element dumpDomNode(AccessibilityNodeInfo node, int index, int width, int height) throws Exception {
        Element element = XPathSelector.getDocument().createElement(simpleClassName(node.getClassName()));
        if (!nafExcludedClass(node) && !nafCheck(node)) {
            element.setAttribute("NAF", Boolean.toString(true));
        }
        XPathSelector.putToDom(node, element);
        XPathSelector.putToNode(element, node);
//        Field localName = element.getClass().getDeclaredField("localName");
//        localName.setAccessible(true);
//        localName.set(element, tag(node.getClassName().toString()));
        element.setAttribute("index", Integer.toString(index));
        element.setAttribute("text", safeCharSeqToString(node.getText()));
        element.setAttribute("resource-id", safeCharSeqToString(node.getViewIdResourceName()));
        element.setAttribute("class", safeCharSeqToString(node.getClassName()));
        element.setAttribute("package", safeCharSeqToString(node.getPackageName()));
        element.setAttribute("content-desc", safeCharSeqToString(node.getContentDescription()));
        element.setAttribute("checkable", Boolean.toString(node.isCheckable()));
        element.setAttribute("checked", Boolean.toString(node.isChecked()));
        element.setAttribute("clickable", Boolean.toString(node.isClickable()));
        element.setAttribute("enabled", Boolean.toString(node.isEnabled()));
        element.setAttribute("focusable", Boolean.toString(node.isFocusable()));
        element.setAttribute("focused", Boolean.toString(node.isFocused()));
        element.setAttribute("scrollable", Boolean.toString(node.isScrollable()));
        element.setAttribute("long-clickable", Boolean.toString(node.isLongClickable()));
        element.setAttribute("password", Boolean.toString(node.isPassword()));
        element.setAttribute("selected", Boolean.toString(node.isSelected()));
        element.setAttribute("bounds", getVisibleBoundsInScreen(node, width, height).toShortString());
        element.setAttribute("visible", Boolean.toString(node.isVisibleToUser()));
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                element.appendChild(dumpDomNode(child, i, width, height));
            } else {
                Logger.i(TAG, String.format("Null child %d/%d, parent: %s", i, count, node.toString()));
            }
        }
        return element;
    }

    public static String tag(String className) {
        // the nth anonymous class has a class name ending in "Outer$n"
        // and local inner classes have names ending in "Outer.$1Inner"
        className = className.replaceAll("\\$[0-9]+", "\\$");
        return className;
    }

    /**
     * returns by excluding inner class name.
     */
    private static String simpleClassName(CharSequence cs) {
        if (cs == null) {
            return "node";
        }
        String name = cs.toString();
        name = name.replaceAll("\\$[0-9]+", "\\$");
        int start = name.lastIndexOf('$');
        if (start == -1) {
            return name;
        }
        return name.substring(0, start);
    }
}
