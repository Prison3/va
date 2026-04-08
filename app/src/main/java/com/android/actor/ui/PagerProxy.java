package com.android.actor.ui;

import static com.android.actor.utils.notification.GlobalNotification.NOTIFY_PROXY_CHANGE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.actor.ActApp;
import com.android.actor.R;
import com.android.actor.control.ActPackageManager;
import com.android.actor.device.ProfilePackage;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.notification.ActObservable;
import com.android.actor.utils.notification.ActObserver;
import com.android.actor.utils.notification.GlobalNotification;
import com.android.actor.utils.proxy.ProxyManager;
import com.android.actor.utils.proxy.ProxyManager.Proxy;

import java.util.*;

public class PagerProxy extends BasePager implements ActObserver {
    private final static String TAG = PagerProxy.class.getSimpleName();
    private RecyclerView mRecycler;
    private TextView mHintText;
    private List<ProfilePackage> mProxyPkgList = new ArrayList<>();

    public PagerProxy(Context context) {
        super(context);
    }

    @Override
    public View bindPagerView() {
        View root = LayoutInflater.from(mContext).inflate(R.layout.pager_proxy, null);
        mRecycler = root.findViewById(R.id.proxy_recycler);
        mHintText = root.findViewById(R.id.proxy_hint_text);
        root.findViewById(R.id.proxy_btn_add).setOnClickListener(v -> {
            showChoiceAppDialog();
        });
        mRecycler.setLayoutManager(new LinearLayoutManager(mContext));
        mRecycler.setAdapter(mAdapter);
        mRecycler.addItemDecoration(new DividerItemDecoration(mContext, DividerItemDecoration.VERTICAL));
        GlobalNotification.addObserver(NOTIFY_PROXY_CHANGE, this);
        refresh();
        return root;
    }

    static class ProxyItemView extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView iptable;
        TextView socks;
        TextView dns;
        CheckBox check;

        public ProxyItemView(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.item_icon);
            iptable = itemView.findViewById(R.id.item_proxy_iptable);
            socks = itemView.findViewById(R.id.item_proxy_socks);
            dns = itemView.findViewById(R.id.item_dns);
            check = itemView.findViewById(R.id.item_test_btn);
        }
    }

    private RecyclerView.Adapter<ProxyItemView> mAdapter = new RecyclerView.Adapter<>() {
        @NonNull
        @Override
        public ProxyItemView onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ProxyItemView(LayoutInflater.from(mContext).inflate(R.layout.item_proxy, parent, false));
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull ProxyItemView holder, int position) {
            ProfilePackage pkg = mProxyPkgList.get(position);
            Proxy proxy = ProxyManager.getInstance().getProxy(pkg.packageName, pkg.profileId);
            holder.itemView.setTag(pkg);
            holder.icon.setImageDrawable(ActPackageManager.getInstance().loadIcon(pkg.packageName));
            if (proxy != null) {
                holder.iptable.setTextColor(Color.BLACK);
                holder.iptable.setText(pkg.packageName + ", uid: " + proxy.iptables.getTargetUid() + ", port: " + proxy.iptables.getRedirectPort());
                holder.socks.setTextColor(Color.BLACK);
                holder.socks.setText(proxy.socks.getShowMsg());
                holder.dns.setTextColor(Color.BLACK);
                holder.dns.setText(proxy.dns.getShowMsg());
            } else {
                holder.iptable.setTextColor(Color.RED);
                holder.iptable.setText("not found iptable for pkg: " + pkg);
                Logger.w(TAG, "cannot find iptables for: " + pkg);
                holder.socks.setTextColor(Color.RED);
                holder.socks.setText("not found v2ray for pkg: " + pkg);
                Logger.w(TAG, "cannot find v2ray for " + pkg);
            }

            holder.itemView.setOnLongClickListener(v -> {
                ProfilePackage _pkg = (ProfilePackage) v.getTag();
                Dialogs.showProxyEditTextDialog(mContext, proxy.socks, _pkg);
                return true;
            });

            holder.itemView.setOnClickListener(v -> {
                if (proxy != null) {
                    new Thread(() -> {
                        String socksProxy = proxy.socksProxy;
                        Logger.d(TAG, "start test proxy: " + socksProxy);
                        Boolean isOk = ProxyManager.testProxy(proxy);
                        ActApp.getInstance().getMainHandler().post(() -> {
                            holder.check.setChecked(true);
                            if (isOk) {
                                ActApp.getInstance().getMainHandler().post(() -> {
                                    holder.check.setEnabled(true);
                                });
                                Toast.makeText(mContext, "代理可用 " + socksProxy, Toast.LENGTH_SHORT).show();
                            } else {
                                ActApp.getInstance().getMainHandler().post(() -> {
                                    holder.check.setEnabled(false);
                                });
                                Toast.makeText(mContext, "代理不可用 " + socksProxy, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }).start();
                }
            });
        }

        @Override
        public int getItemCount() {
            return mProxyPkgList.size();
        }
    };

    public void refresh() {
        mProxyPkgList.clear();
        mProxyPkgList.addAll(ProxyManager.getInstance().getPackages());
        mProxyPkgList.sort(Comparator.comparing((ProfilePackage p) -> p.packageName)
                .thenComparingInt(p -> p.profileId));
        mAdapter.notifyDataSetChanged();
        Logger.d(TAG, "init proxy list view: " + Arrays.toString(mProxyPkgList.toArray()));
    }

    @Override
    public void update(ActObservable obs, Object arg) {
        ActApp.getMainHandler().post(() -> {
            refresh();
        });
    }

    private void showChoiceAppDialog() {
        Dialogs.showSingleChoiceAppListForProxy(mContext, packageName -> {
            showSetProxyDialog(packageName, 0);
        });
    }

    @SuppressLint("SetTextI18n")
    private void showSetProxyDialog(final String chosenPkgName, int profileId) {
        Dialogs.showEditTextDialog(mContext, "Input proxy ip:port", "", proxy -> {
            Logger.i(TAG, "Dialog set " + chosenPkgName + '-' + proxy + " proxy " + proxy + " confirm.");
            if (ActStringUtils.checkProxyPattern(proxy)) {
                Pair<Boolean, String> result = ProxyManager.getInstance().addProxy(ProfilePackage.create(chosenPkgName, profileId), proxy);
                if (result.first) {
                    Logger.i(TAG, "Add proxy ok.");
                } else {
                    String msg = "Add proxy error, " + result.second;
                    Logger.e(TAG, msg);
                    Toast.makeText(mContext, msg, Toast.LENGTH_LONG).show();
                }
            } else {
                Logger.w(TAG, "Dialog set " + chosenPkgName + '-' + profileId + " proxy " + proxy + " invalid pattern.");
                Toast.makeText(mContext, "Invalid proxy pattern.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public String getTitle() {
        return "proxy";
    }

    @Override
    public void unbindPagerView() {
        GlobalNotification.removeObserver(NOTIFY_PROXY_CHANGE);
    }
}
