package com.android.actor.utils.db;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import com.android.actor.ActApp;
import com.android.actor.monitor.Logger;

public class DBHelper extends SQLiteOpenHelper {
    private final static String TAG = DBHelper.class.getSimpleName();
    private static final int DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "actor.db";
    private static DBHelper instance;
    private SQLiteDatabase database;

    public static DBHelper getInstance() {
        if (instance == null) {
            instance = new DBHelper(ActApp.getInstance());
        }
        return instance;
    }

    private DBHelper(@Nullable Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        new Thread(() -> {
            Logger.d(TAG, "initialize database connection.");
            getDatabase();
        }).start();
    }

    /**
     * first open when AccessibilityService onCreate
     * try to close when AccessibilityService onDestroy
     */
    public SQLiteDatabase getDatabase() {
        if (database == null || !database.isOpen()) {
            database = getWritableDatabase();
        }
        return database;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        execSQLSafety(db, XposedContract.getCreateSql());
        execSQLSafety(db, TrafficContract.getCreateSql());
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1 && newVersion == 2) {
            // v1.3.6->v1.3.7: update xposed_counter table to xposed_record
            execSQLSafety(db, "DROP TABLE IF EXISTS xposed_counter");
            execSQLSafety(db, XposedContract.getCreateSql());
        }
    }

    public static void execSQLSafety(SQLiteDatabase db, String sql) {
        try {
            if (db != null && db.isOpen()) {
                Logger.i(TAG, "execSQLSafety: " + sql);
                db.execSQL(sql);
            }
        } catch (SQLException e) {
            Logger.e(TAG, e.toString(), e);
        }
    }
}
