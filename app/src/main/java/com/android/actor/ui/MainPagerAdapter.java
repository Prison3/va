package com.android.actor.ui;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;

import com.android.actor.monitor.Logger;

import java.util.ArrayList;
import java.util.List;

public class MainPagerAdapter extends PagerAdapter {
    private final static String TAG = MainPagerAdapter.class.getSimpleName();
    private final List<BasePager> mViewList = new ArrayList<>();

    public MainPagerAdapter() {
        Logger.d(TAG, "MainPagerAdapter init");
    }

    public void register(BasePager pager) {
        if (!mViewList.contains(pager)) {
            mViewList.add(pager);
        }
    }

    @Override
    public int getCount() {
        return mViewList.size();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    @NonNull
    @Override
    public View instantiateItem(@NonNull ViewGroup container, int position) {
        View view = mViewList.get(position).bindPagerView();
        container.addView(view);
        view.setTag(mViewList.get(position));
        return view;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, @NonNull Object object) {
        View view = (View) object;
        BasePager pager = (BasePager) view.getTag();
        pager.unbindPagerView();
        container.removeView(view);
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        return mViewList.get(position).getTitle();
    }
}
