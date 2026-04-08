package com.android.actor.control.trace;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.actor.BuildConfig;
import com.android.actor.R;
import com.android.actor.control.ActPackageManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.recycler.RecyclerTextAdapter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TraceRecordActivity extends Activity implements View.OnClickListener {
    private final static String TAG = TraceRecordActivity.class.getSimpleName();
    private TraceRecordView mRecordView;
    private final List<EventTrace> mTraceList = new ArrayList<>();
    private RecyclerTextAdapter mAdapter;
    private String mChosenPkgName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trace_record);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        findViewById(R.id.trace_btn_reset).setOnClickListener(this);
        findViewById(R.id.trace_btn_record).setOnClickListener(this);
        findViewById(R.id.trace_btn_drop).setOnClickListener(this);
        findViewById(R.id.trace_btn_export).setOnClickListener(this);
        findViewById(R.id.trace_btn_tips).setOnClickListener(this);
        findViewById(R.id.trace_x5sec_view).setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);

        mRecordView = findViewById(R.id.trace_record_view);
        RecyclerView recyclerView = findViewById(R.id.trace_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        mAdapter = new RecyclerTextAdapter(this, new ArrayList<>(), (textView, msg, pos) -> {
            textView.setText(msg);
            textView.setOnClickListener(view -> {
                Logger.d(TAG, "\n" + mTraceList.get(pos).getRecordString());
                mRecordView.redraw(mTraceList.get(pos));
            });
        });
        recyclerView.setAdapter(mAdapter);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.trace_btn_reset:
                Logger.d(TAG, "on click retry btn");
                mRecordView.reset();
                break;
            case R.id.trace_btn_record:
                Logger.d(TAG, "on click record btn");
                if (!mRecordView.canDraw) {
                    EventTrace trace = mRecordView.record();
                    if (trace != null) {
                        mTraceList.add(trace);
                        String msg = trace.toString();
                        mAdapter.add(msg);
                    }
                }
                break;
            case R.id.trace_btn_drop:
                if (mTraceList.size() > 0) {
                    mTraceList.remove(mTraceList.size() - 1);
                    mAdapter.remove();
                }
                break;
            case R.id.trace_btn_export:
                if (mTraceList.size() > 0) {
                    showChooseAppDialog();
                }
                break;
            case R.id.trace_btn_tips:
                showTipDialog();
                break;
            default:
                break;
        }
    }

    private void showTipDialog() {
        String msg = "1. draw a trace above.\n"
                + "2. click reset to redraw a trace."
                + "3. click record to save a trace to below.\n"
                + "4. click item below and get detail from logcat.\n"
                + "5. click drop to remove last trace.\n"
                + "6. click export and choose app and prefix.\n"
                + "7. all traces will store to cache/pkg/prefix_timestamp";
        new AlertDialog.Builder(TraceRecordActivity.this)
                .setMessage(msg)
                .show();
    }

    private void showChooseAppDialog() {
        List<PackageInfo> pkgList = ActPackageManager.getInstance().getInstalledPackage();
        CharSequence[] appNames = new CharSequence[pkgList.size()];
        for (int i = 0; i < pkgList.size(); i++) {
            appNames[i] = pkgList.get(i).applicationInfo.loadLabel(ActPackageManager.getInstance().getPackageManager());
        }

        new AlertDialog.Builder(TraceRecordActivity.this)
                .setTitle("Choose an app")
                .setSingleChoiceItems(appNames, 0, (dialogInterface, i) -> {
                    mChosenPkgName = pkgList.get(i).packageName;
                    Logger.d(TAG, "Dialog app item " + mChosenPkgName + " onClick.");
                })
                .setPositiveButton("confirm", (dialogInterface, which) -> {
                    if (mChosenPkgName == null) {
                        mChosenPkgName = pkgList.get(0).packageName;
                    }
                    Logger.i(TAG, "Dialog app item " + mChosenPkgName + " confirm.");
                    showEditPrefixDialog();
                })
                .setNegativeButton("cancel", (dialogInterface, which) -> {
                    Logger.d(TAG, "Dialog app item " + mChosenPkgName + " cancel.");
                    mChosenPkgName = null;
                })
                .show();
    }

    private void showEditPrefixDialog() {
        EditText editText = new EditText(TraceRecordActivity.this);
        editText.setHint("file name prefix");
        new AlertDialog.Builder(TraceRecordActivity.this)
                .setMessage("Input password")
                .setView(editText)
                .setPositiveButton("confirm", (dd, ii) -> {
                    String prefix = editText.getText().toString();
                    Logger.i(TAG, "store to " + mChosenPkgName + "/" + prefix + " with " + mTraceList.size() + " trace.");
                    for (EventTrace trace : mTraceList) {
                        try {
                            TraceUtils.storeToFile(mChosenPkgName, prefix, trace);
                            Toast.makeText(TraceRecordActivity.this, "save traces successfully.", Toast.LENGTH_SHORT).show();
                        } catch (IOException e) {
                            e.printStackTrace();
                            Logger.e(TAG, e.toString());
                        }
                    }
                })
                .setNegativeButton("cancel", null)
                .show();
    }
}