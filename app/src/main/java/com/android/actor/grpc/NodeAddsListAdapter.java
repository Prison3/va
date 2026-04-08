package com.android.actor.grpc;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.android.actor.MainActivity;
import com.android.actor.R;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.SPUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.xmlpull.v1.XmlPullParser;

import java.lang.reflect.Type;
import java.util.*;

public class NodeAddsListAdapter extends BaseAdapter {
    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_ADD_ITEM = 1;
    private Context mContext;
    private LayoutInflater mInflater;

    private final static String TAG = NodeAddsListAdapter.class.getSimpleName();

    public static List<String> addrs;


    public NodeAddsListAdapter(Context context) {
        String sp_grpc_node_address = SPUtils.getString(SPUtils.ModuleFile.config, ActorAdapter.SP_GRPC_NODE_ADDRESS, null);
        addrs =  JSON.parseArray(sp_grpc_node_address, String.class);
        Collections.reverse(addrs);
        mContext = context;
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        // 加上一个 "Add Item" 项
        return addrs.size() + 1;
    }

    @Override
    public Object getItem(int position) {
        if (position < addrs.size()) {
            return addrs.get(position) + " (" + position + ")";
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getViewTypeCount() {
        return 2; // 两种类型：普通项和 "Add Item" 项
    }

    @Override
    public int getItemViewType(int position) {
        return (position < addrs.size()) ? VIEW_TYPE_ITEM : VIEW_TYPE_ADD_ITEM;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View customListItemView = mInflater.inflate(R.layout.custom_list_item, null);
        if (getItemViewType(position) == VIEW_TYPE_ITEM) {
            ViewHolder viewHolder;
            if (convertView == null) {
                convertView = customListItemView;
                viewHolder = new ViewHolder();
                viewHolder.textView = convertView.findViewById(android.R.id.text1);
                viewHolder.imageViewDelete = convertView.findViewById(R.id.imageViewDelete); // 找到 imageViewDelete

                convertView.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            // 设置普通项的文本
            viewHolder.textView.setText((CharSequence) this.getItem(position));
            // 删除按钮的点击事件
            viewHolder.imageViewDelete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (Objects.equals(addrs.get(position), ActorAdapter.DEFAULT_NO_CONNECT_ADDRESS) || Objects.equals(addrs.get(position), ActorAdapter.DEFAULT_NODE_ADDRESS)) {
                        Toast.makeText(mContext, "WARN : 不能删除默认节点", Toast.LENGTH_SHORT).show();
                    } else {
                        GRPCManager.getInstance().getActorChannel().removeNodeAddressFromSp(addrs.get(position));
                        addrs.remove(position);
                        Toast.makeText(mContext, "成功删除节点" + position + ", " + addrs.get(position), Toast.LENGTH_SHORT).show();
                        notifyDataSetChanged();
                    }
                }
            });

        } else {
            // "Add Item" 项的布局和处理
            convertView = mInflater.inflate(android.R.layout.simple_list_item_1, parent, false);
            TextView textView = convertView.findViewById(android.R.id.text1);
            textView.setText("Add Item");
        }

        return convertView;
    }

    private static class ViewHolder {
        TextView textView;
        ImageView imageViewDelete;
    }
}
