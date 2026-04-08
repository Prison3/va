package com.android.actor.device;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.actor.ActApp;
import com.android.actor.R;
import com.android.actor.control.ActPackageManager;
import com.android.actor.control.RocketComponent;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.db.TUnit;
import com.android.actor.utils.db.TrafficContract;
import com.android.actor.utils.db.XUnit;
import com.android.actor.utils.db.XposedContract;
import com.android.actor.utils.recycler.RecyclerTextAdapter;

import java.sql.Date;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;


public class TrafficActivity extends Activity implements View.OnClickListener {
    private final static String TAG = TrafficActivity.class.getSimpleName();
    private Calendar mCalendar = Calendar.getInstance();
    private EditText mStartEdit;
    private EditText mEndEdit;
    private EditText mAppEdit;
    private Date mStart;
    private Date mEnd;
    private String mPackage = TrafficContract.PKG_TOTAL;
    private TrafficView mSerialView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_traffic);
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);

        findViewById(R.id.traffic_btn_start).setOnClickListener(this);
        findViewById(R.id.traffic_btn_end).setOnClickListener(this);
        findViewById(R.id.traffic_btn_app).setOnClickListener(this);
        findViewById(R.id.traffic_btn_query).setOnClickListener(this);
        mStartEdit = findViewById(R.id.traffic_edit_start);
        mEndEdit = findViewById(R.id.traffic_edit_end);
        mAppEdit = findViewById(R.id.traffic_edit_app);
        mSerialView = findViewById(R.id.traffic_serial_view);
        setDefaultConfig();
    }

    private void setDefaultConfig() {
        mCalendar = Calendar.getInstance();
        mStart = new Date(mCalendar.getTime().getTime());
        mStartEdit.setText(mStart.toString());

        mCalendar.add(Calendar.DAY_OF_MONTH, 1);

        mEnd = new Date(mCalendar.getTime().getTime());
        mEndEdit.setText(mEnd.toString());

        mAppEdit.setText(mPackage);
        RecyclerView recyclerView = findViewById(R.id.xposed_recycler);
        recyclerView.setAdapter(mAdapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
    }

    private final List<String> mXposedDataSource = new ArrayList<>();
    private final RecyclerTextAdapter mAdapter = new RecyclerTextAdapter(this, mXposedDataSource, ((textView, msg, position) -> {
        textView.setText(mXposedDataSource.get(position));
    }));

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            this.finish();
        }
        return super.onOptionsItemSelected(item);
    }


    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.traffic_btn_start:
                mCalendar = Calendar.getInstance();
                new DatePickerDialog(
                        TrafficActivity.this,
                        (view, year, month, dayOfMonth) -> {
                            mCalendar.set(Calendar.YEAR, year);
                            mCalendar.set(Calendar.MONDAY, month);
                            mCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                            mStart = new Date(mCalendar.getTime().getTime());
                            Logger.d(TAG, "onDateSet: start " + mStart);
                            mStartEdit.setText(mStart.toString());
                        },
                        mCalendar.get(Calendar.YEAR),
                        mCalendar.get(Calendar.MONTH),
                        mCalendar.get(Calendar.DAY_OF_MONTH))
                        .show();
                break;
            case R.id.traffic_btn_end:
                mCalendar = Calendar.getInstance();
                mCalendar.add(Calendar.DAY_OF_MONTH, 1);
                new DatePickerDialog(
                        TrafficActivity.this,
                        (view, year, month, dayOfMonth) -> {
                            mCalendar.set(Calendar.YEAR, year);
                            mCalendar.set(Calendar.MONDAY, month);
                            mCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                            Date date = new Date(mCalendar.getTime().getTime());
                            if (date.after(mStart)) {
                                mEnd = date;
                            } else {
                                mEnd = mStart;
                                mStart = date;
                                mStartEdit.setText(mStart.toString());
                            }
                            mEndEdit.setText(mEnd.toString());
                            Logger.d(TAG, "onDateSet: end " + mEnd);
                        },
                        mCalendar.get(Calendar.YEAR),
                        mCalendar.get(Calendar.MONTH),
                        mCalendar.get(Calendar.DAY_OF_MONTH))
                        .show();
                break;
            case R.id.traffic_btn_app:
                showChoiceAppDialog();
                break;
            case R.id.traffic_btn_query:
                syncQuery();
                break;
            default:
                break;
        }
    }

    private void showChoiceAppDialog() {
        List<PackageInfo> pkgInfoList = new ArrayList<>();
        for (PackageInfo pkgInfo : ActPackageManager.getInstance().getInstalledPackage()) {
            // ignore rocket component app except actor。
            if (!RocketComponent.isRocketComponent(pkgInfo.packageName)
                    || pkgInfo.packageName.equals(ActApp.getInstance().getPackageName())) {
                pkgInfoList.add(pkgInfo);
            }
        }
        List<String> pkgList = new ArrayList<>();
        CharSequence[] appNames = new CharSequence[pkgInfoList.size() + 1];
        appNames[0] = TrafficContract.PKG_TOTAL;
        pkgList.add(TrafficContract.PKG_TOTAL);
        for (int i = 0; i < pkgInfoList.size(); i++) {
            appNames[i + 1] = pkgInfoList.get(i).applicationInfo.loadLabel(ActPackageManager.getInstance().getPackageManager());
            pkgList.add(pkgInfoList.get(i).packageName);
        }
        new AlertDialog.Builder(TrafficActivity.this)
                .setTitle("Choose an app")
                .setSingleChoiceItems(appNames, pkgList.indexOf(mPackage), (dialogInterface, i) -> {
                    mPackage = pkgList.get(i);
                    Logger.d(TAG, "Dialog app item " + mPackage + " onClick.");
                })
                .setPositiveButton("confirm", (dialogInterface, which) -> {
                    Logger.i(TAG, "Dialog app item " + mPackage + " confirm.");
                    mAppEdit.setText(mPackage);
                })
                .setNegativeButton("cancel", (dialogInterface, which) -> {
                    Logger.d(TAG, "Dialog app item " + mPackage + " cancel.");
                })
                .show();
    }

    private void syncQuery() {
        if (mPackage == null) {
            return;
        }
        try {
            List<TUnit> dataSerial = TrafficContract.queryPackageTrafficSerial(mPackage, mStart, mEnd);
            if (dataSerial != null && dataSerial.size() > 0) {
                mSerialView.drawSerial(dataSerial);
            }
        } catch (Exception e) {
            Logger.e(TAG, e.toString(), e);
        }
        try {
            mXposedDataSource.clear();
            if (mPackage.equals(TrafficContract.PKG_TOTAL)) {
                HashMap<String, List<XUnit>> pkgMap = XposedContract.queryAllCounter(mStart, mEnd);
                for (String pkg : pkgMap.keySet()) {
                    for (XUnit unit : pkgMap.get(pkg)) {
                        mXposedDataSource.add(unit.toString());
                    }
                }
            } else {
                for (XUnit unit : XposedContract.queryPackageCounter(mPackage, mStart, mEnd)) {
                    mXposedDataSource.add(unit.toString());
                }
            }
            Logger.d(TAG, "refresh xposed recycler with " + mXposedDataSource.size() + " items.");
            mAdapter.resetList(mXposedDataSource);
        } catch (Exception e) {
            Logger.e(TAG, e.toString(), e);
        }
        Toast.makeText(this, "query success", Toast.LENGTH_SHORT).show();
    }
}
