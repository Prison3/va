package com.android.actor.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import com.android.actor.R;
import com.android.actor.device.CameraMockManager;
import com.android.actor.monitor.Logger;

import java.util.List;

public class PagerCamera extends BasePager{
    private final static String TAG = PagerCamera.class.getSimpleName();
    private CheckBox mCameraCheck = null;
    private EditText mCameraEdit = null;

    private String mChosenPath = null;
    public PagerCamera(Context context) {
        super(context);
    }

    @Override
    public View bindPagerView() {
        @SuppressLint("InflateParams")
        View root = LayoutInflater.from(mContext).inflate(R.layout.pager_camera, null);

        mCameraCheck = root.findViewById(R.id.camera_check_mock);
        mCameraEdit = root.findViewById(R.id.camera_edit_mock);
        Button chooseBtn = root.findViewById(R.id.camera_btn_mock);
        chooseBtn.setOnClickListener(v -> {
            showChoiceMockMedia();
        });

        return root;
    }

    private void showChoiceMockMedia() {
        List<String> list = CameraMockManager.getInstance().getMediaList();
        if (list == null || list.size() == 0) {
            new AlertDialog.Builder(mContext)
                    .setTitle("No media found")
                    .setMessage("Please put media in " + CameraMockManager.MOCK_MEDIA_DIR)
                    .setPositiveButton("confirm", null)
                    .show();
            return;
        }

        CharSequence[] charSequences = new CharSequence[list.size()];
        for (int i = 0; i < list.size(); i++) {
            charSequences[i] = list.get(i);
        }
        new AlertDialog.Builder(mContext)
                .setTitle("Choose an media")
                .setSingleChoiceItems(charSequences, 0, (dialogInterface, i) -> {
                    mChosenPath = list.get(i);
                    Logger.d(TAG, "Dialog media choose " + mChosenPath + " onClick.");
                })
                .setPositiveButton("confirm", (dialogInterface, i) -> {
                    if (mChosenPath == null) {
                        mChosenPath = list.get(0);
                    }
                    Logger.i(TAG, "Dialog media choose " + mChosenPath + " confirm");
                    mCameraEdit.setText(mChosenPath);
                    if (CameraMockManager.getInstance().setMockData(mChosenPath, 0)){
                        mCameraCheck.setChecked(true);
                    }

                })
                .setNegativeButton("cancel", (dialogInterface, i) -> {
                    Logger.i(TAG, "Dialog wifi choose " + mChosenPath + " cancel");
                    mChosenPath = null;
                })
                .show();
    }
    @Override
    public String getTitle() {
        return "cam";
    }
}
