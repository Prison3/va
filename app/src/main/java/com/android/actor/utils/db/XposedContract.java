package com.android.actor.utils.db;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import com.android.actor.monitor.Logger;
import com.android.actor.utils.TimeFormatUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class XposedContract {
    private final static String TAG = XposedContract.class.getSimpleName();

    public static class CounterEntry implements BaseColumns {
        public static final String TABLE_NAME = "xposed_record";
        public static final String REQ_PKG = "pkg_name";
        public static final String REQ_ACTION = "action_name";
        public static final String REQ_COUNT = "req_counter";
        public static final String REQ_DATE = "req_date";
        public static final String REQ_SUCCESS = "req_success";
    }

    public static String getCreateSql() {
        return "CREATE TABLE " + CounterEntry.TABLE_NAME + " (" +
                TrafficContract.TrafficEntry._ID + " INTEGER PRIMARY KEY, " +
                CounterEntry.REQ_PKG + " CHAR(64) NOT NULL, " +
                CounterEntry.REQ_ACTION + " CHAR(64) NOT NULL, " +
                CounterEntry.REQ_DATE + " DATETIME DEFAULT (date('now', 'localtime')), " +
                CounterEntry.REQ_SUCCESS + " INTEGER DEFAULT 0, " +
                CounterEntry.REQ_COUNT + " INTEGER DEFAULT 0);";
    }

    @SuppressLint("DefaultLocale")
    public static boolean record(String pkgName, String action, boolean success) {
        Cursor cursor = null;
        SQLiteDatabase db = DBHelper.getInstance().getDatabase();
        if (db == null || !db.isOpen()) {
            return false;
        }
        boolean ok = true;
        try {
            String currentDate = TimeFormatUtils.getCurrentDateStr();
            cursor = db.rawQuery(String.format("SELECT %s from %s where %s = ? and %s = ? and %s = ? and %s = ?;",
                    CounterEntry.REQ_COUNT, CounterEntry.TABLE_NAME,
                    CounterEntry.REQ_PKG, CounterEntry.REQ_ACTION, CounterEntry.REQ_SUCCESS, CounterEntry.REQ_DATE),
                    new String[]{pkgName, action, success ? "1" : "0", currentDate});
            if (cursor.moveToNext()) {
                @SuppressLint("Range") int count = cursor.getInt(cursor.getColumnIndex(CounterEntry.REQ_COUNT));
                db.execSQL(String.format("UPDATE %s set %s = %d where %s = ? and %s = ? and %s = ? and %s = ?;",
                        CounterEntry.TABLE_NAME, CounterEntry.REQ_COUNT, count + 1,
                        CounterEntry.REQ_PKG, CounterEntry.REQ_ACTION, CounterEntry.REQ_SUCCESS, CounterEntry.REQ_DATE),
                        new String[]{pkgName, action, success ? "1" : "0", currentDate});
            } else {
                ContentValues values = new ContentValues();
                values.put(CounterEntry.REQ_PKG, pkgName);
                values.put(CounterEntry.REQ_ACTION, action);
                values.put(CounterEntry.REQ_COUNT, 1);
                values.put(CounterEntry.REQ_SUCCESS, success);
                db.insert(CounterEntry.TABLE_NAME, null, values);
            }
        } catch (SQLException e) {
            Logger.e(TAG, e.toString(), e);
            ok = false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return ok;
    }

    public static List<XUnit> queryPackageCounter(String pkgName, Date start, Date end) {
        List<XUnit> list = new ArrayList<>();
        SQLiteDatabase db = DBHelper.getInstance().getDatabase();
        if (db == null || !db.isOpen()) {
            return list;
        }
        String sql = String.format(
                "SELECT %s, %s, %s from %s where %s = '%s' and %s >= '%s' and %s < '%s';",
                CounterEntry.REQ_ACTION, CounterEntry.REQ_COUNT, CounterEntry.REQ_SUCCESS,
                CounterEntry.TABLE_NAME,
                CounterEntry.REQ_PKG, pkgName,
                CounterEntry.REQ_DATE, TimeFormatUtils.getDateString(start),
                CounterEntry.REQ_DATE, TimeFormatUtils.getDateString(end));
        Logger.i(TAG, "execute sql: " + sql);
        Cursor cursor = db.rawQuery(sql, new String[]{});
        while (cursor.moveToNext()) {
            @SuppressLint("Range") String action = cursor.getString(cursor.getColumnIndex(CounterEntry.REQ_ACTION));
            @SuppressLint("Range") int count = cursor.getInt(cursor.getColumnIndex(CounterEntry.REQ_COUNT));
            @SuppressLint("Range") boolean success = cursor.getInt(cursor.getColumnIndex(CounterEntry.REQ_SUCCESS)) == 1;
            list.add(new XUnit(pkgName, action, count, success));
        }
        cursor.close();
        return list;
    }

    public static HashMap<String, List<XUnit>> queryAllCounter(Date start, Date end) throws Exception {
        HashMap<String, List<XUnit>> map = new HashMap<>();
        SQLiteDatabase db = DBHelper.getInstance().getDatabase();
        if (db == null || !db.isOpen()) {
            return map;
        }
        String sql = String.format("SELECT distinct(%s) from %s where %s >= '%s' and %s < '%s';",
                CounterEntry.REQ_PKG, CounterEntry.TABLE_NAME,
                CounterEntry.REQ_DATE, TimeFormatUtils.getDateString(start),
                CounterEntry.REQ_DATE, TimeFormatUtils.getDateString(end));
        Logger.i(TAG, "execute sql: " + sql);
        Cursor cursor = db.rawQuery(sql, new String[]{});
        while (cursor.moveToNext()) {
            @SuppressLint("Range") String pkgName = cursor.getString(cursor.getColumnIndex(CounterEntry.REQ_PKG));
            map.put(pkgName, queryPackageCounter(pkgName, start, end));
        }
        cursor.close();
        return map;
    }
}
