// ILppiModule.aidl
package com.android.internal.aidl.lppi;

// Declare any non-default types here with import statements

interface ILppiModule {
    /**
     * Demonstrates some basic types that you can use as parameters
     * and return values in AIDL.
     */
    boolean switchModule(String pkgName, boolean open);
    List getList();
}