package com.android.actor.utils.top;

import com.android.actor.monitor.Logger;
import com.android.actor.utils.ActStringUtils;
import com.android.actor.utils.shell.Root;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Top {
    private final static String TAG = Top.class.getSimpleName();
    private final static int DEFAULT_LINE_LENGTH = 512;
    // 设置行宽，或者用 "stty cols " + DEFAULT_LINE_LENGTH + " && top -n 1 \n"
    private final List<String> mLines = new ArrayList<>();
    private FiledOrder mSort;

    public enum FiledOrder {
        PID, USER, PR, NI, VIRT, RES, SHR, S, CPU, MEM, TIME, CMDLINE
    }

    private TopLineTasks mTask;
    private TopLineMem mMem;
    private TopLineSwap mSwap;
    private TopLineCpu mCpu;
    private TopLineTitle mTitle;
    private List<TopLineItem> mItems = new ArrayList<>();

    public Top() throws Exception {
        this(FiledOrder.CPU);
    }

    public Top(FiledOrder filed) throws Exception {
        if (Root.checkRoot()) {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream dos = new DataOutputStream(p.getOutputStream());
            DataInputStream dis = new DataInputStream(p.getInputStream());
            mSort = filed;
            int sort = filed.ordinal() + 1;
            dos.writeBytes("COLUMNS=" + DEFAULT_LINE_LENGTH + " top -n 1 -s " + sort + "\n");
            dos.writeBytes("exit\n");
            dos.flush();
            String line = null;
            while ((line = dis.readLine()) != null) {
                mLines.add(line);
            }
            dis.close();
            dos.close();
            p.waitFor();
            parse();
        }
    }


    // 只适配了pixel
    private void parse() {
        Logger.d(TAG, "=========================Top start=========================");
        int offset = 0;
        if (mLines.size() < 6) {
            Logger.e(TAG, "Unsupported format: " + ActStringUtils.listToString(mLines));
            return;
        }
        if (mLines.get(offset).length() < 32) {
            offset += 1;
        }
//        Logger.d(TAG, mLines.get(offset));
        mTask = new TopLineTasks(mLines.get(offset));
        offset += 1;
//        Logger.d(TAG, mTask);

        if (mLines.get(offset).length() < 32) {
            offset += 1;
        }
//        Logger.d(TAG, mLines.get(offset));
        mMem = new TopLineMem(mLines.get(offset));
        offset += 1;
        Logger.d(TAG, mMem);

        if (mLines.get(offset).length() < 32) {
            offset += 1;
        }
//        Logger.d(TAG, mLines.get(offset));
        mSwap = new TopLineSwap(mLines.get(offset));
        offset += 1;
//        Logger.d(TAG, mSwap);

        if (mLines.get(offset).length() < 32) {
            offset += 1;
        }
//        Logger.d(TAG, mLines.get(offset));
        mCpu = new TopLineCpu(mLines.get(offset));
        offset += 1;
        Logger.d(TAG, mCpu);


        if (mLines.get(offset).length() < 32) {
            offset += 1;
        }
//        Logger.d(TAG, mLines.get(offset));
        mTitle = new TopLineTitle(mLines.get(offset));
        offset += 1;
//        Logger.d(TAG, mTitle);

        for (int i = 0; i < 10; i++) {
            if (mLines.get(offset + i).length() < 32) {
                offset += 1;
            }
//            Logger.d(TAG, mLines.get(offset + i));
            TopLineItem item = new TopLineItem(mLines.get(offset + i), mTitle.order);
            mItems.add(item);
//            Logger.d(TAG, item);
        }
        Logger.d(TAG, "=========================Top end=========================");
    }

    // api
    public String getSort() {
        return mSort.name();
    }

    public int getCpuUsage() {
        if (mCpu != null) {
            return (mCpu.sys + mCpu.user) * 100 / mCpu.cpu;
        }
        return 0;
    }

    public int getMemoryFree() {
        try {
            if (mMem != null) {
                return Integer.parseInt(mMem.free.substring(0, mMem.free.length() - 1));
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    public List<TopLineItem> getTopArray() {
        return mItems;
    }

    public HashMap<String, Integer> getTop10CpuMap() {
        HashMap<String, Integer> map = new HashMap<>();
        for (TopLineItem item : mItems) {
            map.put(item.getArgs(), (int) item.getCpuPercentage());
        }
        return map;
    }

    // 占cpu前10的内存占用率
    public HashMap<String, Integer> getTop10MemMap() {
        HashMap<String, Integer> map = new HashMap<>();
        for (TopLineItem item : mItems) {
            map.put(item.getArgs(), (int) item.getMemoryPercentage());
        }
        return map;
    }
}
