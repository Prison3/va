package com.android.actor.limited;

import static com.android.actor.device.NewDeviceGenerator.MSG_DONE;
import static com.android.actor.device.NewDeviceGenerator.MSG_ERROR;
import static com.android.actor.utils.notification.GlobalNotification.NOTIFY_PROXY_CHANGE;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.ActApp;
import com.android.actor.R;
import com.android.actor.control.ActActivityManager;
import com.android.actor.device.FlashConfig;
import com.android.actor.device.ManagedProfiles;
import com.android.actor.device.NewDeviceGenerator;
import com.android.actor.device.NewStage;
import com.android.actor.device.ProfilePackage;
import com.android.actor.monitor.Logger;
import com.android.actor.ui.Dialogs;
import com.android.actor.ui.TaskProgressBar;
import com.android.actor.utils.Callback;
import com.android.actor.utils.notification.ActObservable;
import com.android.actor.utils.notification.ActObserver;
import com.android.actor.utils.notification.GlobalNotification;
import com.android.actor.utils.proxy.ProxyManager;
import com.android.actor.utils.shell.Libsu;
import com.mackhartley.roundedprogressbar.RoundedProgressBar;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Arrays;

public class LimitedActivity extends Activity implements ActObserver {

    private static final String TAG = LimitedActivity.class.getSimpleName();
    private String mPackageName = FlashConfig.readAsJson(FlashConfig.LIMITED_EDITION).getString("package_name");
    private int mUid;
    private ImageView mIconView;
    private TextView mPackageNameView;
    private TextView mModelView;
    private RecyclerView mProfileListView;
    private ProfileListAdapter mListAdapter;
    private int mCurrentProfileId;

