package com.android.actor.utils.db;

import android.annotation.SuppressLint;

public class XUnit {
    public final String pkg;
    public final String action;
    public final int count;
    public final boolean success;

    public XUnit(String pkg, String action, int count, boolean success) {
        this.pkg = pkg;
        this.action = action;
        this.count = count;
        this.success = success;
    }

    @SuppressLint("DefaultLocale")
    @Override
    public String toString() {
        return String.format("pkg: %s, action: %s, %s_count: %d",
                pkg, action, success ? "succeed" : "failed", count);
    }
}
