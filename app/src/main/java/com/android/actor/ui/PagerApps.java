package com.android.actor.ui;

import com.android.va.runtime.VHost;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.actor.ActApp;
import com.android.actor.R;
import com.android.actor.monitor.Logger;
import com.android.va.runtime.VActivityManager;
import com.android.va.runtime.VEnvironment;
import com.android.va.runtime.VPackageManager;
import com.android.va.runtime.VProfileManager;
import com.android.va.model.Profile;
import com.android.actor.utils.shell.Libsu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 显示 {@link VPackageManager} 虚拟用户空间内已安装应用（与 VA Apps 列表同源）。
 */
public class PagerApps extends BasePager {

    private static final String TAG = PagerApps.class.getSimpleName();

    /** 安装虚拟 APK 成功后 {@link InstallActivity} 发出，用于刷新列表 */
    public static final String ACTION_REFRESH_VIRTUAL_APPS = "com.android.actor.REFRESH_VIRTUAL_APPS";

    private View mRootView;
    private RecyclerView mAppListRecycler;
    private final VAppAdapter mAdapter = new VAppAdapter();
    private final List<VirtualAppItem> mDataList = new ArrayList<>();
    private BroadcastReceiver mRefreshReceiver;

    public PagerApps(Context context) {
        super(context);
    }