    private RadioGroup mCountryGroup;
    private Button mNewDeviceButton;
    private TextView mOutputText;
    private String mOutput = "";
    private NewDeviceGenerator mGenerator;
    private TextView mProxyInfoView;
    private Button mAppStartButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_limited);
        recursiveInit();
    }

    private void recursiveInit() {
        Libsu.waitReady(() -> {
            init();
        });
    }

    private void init() {
        mIconView = findViewById(R.id.app_icon);
        mPackageNameView = findViewById(R.id.package_name);
        mModelView = findViewById(R.id.model);
        mProfileListView = findViewById(R.id.profile_list);

        try {
            PackageInfo info = getPackageManager().getPackageInfo(mPackageName, 0);
            Drawable icon = info.applicationInfo.loadIcon(getPackageManager());
            mIconView.setImageDrawable(icon);
            mUid = info.applicationInfo.uid;
            mPackageNameView.setText(mPackageName + ": " + mUid);
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mProfileListView.setLayoutManager(layoutManager);
        mListAdapter = new ProfileListAdapter();
        mProfileListView.setAdapter(mListAdapter);

        mCountryGroup = findViewById(R.id.country);
        mNewDeviceButton = findViewById(R.id.device_new_device_btn);
        mOutputText = findViewById(R.id.device_new_output);
        mGenerator = new NewDeviceGenerator(this);

        mAppStartButton = findViewById(R.id.start);
        mProxyInfoView = findViewById(R.id.proxy_info);
        GlobalNotification.addObserver(NOTIFY_PROXY_CHANGE, this);
        refreshAll();
    }

    private void refreshAll() {
        mUid = ManagedProfiles.instance.getPackageProfileUid(mPackageName, mCurrentProfileId);
        mPackageNameView.setText(mPackageName + ": " + mUid);

        boolean startAllowed = true;
        try {
            String content = FileUtils.readFileToString(new File("/data/new_stage/params_store/" + mUid + "/" + mCurrentProfileId + "/device_param_list.json"));
            JSONObject jParameters = JSON.parseObject(content);
            String model = jParameters.getJSONObject("system_properties").getString("ro.product.model");
            mModelView.setText(model);
            String country = jParameters.getString("country");
            int id = getResources().getIdentifier("country_" + country, "id", getPackageName());
            if (id > 0) {
                mCountryGroup.check(id);
            } else {
                mCountryGroup.clearCheck();
            }
        } catch (Throwable e) {
            mModelView.setText("");
            mCountryGroup.clearCheck();
            startAllowed = false;
        }
        refreshProxyInfo();

        String proxy = ProxyManager.getInstance().getAppProxy(mPackageName, mCurrentProfileId);
        if (proxy == null) {
            startAllowed = false;
        }
        if (!FlashConfig.readAsJson(FlashConfig.LIMITED_EDITION).getBooleanValue("no_start_check")) {
            mAppStartButton.setEnabled(startAllowed);
        }
    }

    public void onNewDeviceClicked(View v) {
        Logger.d(TAG, "onNewDeviceClicked, mCurrentProfileId: " + mCurrentProfileId);
        startModifyDevice();
    }

    private void startModifyDevice() {
        mOutputText.setText("");
        mOutput = "";
        if (StringUtils.isEmpty(mPackageName)) {
            Toast.makeText(this, "No app selected", Toast.LENGTH_SHORT).show();
            return;
        }
        String model = null;
        mNewDeviceButton.setEnabled(false);

        String country = null;
        switch (mCountryGroup.getCheckedRadioButtonId()) {
            case R.id.country_us:
                country = "us";
                break;
            case R.id.country_uk:
                country = "uk";
                break;
            case R.id.country_br:
                country = "br";
                break;
            case R.id.country_cn:
                country = "cn";
                break;
            case R.id.country_my:
                country = "my";
                break;
            case R.id.country_th:
                country = "th";
                break;
            case R.id.country_ph:
                country = "ph";
                break;
            case R.id.country_vn:
                country = "vn";
                break;
            case R.id.country_mm:
                country = "mm";
                break;
            case R.id.country_in:
                country = "in";
                break;
        }

        mGenerator.generate(mPackageName, mCurrentProfileId, model, country, false, false, true, (status, text) -> {
            Logger.d(TAG, "generate status: " + status);
            switch (status) {
                case MSG_DONE:
                    Toast.makeText(LimitedActivity.this, "Done.", Toast.LENGTH_SHORT).show();
                    mNewDeviceButton.setEnabled(true);
                    refreshAll();
                    break;
                case MSG_ERROR:
                    Toast.makeText(LimitedActivity.this, "ERROR!", Toast.LENGTH_SHORT).show();
                    mNewDeviceButton.setEnabled(true);
                    break;
            }

            if (status != MSG_ERROR) {
                mOutput += text + "<br/>";
            } else {
                mOutput += "<font color='#FF0000'>" + text + "</font><br/>";
            }
            mOutputText.setText(Html.fromHtml(mOutput));
        });
    }

    public void onProxyModifyClicked(View v) {
        ProxyManager.Proxy proxy = ProxyManager.getInstance().getProxy(mPackageName, mCurrentProfileId);
        ProfilePackage pkg = ProfilePackage.create(mPackageName, mCurrentProfileId);
        Dialogs.showProxyEditTextDialog(this, proxy != null ? proxy.socks : null, pkg);
    }

    public void onProxyCheckClicked(View v) {
        new Thread(() -> {
            ProxyManager.Proxy proxy = ProxyManager.getInstance().getProxy(mPackageName, mCurrentProfileId);
            boolean ok = ProxyManager.testProxy(proxy);
            runOnUiThread(() -> {
                if (ok) {
                    Toast.makeText(this, "代理 OK", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "代理不通", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void refreshProxyInfo() {
        ProxyManager.Proxy proxy = ProxyManager.getInstance().getProxy(mPackageName, mCurrentProfileId);
        Logger.d(TAG, "refreshProxyInfo " + mPackageName + "-" + mCurrentProfileId + ", " + proxy);
        if (proxy == null) {
            mProxyInfoView.setText("未配置");
        } else {
            mProxyInfoView.setText(proxy.socks.getProxy());
        }
        Logger.d(TAG, "mProxyInfoView " + mProxyInfoView.getText());
    }

    @Override
    public void update(ActObservable obs, Object arg) {
        runOnUiThread(() -> {
            refreshAll();
        });
    }

    public void onAppStartClicked(View v) {
        mAppStartButton.setEnabled(false);
        TaskProgressBar progressBar = new TaskProgressBar(this).show();
        new Thread(() -> {
            String reason = NewStage.instance().switchProfile(mPackageName, mCurrentProfileId, n -> {
                runOnUiThread(() -> {
                    progressBar.setProgress(n);
                });
            });
            runOnUiThread(() -> {
                if (reason != null) {
                    Logger.e(TAG, "Error to start app, " + reason);
                    Toast.makeText(this, "ERROR.", Toast.LENGTH_LONG).show();
                } else {
                    refreshAll();
                    ProxyManager.getInstance().switchProfile(mPackageName, mCurrentProfileId);
                    ActActivityManager.getInstance().moveToFront(mPackageName);
                }
                mAppStartButton.setEnabled(true);
                progressBar.dismiss();
            });
        }).start();
    }

    public void onAppStopClicked(View v) {
        ActActivityManager.getInstance().forceStopPackage(mPackageName);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView titleView;

        public ViewHolder(@NonNull View view) {
            super(view);
            titleView = view.findViewById(R.id.title);
        }
    }

    class ProfileListAdapter extends RecyclerView.Adapter<ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile_title, parent, false);
            ViewHolder holder = new ViewHolder(view);
            view.setOnClickListener(v -> {
                int position = holder.getAdapterPosition();
                int[] ids = ManagedProfiles.instance.getAppProfileIds(mPackageName);
                if (position < ids.length) {
                    mCurrentProfileId = ids[position];
                } else {
                    ManagedProfiles.instance.createAppDataDir(mPackageName);
                }
                notifyDataSetChanged();
                refreshAll();
            });
            return holder;
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            holder.itemView.setBackgroundColor(Color.LTGRAY);
            int[] ids = ManagedProfiles.instance.getAppProfileIds(mPackageName);
            if (position < ids.length) {
                int profileId = ids[position];
                holder.titleView.setText("Profile " + profileId);
                if (profileId == mCurrentProfileId) {
                    holder.itemView.setBackgroundColor(Color.GREEN);
                }
            } else {
                holder.titleView.setText("+");
            }
        }

        @Override
        public int getItemCount() {
            return ManagedProfiles.instance.getAppProfileIds(mPackageName).length + 1;
        }
    }
}
