package com.android.actor.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.actor.R;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.db.TUnit;
import com.android.actor.utils.db.TrafficContract;
import com.android.actor.utils.db.XUnit;
import com.android.actor.utils.db.XposedContract;
import com.android.actor.utils.recycler.RecyclerTextAdapter;

import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PagerGrpcInfo extends BasePager {
    private final static String TAG = PagerGrpcInfo.class.getSimpleName();
    private RecyclerView mRecycler;
    private RecyclerTextAdapter mAdapter;
    private static final List<String> mMsgCollector = new ArrayList<>();
    private static int mCollectorLength = 0;
    private final static int COLLECT_MAX_LENGTH = 20;
    private TextView mXposedCounter;
    private TextView mTrafficCounter;


    public PagerGrpcInfo(Context context) {
        super(context);
    }

    @Override
    public View bindPagerView() {
        View root = LayoutInflater.from(mContext).inflate(R.layout.pager_grpc, null);
        mRecycler = root.findViewById(R.id.grpc_recycler_view);
        mAdapter = new RecyclerTextAdapter(mContext, mMsgCollector, (textView, msg, position) -> {
            textView.setText(msg);
        });
        mRecycler.setLayoutManager(new LinearLayoutManager(mContext));
        mRecycler.setAdapter(mAdapter);
        mXposedCounter = root.findViewById(R.id.grpc_text_xposed);
        mTrafficCounter = root.findViewById(R.id.grpc_text_traffic);
        mTrafficCounter.setOnLongClickListener(v -> {
            queryAndUpdateView();
            return true;
        });
        queryXposedCounter();
        return root;
    }

    @SuppressLint("SetTextI18n")
    private void queryXposedCounter() {
        try {
            Date current = new Date(new java.util.Date().getTime());
            Date nextDay = new Date(current.getTime() + 24 * 60 * 60 * 1000);
            HashMap<String, List<XUnit>> map = XposedContract.queryAllCounter(current, nextDay);
            if (map != null) {
                StringBuilder sb = new StringBuilder();
                for (String pkg : map.keySet()) {
                    sb.append(pkg).append(":\n");
                    List<XUnit> list = map.get(pkg);
                    for (XUnit unit : list) {
                        sb.append(unit.action).append("_").append(unit.success ? "ok" : "false").append(":").append(unit.count).append("\n");
                    }
                    sb.append("\n");
                }
                String ret = sb.toString();
                if (mXposedCounter != null) {
                    mXposedCounter.setText(ret.isEmpty() ? "not found record" : ret);
                }
            } else {
                if (mXposedCounter != null) {
                    mXposedCounter.setText("query error.");
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, e.toString(), e);
        }
    }

    @SuppressLint("SetTextI18n")
    private void queryAndUpdateView() {
        try {
            Date current = new Date(new java.util.Date().getTime());
            Date nextDay = new Date(current.getTime() + 24 * 60 * 60 * 1000);

            HashMap<String, TUnit> map = TrafficContract.queryAllTraffic(current, nextDay);
            if (map != null) {
                StringBuilder sb = new StringBuilder();
                TUnit total = map.remove(TrafficContract.PKG_TOTAL);
                if (total != null) {
                    sb.append(TrafficContract.PKG_TOTAL).append(":\n")
                            .append("rsv: ").append(TrafficContract.getBytesText(total.rsv))
                            .append(", snd: ").append(TrafficContract.getBytesText(total.snd))
                            .append("\n");
                }
                for (String pkg : map.keySet()) {
                    TUnit unit = map.get(pkg);
                    if (unit.snd != 0 || unit.rsv != 0) {
                        sb.append(pkg).append(":\n")
                                .append("rsv: ").append(TrafficContract.getBytesText(unit.rsv))
                                .append(", snd: ").append(TrafficContract.getBytesText(unit.snd))
                                .append("\n");
                    }
                }
                String ret = sb.toString();
                if (mTrafficCounter != null) {
                    mTrafficCounter.setText(ret.isEmpty() ? "not found record" : ret);
                }
            } else {
                if (mTrafficCounter != null) {
                    mTrafficCounter.setText("query error");
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, e.toString(), e);
        }
    }

    public void inputMsg(String msg) {
        synchronized (mMsgCollector) {
            mMsgCollector.add(msg);
            if (mCollectorLength < COLLECT_MAX_LENGTH) {
                mCollectorLength++;
                if (mAdapter != null) {
                    mAdapter.notifyItemInserted(mCollectorLength - 1);
                }
            } else {
                mMsgCollector.remove(0);
                if (mAdapter != null) {
                    mAdapter.notifyItemRemoved(0);
                }
            }
        }
    }


    @Override
    public String getTitle() {
        return "grpc";
    }

}
