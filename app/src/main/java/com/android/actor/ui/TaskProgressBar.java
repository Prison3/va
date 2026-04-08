package com.android.actor.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import com.android.actor.R;
import com.mackhartley.roundedprogressbar.RoundedProgressBar;

public class TaskProgressBar {

    private Context mContext;
    private AlertDialog mDialog;
    private RoundedProgressBar mProgressBar;

    public TaskProgressBar(Context context) {
        mContext = context;
    }

    public TaskProgressBar show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        View view = LayoutInflater.from(mContext).inflate(R.layout.dialog_task_progress, null);
        builder.setView(view);
        builder.setCancelable(false);
        mDialog = builder.show();
        mProgressBar = view.findViewById(R.id.advanced_bar_5);
        return this;
    }

    public void setProgress(int percent) {
        mProgressBar.setProgressPercentage(percent, true);
    }

    public void dismiss() {
        mDialog.dismiss();
    }
}
