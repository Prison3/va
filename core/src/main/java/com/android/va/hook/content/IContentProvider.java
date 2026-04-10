package com.android.va.hook.content;

import android.os.IInterface;

public interface IContentProvider {
    IInterface wrapper(final IInterface contentProviderProxy, final String appPkg, String au);
}
