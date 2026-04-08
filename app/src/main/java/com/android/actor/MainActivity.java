package com.android.actor;

import static com.android.actor.device.ActConnManager.USE_WIFI;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.*;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.ViewPager;

import com.android.actor.device.ActConnManager;
import com.android.actor.device.ActWifiManager;
import com.android.actor.device.BasicInfo;
import com.android.actor.device.DeviceInfoManager;
import com.android.actor.device.DeviceNumber;
import com.android.actor.device.NewDeviceActivity;
import com.android.actor.ui.InstallActivity;
import com.android.actor.fi.FIEntryPoint;
import com.android.actor.grpc.ActorAdapter;
import com.android.actor.grpc.NodeAddsListAdapter;
import com.android.actor.grpc.GRPCManager;
import com.android.actor.monitor.Logger;
import com.android.actor.ui.Dialogs;
import com.android.actor.ui.MainPagerAdapter;
import com.android.actor.ui.PagerCamera;
import com.android.actor.ui.PagerGrpcInfo;
import com.android.actor.ui.PagerOTA;
import com.android.actor.ui.PagerProxy;
import com.android.actor.ui.PagerScript;
import com.android.actor.ui.PagerApps;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.SPUtils;
import com.android.actor.utils.notification.ActObservable;
import com.android.actor.utils.notification.ActObserver;
import com.android.actor.utils.notification.GlobalNotification;
import com.android.actor.utils.shell.Libsu;
import com.ogaclejapan.smarttablayout.SmartTabLayout;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;


public class MainActivity extends Activity implements View.OnClickListener, View.OnFocusChangeListener, ActObserver, View.OnLongClickListener {
    private final static String TAG = MainActivity.class.getSimpleName();

    /** 与 {@link #initPagers()} 中 {@code mAdapter.register} 顺序一致：0=apps，1=grpc，2=ota */
    private static final int PAGE_INDEX_APPS = 0;
    public final static String RECEIVER_SERVICE_READY = "com.rocket.SERVICE_READY";

    private final static HashMap<Integer, String> NOTIFICATION_MAP = new HashMap<>();

    static {
        NOTIFICATION_MAP.put(GlobalNotification.NOTIFY_GRPC_CONNECT, "grpc_connection");
        NOTIFICATION_MAP.put(GlobalNotification.NOTIFY_TASK_STATUS, "task_status");
        NOTIFICATION_MAP.put(GlobalNotification.NOTIFY_WIFI_CHANGE, "wifi_change");
        NOTIFICATION_MAP.put(GlobalNotification.NOTIFY_GRPC_MSG, "grpc_msg");
        NOTIFICATION_MAP.put(GlobalNotification.NOTIFY_BATTERY_CHANGE, "battery_change");
    }

    private TextView mBasicInfoText;
    private TextView mSerialText;
//    private CheckBox mRootCheckbox;
//    private CheckBox mAccessibilityCheckbox;
    private CheckBox mWifiCheckbox;
    private TextView mWifiText;
    private Button mWifiBtn;
    private String mChosenWifi;
   // private TextView mBatteryText;
    public EditText mGrpcAddressEditor;
    private CheckBox mGrpcCheckbox;
    private Button mGrpcBtn;

    private Handler mHandler;

    private PagerApps mPagerApps;
    private PagerGrpcInfo mPagerGrpc;
    private PagerScript mPagerTask;
    private PagerProxy mPagerProxy;
    private PagerOTA mPagerOTA;
    private PagerCamera mPagerCamera;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.i(TAG, "onCreate ================================");
        mHandler = new UIHandler();

        setContentView(R.layout.activity_main);
        initPagers();
        initBasicInfo();
        initObserver();

