package com.android.actor.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.actor.control.ActPackageManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.Callback;
import com.android.va.model.InstallResult;
import com.android.va.runtime.VPackageManager;
import com.android.va.runtime.VProfileManager;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.InputStream;

/**
 * 将应用安装到虚拟用户：通过 {@link RadioGroup} 选择「APK 文件」或「已安装应用」。
 *
 * @see VPackageManager#installPackageAsUser(java.io.File, int)
 * @see VPackageManager#installPackageAsUser(String, int)
 */
public class InstallActivity extends Activity {

    private static final String TAG = InstallActivity.class.getSimpleName();
    private static final int REQ_PICK_APK = 1001;

    private RadioGroup mSourceGroup;
    /** 选中时表示从存储选择 APK（与「本机应用」互斥） */
    private RadioButton mRbStorage;

    private TextView mUserInfo;
    private TextView mApkPath;
    private TextView mOutput;
    private Button mInstallBtn;
    /** 从文件管理器 / SD 卡等复制的待安装 APK */
    private File mPendingApk;
    /** 从本机已安装列表选中的包名 */
    private String mSelectedHostPackage;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("安装虚拟应用");
        setContentView(com.android.actor.R.layout.activity_install);

        mSourceGroup = findViewById(com.android.actor.R.id.rg_source);
        mRbStorage = findViewById(com.android.actor.R.id.rb_storage);
        mUserInfo = findViewById(com.android.actor.R.id.tv_user);
        mApkPath = findViewById(com.android.actor.R.id.tv_selection);
        mOutput = findViewById(com.android.actor.R.id.tv_log);
        mInstallBtn = findViewById(com.android.actor.R.id.btn_install);

        mSourceGroup.setOnCheckedChangeListener((group, checkedId) -> {
            clearSelectionForModeSwitch();
            updatePickButtonHint();
        });

        refreshUserLabel();
        updatePickButtonHint();
    }

    /** 切换来源类型时清空另一模式的已选内容 */
    private void clearSelectionForModeSwitch() {
        mPendingApk = null;
        mSelectedHostPackage = null;
        mApkPath.setText("选择 app");
    }

    private boolean isStorageMode() {
        return mRbStorage != null && mRbStorage.isChecked();
    }

    private void updatePickButtonHint() {
        Button pick = findViewById(com.android.actor.R.id.btn_pick);
        if (pick == null) {
            return;
        }
        if (isStorageMode()) {
            pick.setText("选择 APK 文件…");
        } else {
            pick.setText("选择已安装应用…");
        }
    }

    private void refreshUserLabel() {
        try {
            int uid = VProfileManager.get().defaultProfileId();
            mUserInfo.setText("当前虚拟用户 ID: " + uid);
        } catch (Throwable t) {
            Logger.e(TAG, "refreshUserLabel", t);
            mUserInfo.setText("当前虚拟用户 ID: (不可用)");
        }
    }

    /**
     * 根据当前 {@link RadioGroup} 选中项，打开文件选择器或已安装应用列表。
     */
    public void onPickSourceClicked(View v) {
        if (isStorageMode()) {
            pickApkFromStorage();
        } else {
            Dialogs.showSingleChoiceAppList(this, new Callback.C1<String>() {
                @Override
                public void onResult(String s) {
                    mSelectedHostPackage = s;
                    mApkPath.setText("已安装应用 → " + s);
                }
            });
        }
    }

    private void pickApkFromStorage() {
        mSelectedHostPackage = null;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/vnd.android.package-archive");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, "选择 APK 文件"), REQ_PICK_APK);
        } catch (Throwable t) {
            Logger.e(TAG, "pick apk", t);
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_APK || resultCode != RESULT_OK || data == null) {
            return;
        }
        if (!isStorageMode()) {
            Toast.makeText(this, "当前为「已安装应用」模式，已忽略文件选择结果", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        try {
            File out = new File(getCacheDir(), "pending_virtual_install.apk");
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    Toast.makeText(this, "无法读取 APK", Toast.LENGTH_SHORT).show();
                    return;
                }
                FileUtils.copyInputStreamToFile(in, out);
            }
            mPendingApk = out;
            mApkPath.setText("APK 文件 → " + out.getAbsolutePath());
            Logger.i(TAG, "picked file: " + out.getAbsolutePath());
        } catch (Throwable e) {
            Logger.e(TAG, "copy apk", e);
            Toast.makeText(this, "复制 APK 失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public void onInstallClicked(View v) {
        final int userId = VProfileManager.get().defaultProfileId();
        mOutput.setText("");

        if (isStorageMode()) {
            if (mPendingApk == null || !mPendingApk.isFile()) {
                Toast.makeText(this, "请先选择 APK 文件", Toast.LENGTH_SHORT).show();
                return;
            }
            appendLine("installPackageAsUser (from APK file)");
            appendLine("file: " + mPendingApk.getAbsolutePath());
            appendLine("userId: " + userId);
            runInstall(() -> VPackageManager.get().installPackageAsUser(mPendingApk, userId));
            return;
        }

        if (mSelectedHostPackage == null || mSelectedHostPackage.isEmpty()) {
            Toast.makeText(this, "请先选择已安装应用", Toast.LENGTH_SHORT).show();
            return;
        }
        appendLine("installPackageAsUser (from installed app)");
        appendLine("packageName: " + mSelectedHostPackage);
        appendLine("userId: " + userId);
        runInstall(() -> VPackageManager.get().installPackageAsUser(mSelectedHostPackage, userId));
    }

    private void runInstall(java.util.concurrent.Callable<InstallResult> call) {
        mInstallBtn.setEnabled(false);
        new Thread(() -> {
            final InstallResult r;
            try {
                r = call.call();
            } catch (Throwable t) {
                Logger.e(TAG, "install", t);
                runOnUiThread(() -> finishInstallUi(null));
                return;
            }
            runOnUiThread(() -> finishInstallUi(r));
        }, "virtual-apk-install").start();
    }

    private void finishInstallUi(@Nullable InstallResult result) {
        if (isFinishing()) {
            return;
        }
        mInstallBtn.setEnabled(true);
        if (result == null) {
            appendLine("result: null");
            Toast.makeText(InstallActivity.this, "安装失败", Toast.LENGTH_LONG).show();
            return;
        }
        appendLine("success=" + result.success);
        appendLine("packageName=" + result.packageName);
        appendLine("msg=" + result.msg);
        if (result.success && !StringUtils.isEmpty(result.packageName)) {
            Toast.makeText(this, "已安装: " + result.packageName, Toast.LENGTH_SHORT).show();
            try {
                appendLine("host uid: "
                        + ActPackageManager.getInstance().getUidOfPackage(result.packageName));
            } catch (Throwable ignored) {
            }
            Intent refresh = new Intent(com.android.actor.ui.PagerApps.ACTION_REFRESH_VIRTUAL_APPS);
            refresh.setPackage(getPackageName());
            sendBroadcast(refresh);
        } else {
            Toast.makeText(this, "安装失败: " + result.msg, Toast.LENGTH_LONG).show();
        }
    }

    private void appendLine(String line) {
        mOutput.append(line);
        mOutput.append("\n");
    }
}
