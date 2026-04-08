package com.android.actor.ui;

import android.content.Context;
import android.view.View;

abstract class BasePager {
    protected Context mContext;
    public BasePager(Context context){
        mContext = context;
    }
    public abstract View bindPagerView();
    public void unbindPagerView() {}
    public abstract String getTitle();
}
