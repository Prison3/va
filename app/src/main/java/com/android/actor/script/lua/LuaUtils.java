package com.android.actor.script.lua;

import com.android.actor.monitor.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;
import org.luaj.vm2.lib.jse.CoerceLuaToJava;

import java.util.Iterator;
import java.util.List;

public class LuaUtils {
    private final static String TAG = LuaUtils.class.getSimpleName();

    public static LuaTable listToTable(List<?> list) {
        if (list == null) {
            return null;
        }
        if (list.size() == 0) {
            return null;
        }
        LuaTable table = LuaValue.tableOf();
        for (int i = 0; i < list.size(); i++) {
            table.insert(i + 1, CoerceJavaToLua.coerce(list.get(i)));
        }
        return table;
    }

    public static LuaTable jsonToTable(String jsonStr) throws JSONException {
        return jsonToTable(new JSONObject(jsonStr));
    }

    public static LuaTable jsonToTable(JSONObject jsonObject) throws JSONException {
        if (jsonObject == null) {
            return null;
        }
        LuaTable table = LuaValue.tableOf();
        Iterator<String> it = jsonObject.keys();
        while (it.hasNext()) {
            String key = it.next();
            Object value = jsonObject.get(key);
            if (value instanceof JSONObject) {
                table.set(key, jsonToTable((JSONObject) value));
            } else if (value instanceof JSONArray) {
                table.set(key, jsonToTable((JSONArray) value));
            } else {
                table.set(key, CoerceJavaToLua.coerce(value));
            }
        }
        return table;
    }

    public static LuaTable jsonToTable(JSONArray jsonArray) throws JSONException {
        if (jsonArray == null) {
            return null;
        }
        LuaTable table = LuaValue.tableOf();
        // LuaTable start with 1; see test()
        for (int i = 0; i < jsonArray.length(); i++) {
            Object value = jsonArray.get(i);
            if (value instanceof JSONObject) {
                table.insert(i + 1, jsonToTable((JSONObject) value));
            } else if (value instanceof JSONArray) {
                table.insert(i + 1, jsonToTable((JSONArray) value));
            } else {
                table.insert(i + 1, CoerceJavaToLua.coerce(value));
            }
        }
        return table;
    }

    public static Object tableToJson(LuaTable table) throws JSONException {
        if (table == null) {
            return null;
        }
        if (table.length() == 0) {
            JSONObject jsonObject = new JSONObject();
            for (LuaValue key : table.keys()) {
                LuaValue value = table.get(key);
                if (value instanceof LuaTable) {
                    jsonObject.put(key.tojstring(), tableToJson((LuaTable) value));
                } else {
                    jsonObject.put(key.tojstring(), CoerceLuaToJava.coerce(value, Object.class));
                }
            }
            return jsonObject;
        } else if (table.length() != 0 && table.length() == table.keyCount()) {
            JSONArray jsonArray = new JSONArray();
            for (int i = 1; i < table.length() + 1; i++) {
                LuaValue value = table.get(i);
                if (value instanceof LuaTable) {
                    jsonArray.put(tableToJson((LuaTable) value));
                } else {
                    jsonArray.put(CoerceLuaToJava.coerce(value, Object.class));
                }
            }
            return jsonArray;
        } else {
            throw new JSONException("Not match jsonArray or jsonObject. length: " + table.length() + ", keyCount: " + table.keyCount());
        }
    }

    public static String tableToJsonStr(LuaTable table) throws JSONException {
        Object obj = tableToJson(table);
        if (obj == null) {
            return null;
        }
        return obj.toString();
    }

    public static JSONObject tableToJsonObject(LuaTable table) throws JSONException {
        Object obj = tableToJson(table);
        if (obj != null && obj instanceof JSONObject) {
            return (JSONObject) obj;
        } else {
            throw new JSONException("Cannot case to JSONObject," +
                    (obj == null ? "object is null." : "current obj class: " + obj.getClass()));
        }
    }

    public static JSONArray tableToJsonArray(LuaTable table) throws JSONException {
        Object obj = tableToJson(table);
        if (obj != null && obj instanceof JSONArray) {
            return (JSONArray) obj;
        } else {
            throw new JSONException("Cannot case to JSONArray," +
                    (obj == null ? "object is null." : "current obj class: " + obj.getClass()));
        }
    }

    public static void test() throws JSONException {
        LuaTable table = LuaValue.tableOf();
        table.set(1, "a");
        table.set(2, "b");
        table.set(0, "c");
        table.set(3, LuaValue.NIL);
        table.set("d", 4);
        table.set("e", "e");
        table.set("g", CoerceJavaToLua.coerce("dd"));
        table.set("e", "h");
        table.set("h", LuaValue.NIL);
        for (LuaValue key : table.keys()) {
            Logger.d(TAG, key + " " + table.get(key));
            Logger.d(TAG, key.tojstring() + " " + table.get(key).getClass());
        }
        Logger.d(TAG, table.keyCount());
        Logger.d(TAG, table.length()); // 数字的长度

        LuaTable t1 = LuaTable.tableOf();
        // cannot start from 0, else error with keyCount not match length;
//        t1.set(0, "a");
        t1.set(1, "b");
        t1.set(2, "c");
        LuaTable t2 = LuaTable.tableOf();
        t2.set("num", t1);
        t2.set("char", "d");
        Object obj = tableToJson(t2);
        Logger.d(TAG, obj.getClass());
        Logger.d(TAG, obj);
        Logger.d(TAG, jsonToTable((JSONObject) obj));
    }
}
