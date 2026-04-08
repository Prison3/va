package com.android.actor.utils.recycler;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.actor.R;
import com.android.actor.monitor.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RecyclerTextAdapter extends RecyclerView.Adapter<RecyclerTextAdapter.ViewHolder> {
    private final static String TAG = RecyclerTextAdapter.class.getSimpleName();
    private List<String> mList = new ArrayList<>();
    private final Context mContext;
    private final TextConfig mConfig;

    public RecyclerTextAdapter(Context context, String msg, TextConfig config) {
        mContext = context;
        mConfig = config;
        if (msg != null) {
            mList = Arrays.asList(msg.split("\n"));
        }
    }

    public RecyclerTextAdapter(Context context, List<String> list, TextConfig config) {
        mContext = context;
        mConfig = config;
        mList = list;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        mConfig.config(holder.text, mList.get(position), position);
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    public void add(String msg) {
        mList.add(msg);
        notifyItemInserted(mList.size() - 1);
        Logger.d(TAG, "add " + (mList.size() - 1) + ":" + msg);
    }

    public void remove() {
        int pos = mList.size() - 1;
        String msg = mList.remove(pos);
        notifyItemRemoved(pos);
        Logger.d(TAG, "remove " + pos + ": " + msg);
    }

    public void resetList(List<String> list) {
        mList = new ArrayList<>(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(mContext).inflate(R.layout.item_text, parent, false));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        public final TextView text;

        public ViewHolder(View v) {
            super(v);
            text = v.findViewById(R.id.item_recycler_text);
        }
    }

    public interface TextConfig {
        void config(TextView textView, String msg, int position);
    }
}
