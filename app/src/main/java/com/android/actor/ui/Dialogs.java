package com.android.actor.ui;

import static com.android.actor.utils.ViewUtils.dp2px;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.actor.ActApp;
import com.android.actor.R;
import com.android.actor.control.ActPackageManager;
import com.android.actor.device.ProfilePackage;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.Callback;
import com.android.actor.utils.ReflectUtils;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.proxy.ProxyBase;
import com.android.actor.utils.proxy.ProxyManager;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Dialogs {

    private static final String TAG = Dialogs.class.getSimpleName();
    private static int sSingleChoiceItemLayout;
    private static int sMultiChoiceItemLayout;

    static {
        try {
            Object alert = ReflectUtils.getObjectField(new AlertDialog.Builder(ActApp.getInstance()).create(), "mAlert");
            sSingleChoiceItemLayout = ReflectUtils.getIntField(alert, "mSingleChoiceItemLayout");
            sMultiChoiceItemLayout = ReflectUtils.getIntField(alert, "mMultiChoiceItemLayout");
        } catch (Throwable e) {
            sSingleChoiceItemLayout = android.R.layout.select_dialog_singlechoice;
            sMultiChoiceItemLayout = android.R.layout.select_dialog_multichoice;
        }
    }

    public static class Item {
        public Drawable icon;
        public String text;
        public boolean checked;
        public boolean strikeThru = false;

        public Item(Drawable icon, String text) {
            this(icon, text, false);
        }

        public Item(Drawable icon, String text, boolean checked) {
            this.icon = icon;
            this.text = text;
            this.checked = checked;
        }

        public Item withStrikeThru(boolean strikeThru) {
            this.strikeThru = strikeThru;
            return this;
        }
    }

    public static void showSingleChoiceAppList(Context context, Callback.C1<String> listener) {
        showSingleChoiceAppList(context, ActPackageManager.getInstance().getPackagesBuilder(), listener);
    }

    public static void showSingleChoiceAppListForProxy(Context context, Callback.C1<String> listener) {
        showSingleChoiceAppList(context, ActPackageManager.getInstance().getPackagesBuilder().withGMS().withGooglePlay(), listener);
    }

    public static void showSingleChoiceAppList(Context context, ActPackageManager.PackagesBuilder builder, Callback.C1<String> listener) {
        Item[] items = Arrays.stream(builder.buildNames())
                .map(name -> new Item(ActPackageManager.getInstance().loadIcon(name), name))
                .toArray(Item[]::new);
        showSingleChoiceList(context, "Choose an app", items, (dialog, which) -> {
            listener.onResult(items[which].text);
        });
    }

    public static void showSingleChoiceList(Context context, String title, String[] items, DialogInterface.OnClickListener listener) {
        showSingleChoiceList(context, title, Arrays.stream(items).map(text -> new Item(null, text)).toArray(Item[]::new), listener);
    }

    public static void showSingleChoiceList(Context context, String title, List<?> _items, DialogInterface.OnClickListener listener) {
        Type superCls = _items.getClass().getGenericSuperclass();
        Type type = ((ParameterizedType) superCls).getActualTypeArguments()[0];
        Item[] items;
        if (Item.class.getTypeName().equals(type.getTypeName())) {
            items = _items.toArray(new Item[0]);
        } else {
            items = _items.stream().map(item -> new Item(null, item.toString())).toArray(Item[]::new);
        }
        showSingleChoiceList(context, title, items, listener);
    }

    public static void showSingleChoiceList(Context context, String title, Item[] items, DialogInterface.OnClickListener listener) {
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setAdapter(new ArrayAdapter<Item>(context, sSingleChoiceItemLayout, items) {
                    @NonNull
                    @Override
                    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                        CheckedTextView view = (CheckedTextView) super.getView(position, convertView, parent);
                        Item item = getItem(position);
                        view.setText(item.text);
                        if (item.icon != null) {
                            item.icon.setBounds(0, 0, dp2px(40), dp2px(40));
                            view.setCompoundDrawables(item.icon, null, null, null);
                            view.setCompoundDrawablePadding(dp2px(10));
                        }
                        if (item.strikeThru) {
                            view.getPaint().setFlags(Paint.STRIKE_THRU_TEXT_FLAG);
                        } else {
                            view.getPaint().setFlags(0);
                        }
                        return view;
                    }
                }, (dialog, which) -> {
                    Item item = items[which];
                    if (item.strikeThru) {
                        Toast.makeText(context, "\"" + item.text + "\" is not allowed.", Toast.LENGTH_SHORT).show();
                    } else {
                        listener.onClick(dialog, which);
                    }
                })
                .show();
    }

    /*public static void showMultiChoiceAppList(Context context, int profileId, Callback.C1<List<String>> listener) {
        showMultiChoiceAppList(context, FlyPackageManager.getInstance().getPackagesBuilder(profileId), listener);
    }

    public static void showMultiChoiceAppList(Context context, PackagesBuilder builder, Callback.C1<List<String>> listener) {
        Item[] items = Arrays.stream(builder.buildNames())
                .map(name -> new Item(FlyPackageManager.getInstance().loadBadgedIcon(name, builder.profileId), name))
                .toArray(Item[]::new);
        showMultiChoiceList(context, "Choose apps", items, (whichList) -> {
            List<String> apps = new ArrayList<>();
            for (int which : whichList) {
                apps.add(items[which].text);
            }
            listener.onResult(apps);
        });
    }*/

    public static void showMultiChoiceList(Context context, String title, String[] items, Callback.C1<List<Integer>> listener) {
        showMultiChoiceList(context, title, Arrays.stream(items).map(text -> new Item(null, text)).toArray(Item[]::new), listener);
    }

    public static void showMultiChoiceList(Context context, String title, List<?> _items, Callback.C1<List<Integer>> listener) {
        Type superCls = _items.getClass().getGenericSuperclass();
        Type type = ((ParameterizedType) superCls).getActualTypeArguments()[0];
        Item[] items;
        if (Item.class.getTypeName().equals(type.getTypeName())) {
            items = _items.toArray(new Item[0]);
        } else {
            items = _items.stream().map(item -> new Item(null, item.toString())).toArray(Item[]::new);
        }
        showMultiChoiceList(context, title, items, listener);
    }

    public static void showMultiChoiceList(Context context, String title, Item[] items, Callback.C1<List<Integer>> listener) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setAdapter(new ArrayAdapter<Item>(context, sMultiChoiceItemLayout, items) {
                    @NonNull
                    @Override
                    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                        CheckedTextView view = (CheckedTextView) super.getView(position, convertView, parent);
                        Item item = getItem(position);
                        view.setText(item.text);
                        if (item.icon != null) {
                            item.icon.setBounds(0, 0, dp2px(40), dp2px(40));
                            view.setCompoundDrawables(item.icon, null, null, null);
                            view.setCompoundDrawablePadding(dp2px(10));
                        }
                        view.setBackgroundColor(item.checked ? 0xFFF7CE4F : Color.WHITE);
                        if (item.strikeThru) {
                            view.getPaint().setFlags(Paint.STRIKE_THRU_TEXT_FLAG);
                        } else {
                            view.getPaint().setFlags(0);
                        }
                        return view;
                    }
                }, null)
                .setPositiveButton("确定", (_dialog, which) -> {
                    List<Integer> whiches = new ArrayList<>();
                    for (int i = 0; i < items.length; ++i) {
                        if (items[i].checked) {
                            whiches.add(i);
                        }
                    }
                    listener.onResult(whiches);
                })
                .create();

        // don't know why listener only works here.
        dialog.getListView().setOnItemClickListener((parent, view, position, id) -> {
            boolean select = !items[position].checked;
            items[position].checked = select;
            dialog.getListView().setItemChecked(position, select);
            ((BaseAdapter) dialog.getListView().getAdapter()).notifyDataSetChanged();
        });
        dialog.show();
    }

    public static void showConfirmDialog(Context context, String text, DialogInterface.OnClickListener listener) {
        new AlertDialog.Builder(context)
                .setMessage(text)
                .setNegativeButton("no", null)
                .setPositiveButton("yes", listener).show();
    }

    public static void showTextDialog(Context context, String title, CharSequence text) {
        TextView textView = new TextView(context);
        textView.setTextColor(Color.BLACK);
        textView.setPadding(5, 5, 5, 5);
        textView.setText(text);
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(textView)
                .show();
    }

    public static void showEditTextDialog(Context context, String title, String text, Callback.C1<String> listener) {
        EditText editText = new EditText(context);
        editText.setText(text);
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(editText)
                .setPositiveButton("确定", (dialog, which) -> {
                    String str = editText.getText().toString();
                    listener.onResult(str);
                })
                .show();
    }

    /*public static void showNumberEditTextDialog(Context context, String title, String text, Callback.C1<String> listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_editor_input, null);
        builder.setView(view);
        TextView tv_title = view.findViewById(R.id.tv_title);
        tv_title.setText(title);
        TextView tv_msg = view.findViewById(R.id.tv_msg);
        tv_msg.setText(text);
        EditText edit_seq = view.findViewById(R.id.edit_seq);
        edit_seq.setHint(DeviceNumber.get());
        builder.setPositiveButton("确定", (dialog, which) -> {
            String seq_no = edit_seq.getText().toString();
            if (TextUtils.isEmpty(seq_no)) {
                seq_no = DeviceNumber.get();
            }
            listener.onResult(seq_no);
        }).show();
    }*/

    public static void showProxyEditTextDialog(Context context, ProxyBase proxyBase, ProfilePackage pkg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_proxy_editor, null);
        builder.setView(view);
        TextView tvPkg = view.findViewById(R.id.tv_pkg_name);
        EditText editProxy = view.findViewById(R.id.edit_proxy);
        tvPkg.setText(pkg.toString());
        String proxyAddr = proxyBase != null ? proxyBase.getProxy() : "";
        editProxy.setText(proxyAddr);
        builder.setNeutralButton("删除", (dialog, which)-> ProxyManager.getInstance().removeProxy(pkg));
        builder.setPositiveButton("确定", (dialog, which) -> {
            String proxy = editProxy.getText().toString().trim();
            if (ActStringUtils.checkProxyPattern(proxy)) {
                ProxyManager.getInstance().removeProxy(pkg.packageName, pkg.profileId);
                Pair<Boolean, String> result = ProxyManager.getInstance().addProxy(ProfilePackage.create(pkg.packageName, pkg.profileId), proxy);
                if (result.first) {
                    Logger.i(TAG, "Add proxy ok.");
                } else {
                    String msg = "Add proxy error, " + result.second;
                    Logger.e(TAG, msg);
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
                }
            } else {
                Logger.w(TAG, "Dialog set " + pkg.packageName + '-' + pkg.profileId + " proxy " + proxy + " invalid pattern.");
                Toast.makeText(context, "Invalid proxy pattern.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.show();
    }
}