    @Override
    public View bindPagerView() {
        mRootView = LayoutInflater.from(mContext).inflate(R.layout.pager_remote, null);
        mAppListRecycler = mRootView.findViewById(R.id.vapp_list);
        mAppListRecycler.setLayoutManager(new LinearLayoutManager(mContext));
        mAppListRecycler.setAdapter(mAdapter);


        mRefreshReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshVirtualApps();
            }
        };
        IntentFilter filter = new IntentFilter(ACTION_REFRESH_VIRTUAL_APPS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mContext.registerReceiver(mRefreshReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            mContext.registerReceiver(mRefreshReceiver, filter);
        }

        Libsu.waitReady(this::refreshVirtualApps);
        return mRootView;
    }

    @Override
    public String getTitle() {
        return "apps";
    }

    /** Logcat 过滤示例: {@code adb logcat -s PagerApps:I Prison:D}，或搜 {@code [VA-Apps]} */
    private static final String L = "[VA-Apps]";

    /**
     * 在后台线程读取虚拟用户已装应用，回到主线程刷新列表。
     */
    public void refreshVirtualApps() {
        new Thread(() -> {
            try {
                Logger.i(TAG, L + " refresh start");
                Context ctx = VHost.getContext();
                if (ctx == null) {
                    Logger.e(TAG, L + " VHost.getContext()==null → 未在 attachBaseContext 调用 VRuntime.startUp？");
                    ActApp.post(() -> Toast.makeText(mContext, "VA 未初始化", Toast.LENGTH_SHORT).show());
                    return;
                }
                Logger.i(TAG, L + " context ok, package=" + ctx.getPackageName());

                int userId = VProfileManager.get().defaultProfileId();
                Logger.i(TAG, L + " defaultProfileId=" + userId);

                try {
                    List<Profile> users = VProfileManager.get().getProfiles();
                    if (users == null) {
                        Logger.w(TAG, L + " getProfiles() == null");
                    } else {
                        Logger.i(TAG, L + " getProfiles() size=" + users.size());
                        for (int i = 0; i < users.size(); i++) {
                            Profile u = users.get(i);
                            Logger.i(TAG, L + "   user[" + i + "] id=" + u.id + " name=" + u.name);
                        }
                    }
                } catch (Throwable t) {
                    Logger.e(TAG, L + " getProfiles() failed", t);
                }

                List<ApplicationInfo> installed = VPackageManager.get().getInstalledApplications(0, userId);
                if (installed == null) {
                    installed = Collections.emptyList();
                }
                Logger.i(TAG, L + " getInstalledApplications(0, " + userId + ") raw size=" + installed.size());

                if (installed.isEmpty()) {
                    List<PackageInfo> pkgs = VPackageManager.get().getInstalledPackages(0, userId);
                    int n = pkgs == null ? -1 : pkgs.size();
                    Logger.w(TAG, L + " 应用列表为空。对比: getInstalledPackages(0, " + userId + ") count=" + n
                            + " — 若二者皆为 0，多为远程 PackageManager 未就绪或该 userId 下确实无应用");
                } else {
                    int logMax = Math.min(installed.size(), 15);
                    for (int i = 0; i < logMax; i++) {
                        ApplicationInfo ai = installed.get(i);
                        Logger.i(TAG, L + "   raw[" + i + "] " + ai.packageName + " uid=" + ai.uid);
                    }
                    if (installed.size() > logMax) {
                        Logger.i(TAG, L + "   ... (" + (installed.size() - logMax) + " more)");
                    }
                }

                String hostPkg = VHost.getPackageName();
                Logger.i(TAG, L + " hostPkg(filter)=" + hostPkg);

                PackageManager pm = ctx.getPackageManager();
                List<VirtualAppItem> rows = new ArrayList<>();
                int skippedHost = 0;
                for (ApplicationInfo ai : installed) {
                    if (hostPkg != null && hostPkg.equals(ai.packageName)) {
                        skippedHost++;
                        continue;
                    }
                    String label = ai.packageName;
                    try {
                        CharSequence cs = pm.getApplicationLabel(ai);
                        if (cs != null) {
                            label = cs.toString();
                        }
                    } catch (Throwable ignored) {
                    }
                    String versionText = "";
                    try {
                        PackageInfo pi = VPackageManager.get().getPackageInfo(ai.packageName, 0, userId);
                        if (pi != null) {
                            String vn = pi.versionName != null ? pi.versionName : "";
                            versionText = vn + " (" + pi.versionCode + ")";
                        }
                    } catch (Throwable ignored) {
                    }
                    rows.add(new VirtualAppItem(ai, userId, label, versionText));
                }
                Logger.i(TAG, L + " after host filter: rows=" + rows.size() + " skippedHost=" + skippedHost);

                rows.sort(Comparator.comparing(a -> a.label.toLowerCase(Locale.ROOT)));
                ActApp.post(() -> {
                    mDataList.clear();
                    mDataList.addAll(rows);
                    mAdapter.notifyDataSetChanged();
                });
                Logger.i(TAG, L + " refresh done UI updated");
            } catch (Throwable e) {
                Logger.e(TAG, L + " refreshVirtualApps exception", e);
                ActApp.post(() -> Toast.makeText(mContext,
                        "加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "pager-apps-vm").start();
    }

    private static final int VIEW_TYPE_APP = 0;
    private static final int VIEW_TYPE_ADD = 1;

    private class VAppAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_ADD) {
                View v = LayoutInflater.from(mContext).inflate(R.layout.item_apps_add, parent, false);
                return new AddInstallRowHolder(v);
            }
            return new AppInfoView(LayoutInflater.from(mContext).inflate(R.layout.item_apps, parent, false));
        }

        @Override
        public int getItemViewType(int position) {
            return position == mDataList.size() ? VIEW_TYPE_ADD : VIEW_TYPE_APP;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof AddInstallRowHolder) {
                holder.itemView.setOnClickListener(v -> {
                    Logger.i(TAG, "open InstallActivity from apps +");
                    mContext.startActivity(new Intent(mContext, InstallActivity.class));
                });
                return;
            }
            AppInfoView h = (AppInfoView) holder;
            VirtualAppItem item = mDataList.get(position);
            String ver = item.versionText != null ? item.versionText : "";
            h.info.setText(ver.isEmpty() ? item.packageName : item.packageName + " · " + ver);
            h.uid.setText("" + item.applicationInfo.uid);

            Drawable icon = null;
            try {
                Context ctx = VHost.getContext();
                if (ctx != null) {
                    icon = ctx.getPackageManager().getApplicationIcon(item.applicationInfo);
                }
            } catch (Throwable e) {
                Logger.w(TAG, "icon " + item.packageName, e);
            }
            h.imageView.setImageDrawable(icon);

            h.open.setOnClickListener(v -> {
                Logger.i(TAG, "launch " + item.packageName + " user=" + item.userId);
                boolean ok = VActivityManager.get().launchApk(item.packageName, item.userId);
                if (!ok) {
                    Toast.makeText(mContext, "无法启动: " + item.packageName, Toast.LENGTH_SHORT).show();
                }
            });
            h.stop.setOnClickListener(v -> {
                Logger.i(TAG, "stopPackage " + item.packageName + " user=" + item.userId);
                new Thread(() -> {
                    try {
                        VPackageManager.get().stopPackage(item.packageName, item.userId);
                    } catch (Throwable t) {
                        Logger.e(TAG, "stopPackage", t);
                    }
                }, "vm-stop").start();
            });
            h.clearCache.setOnClickListener(v -> {
                Logger.i(TAG, "clearApplicationCache " + item.packageName + " user=" + item.userId);
                new Thread(() -> {
                    try {

                        VPackageManager.get().clearPackage(item.packageName, item.userId);
                        ActApp.post(() -> {
                            Toast.makeText(mContext, "Cache cleared: " + item.packageName, Toast.LENGTH_SHORT).show();
                            refreshVirtualApps();
                        });
                    } catch (Throwable t) {
                        Logger.e(TAG, "clearApplicationCache", t);
                        ActApp.post(() -> Toast.makeText(mContext,
                                "Clear cache failed: " + t.getMessage(), Toast.LENGTH_LONG).show());
                    }
                }, "vm-clear-cache").start();
            });
            h.uninstall.setOnClickListener(v -> new AlertDialog.Builder(mContext)
                    .setTitle("Uninstall")
                    .setMessage("Remove " + item.label + " (" + item.packageName + ") from this profile?")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        Logger.i(TAG, "uninstallPackageAsUser " + item.packageName + " user=" + item.userId);
                        new Thread(() -> {
                            try {
                                VPackageManager.get().uninstallPackageAsUser(item.packageName, item.userId);
                                ActApp.post(() -> {
                                    Toast.makeText(mContext, "Uninstalled: " + item.packageName, Toast.LENGTH_SHORT).show();
                                    refreshVirtualApps();
                                });
                            } catch (Throwable t) {
                                Logger.e(TAG, "uninstallPackageAsUser", t);
                                ActApp.post(() -> Toast.makeText(mContext,
                                        "Uninstall failed: " + t.getMessage(), Toast.LENGTH_LONG).show());
                            }
                        }, "vm-uninstall").start();
                    })
                    .show());
        }

        @Override
        public int getItemCount() {
            return mDataList.size() + 1;
        }
    }

    static final class AddInstallRowHolder extends RecyclerView.ViewHolder {
        AddInstallRowHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class AppInfoView extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView info;
        TextView uid;
        Button open;
        Button stop;
        Button clearCache;
        Button uninstall;

        AppInfoView(@NonNull View item) {
            super(item);
            imageView = item.findViewById(R.id.item_apps_img);
            info = item.findViewById(R.id.item_apps_info);
            uid = item.findViewById(R.id.item_apps_uid);
            open = item.findViewById(R.id.item_apps_open);
            stop = item.findViewById(R.id.item_apps_stop);
            clearCache = item.findViewById(R.id.item_apps_clear_cache);
            uninstall = item.findViewById(R.id.item_apps_uninstall);
        }
    }

    static class VirtualAppItem {
        final ApplicationInfo applicationInfo;
        final int userId;
        final String label;
        final String packageName;
        /** 仅版本展示：versionName (versionCode)，无 PackageInfo 时为空串 */
        final String versionText;

        VirtualAppItem(ApplicationInfo ai, int userId, String label, String versionText) {
            this.applicationInfo = ai;
            this.userId = userId;
            this.label = label;
            this.packageName = ai.packageName;
            this.versionText = versionText;
        }
    }

    @Override
    public void unbindPagerView() {
        if (mRefreshReceiver != null) {
            try {
                mContext.unregisterReceiver(mRefreshReceiver);
            } catch (Throwable ignored) {
            }
            mRefreshReceiver = null;
        }
    }
}
