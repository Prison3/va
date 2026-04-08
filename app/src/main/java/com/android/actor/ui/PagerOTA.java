package com.android.actor.ui;

import android.content.Context;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.actor.ActApp;
import com.android.actor.R;
import com.android.actor.control.SelfUpdater;
import com.android.actor.grpc.ActorAdapter;
import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;

public class PagerOTA extends BasePager implements Logger.Listener {

    private static final String TAG = PagerOTA.class.getSimpleName();
    private ScrollView mLogScroll;
    private TextView mLogText;
    private StringBuilder mLogBuilder;
    private long mLastTouchTime;

    public PagerOTA(Context context) {
        super(context);
    }

    @Override
    public View bindPagerView() {
        View root = LayoutInflater.from(mContext).inflate(R.layout.pager_ota, null);
        CheckBox noDelayCheck = root.findViewById(R.id.no_delay_check);
        noDelayCheck.setChecked(SelfUpdater.instance().isNoDelay());
        noDelayCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SelfUpdater.instance().setNoDelay(isChecked);
            if (isChecked) {
                GRPCManager.getInstance().getActorChannel().resetChannel();
            }
        });
        mLogScroll = root.findViewById(R.id.log_scroll);
        mLogText = root.findViewById(R.id.log_text);
        mLogScroll.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mLastTouchTime = System.currentTimeMillis();
                return false;
            }
        });
        mLogBuilder = new StringBuilder();
        Logger.addLogListener(this, "SelfUpdater", "HttpDownloader");
        return root;
    }

    @Override
    public void onLog(Logger.Keep keep) {
        ActApp.post(() -> {
            mLogBuilder.append('\n');
            mLogBuilder.append(keep);
            mLogText.setText(mLogBuilder.toString());
            if (System.currentTimeMillis() - mLastTouchTime > 20 * 1000) {
                mLogText.setMovementMethod(new ScrollingMovementMethod());
                mLogScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    @Override
    public void unbindPagerView() {
        Logger.removeLogListener(this, "SelfUpdater", "HttpDownloader");
        mLogBuilder = null;
    }

    @Override
    public String getTitle() {
        return "ota";
    }
}