        String packageName = SPUtils.getString(SPUtils.ModuleFile.fi, "package_name");
        if (!StringUtils.isEmpty(packageName)) {
            mBasicInfoText.setText(packageName);
        }
        startService(new Intent(this, AlphaService.class));
    }

    public void initPagers() {
        ViewPager mViewPager = findViewById(R.id.main_pager);
        SmartTabLayout tab = findViewById(R.id.main_tab);
        MainPagerAdapter mAdapter = new MainPagerAdapter();
        mPagerApps = new PagerApps(this);
        mPagerGrpc = new PagerGrpcInfo(this);
//        mPagerTask = new PagerScript(this);
//        mPagerProxy = new PagerProxy(this);
        mPagerOTA = new PagerOTA(this);
        //mPagerCamera = new PagerCamera(this);
        mAdapter.register(mPagerApps);
        mAdapter.register(mPagerGrpc);
//        mAdapter.register(mPagerTask);
//        mAdapter.register(mPagerProxy);
        mAdapter.register(mPagerOTA);
        //mAdapter.register(mPagerCamera);
        mViewPager.setAdapter(mAdapter);
        mViewPager.setCurrentItem(1);
        mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (position == PAGE_INDEX_APPS) {
                    mPagerApps.refreshVirtualApps();
                }
            }
        });
        tab.setViewPager(mViewPager);
    }

    public void initBasicInfo() {
        mBasicInfoText = findViewById(R.id.basic_info);
        mBasicInfoText.setOnClickListener(this);
        mSerialText = findViewById(R.id.main_serial);
        mSerialText.setOnClickListener(this);
//        mRootCheckbox = findViewById(R.id.main_check_root);
//        mAccessibilityCheckbox = findViewById(R.id.main_check_accessibility);
        mWifiCheckbox = findViewById(R.id.main_check_wifi);
        mWifiText = findViewById(R.id.main_text_wifi);
        mWifiBtn = findViewById(R.id.main_btn_wifi);
        mWifiBtn.setOnClickListener(this);
       // mBatteryText = findViewById(R.id.main_battery_info);
       // mBatteryText.setOnLongClickListener(this);
        mGrpcAddressEditor = findViewById(R.id.main_edit_grpc);
        refreshGrpcAddress();
        mGrpcCheckbox = findViewById(R.id.main_check_grpc);
        mGrpcBtn = findViewById(R.id.main_btn_grpc);
        mGrpcBtn.setOnClickListener(this);
        mGrpcAddressEditor.setText(ActorAdapter.DEFAULT_RETRY_ADDRESS);
        mGrpcAddressEditor.setOnFocusChangeListener(this);
        mGrpcBtn.setOnClickListener(this);
    }

    public void initObserver() {
        Logger.d(TAG, "Add accessibility service receiver.");
        IntentFilter filter = new IntentFilter();
        filter.addAction(RECEIVER_SERVICE_READY);
        filter.addAction(ActBroadcastReceiver.SET_GRPC);
        registerReceiver(mServiceReadyReceiver, filter);
        for (int code : NOTIFICATION_MAP.keySet()) {
            GlobalNotification.addObserver(code, this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onMenuOpened(int featureId, @NonNull Menu menu) {
        if (featureId == Window.FEATURE_ACTION_BAR && menu != null) {
            int type = DeviceInfoManager.getInstance().getConnManager().getType();
//            menu.findItem(R.id.menu_4g).setChecked(type == USE_4G);
//            menu.findItem(R.id.menu_enable_wired).setChecked(SPUtils.getBoolean(SPUtils.ModuleFile.wired_network, "enable_wired"));
//            menu.findItem(R.id.menu_keep_screen_on).setChecked(!BrightnessController.instance().isEnabled());
//            menu.findItem(R.id.menu_scrcpy_dedicated).setChecked(ScrcpyDedicated.instance.isEnabled());
        }
        return super.onMenuOpened(featureId, menu);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
//            case R.id.menu_set_fi_package:
//                Logger.d(TAG, "onClick fi package");
//                Dialogs.showSingleChoiceAppList(this, new Callback.C1<String>() {
//                    @Override
//                    public void onResult(String packageName) {
//                        Logger.i(TAG, "Set fi package " + packageName);
//                        SPUtils.putString(SPUtils.ModuleFile.fi, "package_name", packageName);
//                        mBasicInfoText.setText(packageName);
//                    }
//                });
                //break;
//            case R.id.menu_net_traffic:
//                Logger.d(TAG, "onClick traffic item");
//                startActivity(new Intent(this, TrafficActivity.class));
//                break;
            case R.id.menu_one_key_new_device:
                Logger.d(TAG, "onClick one key new device");
                startActivity(new Intent(this, NewDeviceActivity.class));
                break;
            case R.id.menu_install_virtual_app:
                Logger.d(TAG, "onClick install virtual app");
                startActivity(new Intent(this, InstallActivity.class));
                break;
//            case R.id.menu_4g:
//                Logger.d(TAG, "onClick 4g");
//                int type = DeviceInfoManager.getInstance().getConnManager().getType();
//                DeviceInfoManager.getInstance().getConnManager().enableWired(type == USE_4G ? USE_WIFI : USE_4G);
//                Libsu.rebootWithNecessary(this);
                //break;
//            case R.id.menu_enable_wired:
//                boolean enabled = SPUtils.getBoolean(SPUtils.ModuleFile.wired_network, "enable_wired");
//                enabled = !enabled;
//                SPUtils.putBoolean(SPUtils.ModuleFile.wired_network, "enable_wired", enabled);
//                DeviceInfoManager.getInstance().getConnManager().getWired().setEnabled(enabled);
//                break;
//            case R.id.menu_keep_screen_on:
//                boolean isEnabled = BrightnessController.instance().isEnabled();
//                BrightnessController.instance().setEnable(!isEnabled);
//                break;
//            case R.id.menu_scrcpy_dedicated:
//                ScrcpyDedicated.instance.onSwitch();
//                break;
            case R.id.menu_reboot:
                Libsu.rebootWithConfirmation(this);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.basic_info:
                BasicInfo.show(this);
                break;
            case R.id.main_serial:
                Dialogs.showEditTextDialog(this, "编号", DeviceNumber.get(), s -> {
                    DeviceNumber.set(s);
                    refreshSerial();
                    DeviceNumber.setAsSerial();
                });
                break;
            case R.id.main_btn_grpc:
                Logger.d(TAG, "onClick grpc btn");
                mGrpcAddressEditor.clearFocus();
                InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
                String nodeAddress = mGrpcAddressEditor.getText().toString();
                if (ActStringUtils.checkProxyPattern(nodeAddress)) {
                    Logger.d(TAG, "Set node address: " + nodeAddress);
                    GRPCManager.getInstance().setUpActorChannel(nodeAddress);
                    GRPCManager.getInstance().getActorChannel().addNodeAddressToSp(nodeAddress);
                    Toast.makeText(this, "Set node addr success.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Invalid address type.", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.main_btn_wifi:
                Logger.d(TAG, "onClick wifi settings btn");
                //showChoiceWifiDialog();

                try {
                    //FIRequest.get("com.henrikherzig.playintegritychecker").
                    int port = FIEntryPoint.getPort("com.henrikherzig.playintegritychecker");
                    Logger.d(TAG, "port " + port);
                } catch (Throwable e) {
                    Logger.e(TAG, "ee", e);
                }
                break;
            default:
                break;
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onResume() {
        super.onResume();
        Logger.i(TAG, "onResume ================================");
        Intent asIntent = new Intent(this, ActAccessibility.class);
        ComponentName cn = startService(asIntent);
        if (cn == null) {
            Logger.e(TAG, "not start " + asIntent + ", sdk " + Build.VERSION.SDK_INT);
        }
        //mRootCheckbox.setChecked(Root.acquireRoot());
      //  mGrpcCheckbox.setChecked(GRPCManager.getInstance().getActorChannel().isLastRequestSucceed());
//        mAccessibilityCheckbox.setChecked(ActAccessibility.isStart());
//        if (ActAccessibility.isStart()) {
//            if (ActAccessibility.getInstance() != null) {
//                mBatteryText.setText(ActAccessibility.getInstance().mBatteryReceiver.getBatteryMsg());
//            }
//        }
        setTitle(getString(R.string.app_name) + "_v" + BuildConfig.VERSION_NAME);
        refreshSerial();
        setWifiView();

        GlobalNotification.addObserver(GlobalNotification.NOTIFY_GRPC_SET_ADDRESS, this);
    }

    private void refreshSerial() {
        String serial = "";
        try {
            serial = Build.getSerial();
        } catch (SecurityException e) {
        }
        mSerialText.setText(DeviceNumber.get() + "/" + serial);
    }

    private void refreshGrpcAddress() {
        Logger.d(TAG, "refresh EditText GrpcAddress: " + ActorAdapter.DEFAULT_RETRY_ADDRESS);
        mGrpcAddressEditor.setText(ActorAdapter.DEFAULT_RETRY_ADDRESS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        GlobalNotification.removeObserver(GlobalNotification.NOTIFY_GRPC_SET_ADDRESS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Logger.i(TAG, "onDestroy ================================");
        unregisterReceiver(mServiceReadyReceiver);
        for (int code : NOTIFICATION_MAP.keySet()) {
            GlobalNotification.removeObserver(code);
        }
        mHandler.removeCallbacksAndMessages(null);
        mHandler = null;
    }

    private final BroadcastReceiver mServiceReadyReceiver = new BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onReceive(Context context, Intent intent) {
            Logger.d(TAG, "MainActivity onReceived  " + intent.getAction());
            switch (intent.getAction()) {
                case RECEIVER_SERVICE_READY:
                    Logger.d(TAG, "Accessibility enable.");
                    //mAccessibilityCheckbox.setChecked(ActAccessibility.isStart());
                    break;
            }
        }
    };

    @Override
    public void update(ActObservable obs, Object arg) {
        Logger.d(TAG, "Receive notification: " + NOTIFICATION_MAP.get(obs.getCode()) + " " + arg);
        if (mHandler != null) {
            Message msg = new Message();
            msg.what = obs.getCode();
            msg.obj = arg;
            mHandler.sendMessage(msg);
        }
    }

    @Override
    public boolean onLongClick(View view) {
        //if (view.getId() == R.id.main_battery_info) {
//            if (ActAccessibility.isStart()) {
//                assert ActAccessibility.getInstance() != null;
//                ActAccessibility.getInstance().switchDarkMask();
//            }
        //}
        return false;
    }

    @SuppressLint("HandlerLeak")
    private class UIHandler extends Handler {
        @Override
        public void handleMessage(android.os.Message msg) {
            Logger.d(TAG, "Receive notification: " + NOTIFICATION_MAP.get(msg.what) + " " + msg.obj + " " + msg.what);
            switch (msg.what) {
                case GlobalNotification.NOTIFY_GRPC_CONNECT:
                    mGrpcCheckbox.setChecked((Boolean) msg.obj);
                    break;
                case GlobalNotification.NOTIFY_GRPC_SET_ADDRESS:
                    refreshGrpcAddress();
                    break;
                case GlobalNotification.NOTIFY_TASK_STATUS:
                    mPagerTask.updateTaskStatus();
                    break;
                case GlobalNotification.NOTIFY_WIFI_CHANGE:
                    setWifiView();
                    break;
                case GlobalNotification.NOTIFY_GRPC_MSG:
                    mPagerGrpc.inputMsg(msg.obj.toString());
                    break;
//                case GlobalNotification.NOTIFY_BATTERY_CHANGE:
//                    mBatteryText.setText(msg.obj.toString());
//                    break;
                default:
                    break;
            }
        }

    }


    private void showChoiceWifiDialog() {
        List<String> list = DeviceInfoManager.getInstance().getConnManager().getWifi().getScanList();
        CharSequence[] charSequences = new CharSequence[list.size()];
        for (int i = 0; i < list.size(); i++) {
            charSequences[i] = list.get(i);
        }
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Choose an wifi")
                .setSingleChoiceItems(charSequences, 0, (dialogInterface, i) -> {
                    mChosenWifi = list.get(i);
                    Logger.d(TAG, "Dialog wifi choose " + mChosenWifi + " onClick.");
                })
                .setPositiveButton("confirm", (dialogInterface, i) -> {
                    if (mChosenWifi == null) {
                        mChosenWifi = list.get(0);
                    }
                    Logger.i(TAG, "Dialog wifi choose " + mChosenWifi + " confirm");
                    showSetWifiPasswordDialog();
                })
                .setNegativeButton("cancel", (dialogInterface, i) -> {
                    Logger.i(TAG, "Dialog wifi choose " + mChosenWifi + " cancel");
                    mChosenWifi = null;
                })
                .show();
    }

    private void showSetWifiPasswordDialog() {
        EditText userEdit = new EditText(this);
        EditText passEdit = new EditText(this);
        passEdit.setHint("1QAZ2wsx3edc...");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(userEdit);
        layout.addView(passEdit);
        new AlertDialog.Builder(this)
                .setMessage("Input username/password")
                .setView(layout)
                .setPositiveButton("confirm", (dialogInterface, i) -> {
                    String username = userEdit.getText().toString();
                    String password = passEdit.getText().toString();
                    if (password.isEmpty()) {
                        password = "1QAZ2wsx3edc...";
                    }
                    Logger.d(TAG, "Dialog set wifi: " + mChosenWifi + ", password: " + password + " confirm.");
                    DeviceInfoManager.getInstance().getConnManager().connect(
                            new ActWifiManager.UserInfo(mChosenWifi, username, password));
                })
                .setNegativeButton("cancel", null)
                .setOnDismissListener(dialogInterface -> {
                    Logger.d(TAG, "Dialog set wifi: " + mChosenWifi + " dismiss.");
                    mChosenWifi = null;
                })
                .show();
    }

    @SuppressLint("SetTextI18n")
    private void setWifiView() {
        ActConnManager connManager = DeviceInfoManager.getInstance().getConnManager();
        if (connManager.getType() != USE_WIFI || ActWifiManager.sIsConfigWifi) {
            mWifiCheckbox.setText(connManager.getTypeString());
        } else {
            mWifiCheckbox.setText(connManager.getTypeString() + " *");
        }
        if (connManager.isConnected()) {
            mWifiCheckbox.setChecked(true);
            mWifiText.setText(connManager.getConnectionInfo());
        } else {
            mWifiCheckbox.setChecked(false);
            mWifiText.setText("no connect");
        }
    }

    private NodeAddsListAdapter mAdapter;

    @SuppressLint("NonConstantResourceId")
    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        Logger.d(TAG, "onFocusChange " + v + ", " + hasFocus);
        switch (v.getId()) {
            case R.id.main_edit_grpc:
                if (hasFocus) {
                    mAdapter = new NodeAddsListAdapter(this);
                    ListPopupWindow listPopup = new ListPopupWindow(this);
                    listPopup.setAdapter(mAdapter);
                    listPopup.setWidth(800);
                    listPopup.setAnchorView(mGrpcAddressEditor);
                    listPopup.setModal(true);
                    listPopup.setOnItemClickListener((parent, view, position, id) -> {
                        if (position < mAdapter.getCount() - 1) {
                            listPopup.dismiss();
                            String addr = NodeAddsListAdapter.addrs.get(position);
                            mGrpcAddressEditor.setText(addr);
                            GRPCManager.getInstance().getActorChannel().addNodeAddressToSp(addr);

                            mGrpcBtn.performClick();
                            refreshGrpcAddress();
                        } else {
                            // 在 mData 中添加新项
                            Logger.d(TAG, "click to add new node address");
                            Dialogs.showEditTextDialog(this, "Add new node address", "", nodeAddress -> {
                                Logger.d(TAG, "add new node address: " + nodeAddress);
                                if (ActStringUtils.checkProxyPattern(nodeAddress)) {
                                    GRPCManager.getInstance().getActorChannel().addNodeAddressToSp(nodeAddress);
                                    Toast.makeText(this, "Add new node address success", Toast.LENGTH_SHORT).show();
                                    mGrpcAddressEditor.clearFocus();
                                    mAdapter.notifyDataSetChanged();
                                    listPopup.dismiss();
                                }
                                else {
                                    Toast.makeText(this, "Invalid node address", Toast.LENGTH_SHORT).show();
                                }
                            });
                            mAdapter.notifyDataSetChanged();
                        }
                    });
                    listPopup.show();
                }
                break;
        }
    }

}
