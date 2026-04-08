package com.android.actor.utils.db;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.TrafficStats;
import android.provider.BaseColumns;

import com.android.actor.ActApp;
import com.android.actor.control.ActPackageManager;
import com.android.actor.control.RocketComponent;
import com.android.actor.monitor.Logger;

import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class TrafficContract {
    private final static String TAG = TrafficContract.class.getSimpleName();
    public static final String PKG_TOTAL = "total";

    public static class TrafficEntry implements BaseColumns {
        public static final String TABLE_NAME = "traffic_record";
        public static final String PKG_NAME = "pkg_name";
        public static final String RSV_BYTES = "rsv_bytes";
        public static final String SND_BYTES = "snd_bytes";
        public static final String SHOT_TIME = "snapshot_time";
    }

    public static String getCreateSql() {
        return "CREATE TABLE " + TrafficEntry.TABLE_NAME + " (" +
                TrafficEntry._ID + " INTEGER PRIMARY KEY, " +
                TrafficEntry.PKG_NAME + " CHAR(64) NOT NULL, " +
                TrafficEntry.RSV_BYTES + " INTEGER NOT NULL, " +
                TrafficEntry.SND_BYTES + " INTEGER NOT NULL, " +
                TrafficEntry.SHOT_TIME + " DATETIME DEFAULT (datetime('now', 'localtime')));";
    }

    public static boolean record(String pkgName, long rsvBytes, long sndBytes) {
        SQLiteDatabase db = DBHelper.getInstance().getDatabase();
        if (db == null || !db.isOpen()) {
            return false;
        }
        boolean ok = true;
        try {
            ContentValues values = new ContentValues();
            values.put(TrafficEntry.PKG_NAME, pkgName);
            values.put(TrafficEntry.RSV_BYTES, rsvBytes);
            values.put(TrafficEntry.SND_BYTES, sndBytes);
            db.insert(TrafficEntry.TABLE_NAME, null, values);
        } catch (SQLException e) {
            Logger.e(TAG, e.toString(), e);
            ok = false;
        }
        return ok;
    }

    /**
     * record apps traffic
     */
    public static void recordAppTraffic() {
        for (PackageInfo pkgInfo : ActPackageManager.getInstance().getInstalledPackage()) {
            try {
                // ignore rocket component app except actor。
                if (!RocketComponent.isRocketComponent(pkgInfo.packageName)
                        || pkgInfo.packageName.equals(ActApp.getInstance().getPackageName())) {
                    int uid = ActPackageManager.getInstance().getUidOfPackage(pkgInfo.packageName);
                    long srv = TrafficStats.getUidRxBytes(uid);
                    long snd = TrafficStats.getUidTxBytes(uid);
                    Logger.d(TAG, pkgInfo.packageName + " srv " + srv + ", snd " + snd);
                    TrafficContract.record(pkgInfo.packageName, srv, snd);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Logger.e(TAG, e.toString(), e);
            }
        }
        TrafficContract.record(PKG_TOTAL, TrafficStats.getTotalRxBytes(), TrafficStats.getTotalTxBytes());
    }

//    private void testRcvTraffic(int uid) {
//        try {
//            String sndPath = "/proc/uid_stat/" + uid + "/tcp_snd";
//            String srvPath = "/proc/uid_stat/" + uid + "/tcp_rcv";
//            Logger.i(TAG, "getTotalRxBytes: " + TrafficStats.getTotalRxBytes());
//            Logger.i(TAG, "getTotalRxPackets: " + TrafficStats.getTotalRxPackets());
//            Logger.i(TAG, "getTotalTxBytes: " + TrafficStats.getTotalTxBytes());
//            Logger.i(TAG, "getTotalTxPackets: " + TrafficStats.getTotalTxPackets());
//
//
//            Logger.i(TAG, "getUidRxBytes: " + TrafficStats.getUidRxBytes(uid));
//            Logger.i(TAG, "getUidRxPackets: " + TrafficStats.getUidRxPackets(uid));
//            Logger.i(TAG, "getUidTxBytes: " + TrafficStats.getUidTxBytes(uid));
//            Logger.i(TAG, "getUidTxPackets: " + TrafficStats.getUidTxPackets(uid));
//
//            Logger.d(TAG, "tcp_sed: " + Shell.execRootCmd("cat " + sndPath).get(0));
//            Logger.d(TAG, "tcp_srv: " + Shell.execRootCmd("cat " + srvPath).get(0));
//        } catch (Exception e) {
//            Logger.e(TAG, e.toString(), e);
//        }
//    }

    /**
     * query all app traffic date with Date [start, end)
     * should not call the method in main thread, because this operation may waste a long time.
     *
     * @return a HashMap with pkgName-{rsvBytes, sndBytes}, the Date-start bytes count is 0;
     */
    public static HashMap<String, TUnit> queryAllTraffic(Date start, Date end) throws Exception {
        SQLiteDatabase db = DBHelper.getInstance().getDatabase();
        if (db == null || !db.isOpen()) {
            return null;
        }
        HashMap<String, TUnit> map = new HashMap<>();
        String sql = String.format(
                "SELECT distinct(%s) from %s where %s >= '%s' and %s < '%s'",
                TrafficEntry.PKG_NAME, TrafficEntry.TABLE_NAME,
                TrafficEntry.SHOT_TIME, start,
                TrafficEntry.SHOT_TIME, end);
        Logger.i(TAG, "execute sql: " + sql);
        Cursor cursor = db.rawQuery(sql,new String[]{});
        while (cursor.moveToNext()) {
            @SuppressLint("Range") String pkgName = cursor.getString(cursor.getColumnIndex(TrafficEntry.PKG_NAME));
            map.put(pkgName, queryPackageTraffic(pkgName, start, end));
        }
        cursor.close();
        return map;
    }

    /**
     * query all app traffic date serial with Date [start, end)
     * should not call the method in main thread, because this operation may waste a long time.
     *
     * @return a HashMap with pkgName-List[{rsvBytes, sndBytes}], the Date-start bytes count is 0;
     */
    public static HashMap<String, List<TUnit>> queryAllTrafficSerial(Date start, Date end) throws Exception {
        SQLiteDatabase db = DBHelper.getInstance().getDatabase();
        if (db == null || !db.isOpen()) {
            return null;
        }
        HashMap<String, List<TUnit>> map = new HashMap<>();
        Cursor cursor = db.rawQuery(String.format(
                "SELECT distinct(%s) from %s where %s >= '%s' and %s < '%s'",
                TrafficEntry.PKG_NAME, TrafficEntry.TABLE_NAME,
                TrafficEntry.SHOT_TIME, start,
                TrafficEntry.SHOT_TIME, end),
                new String[]{});
        while (cursor.moveToNext()) {
            @SuppressLint("Range") String pkgName = cursor.getString(cursor.getColumnIndex(TrafficEntry.PKG_NAME));
            map.put(pkgName, queryPackageTrafficSerial(pkgName, start, end));
        }
        cursor.close();
        return map;
    }

    /**
     * query an app traffic date with Date [start, end)
     * should not call the method in main thread, because this operation may waste a long time.
     *
     * @return a Pair {rsvBytes, sndBytes}, the Date-start bytes count is 0; return null if query out nothing.
     */
    public static TUnit queryPackageTraffic(String pkgName, Date start, Date end) throws Exception {
        SQLiteDatabase db = DBHelper.getInstance().getDatabase();
        if (db == null || !db.isOpen()) {
            return null;
        }
        TUnit last = null, firstOffset = null, breakOffset = new TUnit(0L, 0L, 0);
        String sql = String.format(
                "SELECT %s, %s, %s ts from %s where %s = '%s' and %s >= '%s' and %s < '%s' order by %s",
                TrafficEntry.RSV_BYTES, TrafficEntry.SND_BYTES,
                "strftime('%s'," + TrafficEntry.SHOT_TIME + ", 'localtime')",
                TrafficEntry.TABLE_NAME,
                TrafficEntry.PKG_NAME, pkgName,
                TrafficEntry.SHOT_TIME, start,
                TrafficEntry.SHOT_TIME, end,
                TrafficEntry.SHOT_TIME);
        Logger.i(TAG, "execute sql: " + sql);
        Cursor cursor = db.rawQuery(sql, new String[]{});
        while (cursor.moveToNext()) {
            @SuppressLint("Range") long rsv = cursor.getLong(cursor.getColumnIndex(TrafficEntry.RSV_BYTES));
            @SuppressLint("Range") long snd = cursor.getLong(cursor.getColumnIndex(TrafficEntry.SND_BYTES));
            @SuppressLint("Range") int time = cursor.getInt(cursor.getColumnIndex("ts"));
            if (firstOffset != null) {
                if (rsv < last.rsv || snd < last.snd) { // meet break point, device may be reboot. add the last as break offset
                    breakOffset.rsv += last.rsv;
                    breakOffset.snd += last.snd;
                }
            } else {
                firstOffset = new TUnit(rsv, snd, time);
            }
            last = new TUnit(rsv, snd, time);
        }
        if (firstOffset != null) {
            last.rsv += breakOffset.rsv - firstOffset.rsv;
            last.snd += breakOffset.snd - firstOffset.snd;
        }
        // cut down the offset
        cursor.close();
        return last;
    }

    /**
     * query a serial traffic data of a app with Date [start, end)
     * should not call the method in main thread, because this operation may waste a long time.
     *
     * @return a ArrayList of traffic data which the Date-start bytes count is 0; return a 0 len list if query out nothing.
     */
    public static List<TUnit> queryPackageTrafficSerial(String pkgName, Date start, Date end) throws Exception {
        SQLiteDatabase db = DBHelper.getInstance().getDatabase();
        if (db == null || !db.isOpen()) {
            return null;
        }
        List<TUnit> list = new ArrayList<>();
        String sql = String.format(
                "SELECT %s, %s, %s ts from %s where %s = '%s' and %s >= '%s' and %s < '%s' order by %s",
                TrafficEntry.RSV_BYTES, TrafficEntry.SND_BYTES,
                "strftime('%s'," + TrafficEntry.SHOT_TIME + ", 'utc')",
                TrafficEntry.TABLE_NAME,
                TrafficEntry.PKG_NAME, pkgName,
                TrafficEntry.SHOT_TIME, start,
                TrafficEntry.SHOT_TIME, end,
                TrafficEntry.SHOT_TIME);
        Logger.i(TAG, "execute sql: " + sql);
        Cursor cursor = db.rawQuery(sql, new String[]{});
        TUnit last = null, offsetFirst = null;
        TUnit breakOffset = new TUnit(0L, 0L, 0);
        while (cursor.moveToNext()) {
            @SuppressLint("Range") long rsv = cursor.getLong(cursor.getColumnIndex(TrafficEntry.RSV_BYTES));
            @SuppressLint("Range") long snd = cursor.getLong(cursor.getColumnIndex(TrafficEntry.SND_BYTES));
            @SuppressLint("Range") int time = cursor.getInt(cursor.getColumnIndex("ts"));
            if (offsetFirst != null) {
                if (rsv < last.rsv || snd < last.snd) { // meet break point, device may be reboot. add the last as break offset
                    breakOffset.rsv += last.rsv;
                    breakOffset.snd += last.snd;
                }
            } else { // record first as offset
                offsetFirst = new TUnit(rsv, snd, time);
            }
            last = new TUnit(rsv, snd, time);
            list.add(new TUnit(last.rsv + breakOffset.rsv - offsetFirst.rsv, last.snd + breakOffset.snd - offsetFirst.snd, time));
        }
        cursor.close();
        return list;
    }

    @SuppressLint("DefaultLocale")
    public static String getBytesText(long b) {
        double kb = (double) (b / 1024);
        if (kb > 1) {
            double mb = kb / 1024;
            if (mb > 1) {
                double gb = mb / 1024;
                if (gb > 1) {
                    return String.format("%.2fGB", gb);
                } else {
                    return String.format("%.2fMB", mb);
                }
            } else {
                return String.format("%.2fKB", kb);
            }
        } else {
            return b + "B";
        }
    }
}
