package com.android.actor.device;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.R;
import com.android.actor.control.ActPackageManager;
import com.android.actor.monitor.Logger;
import com.android.actor.ui.Dialogs;
import com.android.actor.utils.Callback;
import com.android.actor.utils.recycler.RecyclerTextAdapter;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static com.android.actor.device.NewDeviceGenerator.MSG_DONE;
import static com.android.actor.device.NewDeviceGenerator.MSG_ERROR;

public class NewDeviceActivity extends Activity {

    private static final String TAG = NewDeviceActivity.class.getSimpleName();
    private TextView mSelectedAppText;
    private RadioGroup mCountryGroup;
    private Button mNewDeviceButton;
    private Button mResetDeviceButton;
    private TextView mOutputText;

    private NewDeviceGenerator mGenerator;
    private String mPackageName;
    private String mDeviceModel;
    private boolean mRandomGPS = false;
    private boolean mUseSim = false;
    private boolean mIsModify;
    private RecyclerTextAdapter mMockedPackageAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("改机");
        setContentView(R.layout.activity_new_device);
        mSelectedAppText = findViewById(R.id.device_select_app_text);
        mCountryGroup = findViewById(R.id.country);
        mNewDeviceButton = findViewById(R.id.device_new_device_btn);
        mResetDeviceButton = findViewById(R.id.device_reset_device_btn);
        mOutputText = findViewById(R.id.device_new_output);

        mGenerator = new NewDeviceGenerator(this);
        RadioGroup modelGroup = findViewById(R.id.device_model_group);
        mGenerator.getModelList().forEach(name -> {
            RadioButton radioButton = new RadioButton(this);
            radioButton.setText(name);
            radioButton.setOnClickListener(this::onDeviceModelClicked);
            modelGroup.addView(radioButton, new RadioGroup.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        });
        listUsed();
    }

    @SuppressLint("SetTextI18n")
    public void onSelectAppClicked(View v) {
        Dialogs.showSingleChoiceAppList(this, packageName -> {
            try {
                Logger.d(TAG, "Selected " + packageName);
                mPackageName = packageName;
                mSelectedAppText.setText(mPackageName + ":"+ActPackageManager.getInstance().getUidOfPackage(packageName));

                String country = NewStage.instance().getPackageParameterValue(packageName, "country");
                int id = getResources().getIdentifier("country_" + country, "id", getPackageName());
                if (id > 0) {
                    mCountryGroup.check(id);
                } else {
                    mCountryGroup.clearCheck();
                }
            }catch (Throwable e){
                Logger.e("setText error",e);
            }
        });
    }

    private List<String> getMockedList() {
        List<String> packageNames = NewStage.instance().getModifiedPackages();
        Collections.sort(packageNames);
        List<String> mockedList = new ArrayList<>(packageNames.size());
        for (String packageName : packageNames) {
            try {
                String model = NewStage.instance().getPackageSystemProperty(packageName, "ro.product.model");
                Logger.i(TAG, "packageName:" + packageName +  "model:"+model);
                if (model == null) {
                    continue;
                }
                int uid = ActPackageManager.getInstance().getUidOfPackage(packageName);
                Logger.d(TAG, "Mocked " + packageName + ", " + uid + ", " + model);
                String name = model;
                Optional<String> optional = mGenerator.getModelList().stream().filter(m -> m.endsWith(model)).findFirst();
                if (optional.isPresent()) {
                    name = optional.get();
                }
                mockedList.add(uid + " : " + name + " : "+ packageName);
            }catch (Throwable e){
                Logger.e(TAG, e);
            }
        }
        return mockedList;
    }

    private void listUsed() {
        RecyclerView recyclerView = findViewById(R.id.device_mocked_package);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        mMockedPackageAdapter = new RecyclerTextAdapter(this, getMockedList(), (text, msg, pos) -> {
            text.setText(msg);
        });
        recyclerView.setAdapter(mMockedPackageAdapter);
    }

    public void onDeviceModelClicked(View v) {
        mDeviceModel = ((RadioButton) v).getText().toString();
        Logger.d(TAG, "onDeviceModelClicked " + mDeviceModel);
    }

    public void onGpsRandomClicked(View v) {
        mRandomGPS = ((CheckBox) v).isChecked();
        Logger.d(TAG, "onGpsRandomClicked: " + mRandomGPS);
    }

    public void onSimRandomClicked(View v) {
        mUseSim = ((CheckBox) v).isChecked();
        Logger.d(TAG, "onSimRandomClicked: " + mUseSim);
    }

    public void onNewDeviceClicked(View v) {
        mIsModify = true;
        Logger.d(TAG, "onNewDeviceClicked, mIsModify: " + mIsModify);
        startModifyDevice();
    }

    public void onResetDeviceClicked(View v) {
        mIsModify = false;
        Logger.d(TAG, "onResetDeviceClicked, mIsModify: " + mIsModify);
        startModifyDevice();
    }

    @SuppressLint("SetTextI18n")
    private void startModifyDevice() {
        mOutputText.setText("");
        if (StringUtils.isEmpty(mPackageName)) {
            Toast.makeText(this, "No app selected", Toast.LENGTH_SHORT).show();
            return;
        }
        String model = mDeviceModel;
        mNewDeviceButton.setEnabled(false);
        mResetDeviceButton.setEnabled(false);

        String country = null;
        switch (mCountryGroup.getCheckedRadioButtonId()) {
            case R.id.country_us:
                country = "us";
                break;
            // 美国
            case R.id.country_uk:
                country = "uk";
                break;
            // 英国
            case R.id.country_br:
                country = "br";
                break;
            // 墨西哥
            case R.id.country_mx:
                country = "mx";
                break;
            case R.id.country_co:
                country = "co";
                break;
            // 澳大利亚
            case R.id.country_au:
                country = "au";
                break;
            // 法国
            case R.id.country_fr:
                country = "fr";
                break;
            // 德国
            case R.id.country_de:
                country = "de";
                break;
            case R.id.country_it:
                country = "it";
                break;
            case R.id.country_es:
                country = "es";
                break;
            // 中国
            case R.id.country_cn:
                country = "cn";
                break;
            case R.id.country_jp:
                country = "jp";
                break;
            // 马来西亚
            case R.id.country_my:
                country = "my";
                break;
            // 泰国
            case R.id.country_th:
                country = "th";
                break;
            // 菲律宾
            case R.id.country_ph:
                country = "ph";
                break;
            // 越南
            case R.id.country_vn:
                country = "vn";
                break;
            // 新加坡
            case R.id.country_sg:
                country = "sg";
                break;
            case R.id.country_in:
                country = "in";
                break;
            case R.id.country_ae:
                country = "ae";
                break;
        }
        mGenerator.generate(mPackageName, 0, model, country, mRandomGPS, mUseSim, mIsModify, (status, text) -> {
            Logger.d(TAG, "generate status: " + status + " mIsModify: " + mIsModify);
            switch (status) {
                case MSG_DONE:
                    Toast.makeText(NewDeviceActivity.this, "Done.", Toast.LENGTH_SHORT).show();
                    mNewDeviceButton.setEnabled(true);
                    mResetDeviceButton.setEnabled(true);
                    mMockedPackageAdapter.resetList(getMockedList());
                    break;
                case MSG_ERROR:
                    Toast.makeText(NewDeviceActivity.this, "ERROR!", Toast.LENGTH_SHORT).show();
                    mNewDeviceButton.setEnabled(true);
                    mResetDeviceButton.setEnabled(true);
                    break;
            }
            mOutputText.setText(mOutputText.getText() + "\n" + text);
        });
    }
}
