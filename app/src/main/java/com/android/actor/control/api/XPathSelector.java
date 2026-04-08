package com.android.actor.control.api;

import android.view.accessibility.AccessibilityNodeInfo;

import com.android.actor.control.AccessibilityNodeInfoDumper;
import com.android.actor.control.UiElement;
import com.android.actor.monitor.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

public class XPathSelector {

    private final static String TAG = XPathSelector.class.getSimpleName();
    private static final XPath XPATH_COMPILER = XPathFactory.newInstance().newXPath();
    // document needs to be static so that when buildDomNode is called recursively
    // on children they are in the same document to be appended.
    private static Document document;
    // The two maps should be kept in sync
    private static final Map<AccessibilityNodeInfo, Element> TO_DOM_MAP = new HashMap<>();
    private static final Map<Element, AccessibilityNodeInfo> FROM_DOM_MAP = new HashMap<>();

    public static void clearData() {
        TO_DOM_MAP.clear();
        FROM_DOM_MAP.clear();
        document = null;
    }

    public static List<UiElement> find(String xPathString) {
        return find(null, xPathString);
    }

    public static List<UiElement> find(AccessibilityNodeInfo nodeInfo, String xPathString) {
        Logger.d(TAG, "find " + xPathString + " for " + nodeInfo);
        long start = System.currentTimeMillis();
        List<UiElement> list = new ArrayList<>();
        try {
            Element root = AccessibilityNodeInfoDumper.dumpDomWindow(nodeInfo);
            // Logger.d(TAG, "root: " + root);
            XPathExpression xPathExpression = XPATH_COMPILER.compile(xPathString);
            NodeList nodes = (NodeList) xPathExpression.evaluate(root, XPathConstants.NODESET);
            int nodesLength = nodes.getLength();
            for (int i = 0; i < nodesLength; i++) {
                Element e = (Element) nodes.item(i);
                Logger.d(TAG, "find node: " + FROM_DOM_MAP.get(e));
                if (e.getNodeType() == Node.ELEMENT_NODE && !e.getTagName().equals("hierarchy")) {
                    list.add(new UiElement(FROM_DOM_MAP.get(e)));
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "find error: " + e.toString(), e);
        } finally {
            clearData();
            Logger.w(TAG, "find using " + (System.currentTimeMillis() - start) + "ms");
        }
        return list;
    }

    public static Document getDocument() throws Exception {
        if (document == null) {
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        }
        return document;
    }

    public static void putToDom(AccessibilityNodeInfo node, Element element) {
        TO_DOM_MAP.put(node, element);

    }

    public static void putToNode(Element element, AccessibilityNodeInfo node) {
        FROM_DOM_MAP.put(element, node);
    }
}
