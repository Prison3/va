package com.android.actor.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.actor.R;
import com.android.actor.script.ScriptExecutor;
import com.android.actor.script.lua.LuaLogger;
import com.android.actor.script.lua.LuaScript;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.recycler.RecyclerTextAdapter;

import java.util.ArrayList;
import java.util.Arrays;


public class PagerScript extends BasePager {
    private final static int COLOR_E = Color.rgb(0xCC, 0x66, 0x6E);
    private final static int COLOR_V = Color.rgb(0x53, 0x94, 0xEC);
    private final static int COLOR_I = Color.rgb(0xA8, 0xC0, 0x23);
    private final static int COLOR_W = Color.rgb(0xA8, 0xC0, 0x23);
    private TextView mTaskInfoText;
    private Button mStopBtn;
    private Button mRetryBtn;
    private RecyclerView mRecycler;
    private RecyclerTextAdapter mAdapter;

    public PagerScript(Context context) {
        super(context);
    }

    @Override
    public View bindPagerView() {
        @SuppressLint("InflateParams")
        View root = LayoutInflater.from(mContext).inflate(R.layout.pager_task, null);
        mTaskInfoText = root.findViewById(R.id.task_text_info);
        mRetryBtn = root.findViewById(R.id.task_btn_retry);
        mStopBtn = root.findViewById(R.id.task_btn_stop);
        mStopBtn.setVisibility(View.INVISIBLE);
        mRetryBtn.setVisibility(View.INVISIBLE);
        mRecycler = root.findViewById(R.id.task_recycler_logs);
        mAdapter = new RecyclerTextAdapter(mContext, SPUtils.getString(SPUtils.ModuleFile.script, "record"), (text, msg, pos) -> {
            if (msg != null && !msg.isEmpty()) {
                if (msg.startsWith(LuaLogger.Level.D.name())) {
                    text.setTextColor(Color.BLACK);
                } else if (msg.startsWith(LuaLogger.Level.E.name())) {
                    text.setTextColor(COLOR_E);
                } else if (msg.startsWith(LuaLogger.Level.V.name())) {
                    text.setTextColor(COLOR_V);
                } else if (msg.startsWith(LuaLogger.Level.I.name())) {
                    text.setTextColor(COLOR_I);
                } else if (msg.startsWith(LuaLogger.Level.W.name())) {
                    text.setTextColor(COLOR_W);
                }
                text.setText(msg);
            }
        });
        mRecycler.setLayoutManager(new LinearLayoutManager(mContext));
        mRecycler.setAdapter(mAdapter);
        mStopBtn.setOnClickListener(v -> {
            ScriptExecutor.getInstance().stopScript();
        });
        mRetryBtn.setOnClickListener(v -> {
            ScriptExecutor.getInstance().retry();
        });
        updateTaskStatus();
        return root;
    }

    public void updateTaskStatus() {
        String taskId = SPUtils.getString(SPUtils.ModuleFile.script, "taskId");
        if (taskId != null) {
            String status = SPUtils.getString(SPUtils.ModuleFile.script, "status");
            String createTime = SPUtils.getString(SPUtils.ModuleFile.script, "createTime");
            String finishTime = SPUtils.getString(SPUtils.ModuleFile.script, "finishTime");
            String msg = String.format("task_id: %s\ncreated_at: %s\nfinished_at: %s\nstatus:%s", taskId, createTime, finishTime, status);
            if (mTaskInfoText != null) {
                mTaskInfoText.setText(msg);
            }
            if (status != null) {
                if (!status.equals(LuaScript.LuaStatus.notfound.name()) &&
                        !status.equals(LuaScript.LuaStatus.running.name())) {
                    String record = SPUtils.getString(SPUtils.ModuleFile.script, "record");
                    if (mAdapter != null) {
                        mAdapter.resetList(record == null ? new ArrayList<>() : Arrays.asList(record.split("\n")));
                        mAdapter.notifyDataSetChanged();
                    }
                    if (ScriptExecutor.getInstance().geCurrentTaskId() != null && mRetryBtn != null) {
                        mRetryBtn.setVisibility(View.VISIBLE);
                    }
                } else {
                    if (mRetryBtn != null) {
                        mRetryBtn.setVisibility(View.INVISIBLE);
                    }
                }
                if (status.equals(LuaScript.LuaStatus.running.name())) {
                    if (mStopBtn != null) {
                        mStopBtn.setVisibility(View.VISIBLE);
                    }
                    if (mAdapter != null) {
                        mAdapter.resetList(new ArrayList<>());
                        mAdapter.notifyDataSetChanged();
                    }
                } else {
                    if (mStopBtn != null) {
                        mStopBtn.setVisibility(View.INVISIBLE);
                    }
                }
            }
        }
    }

    @Override
    public String getTitle() {
        return "script";
    }
}
