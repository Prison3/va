package com.android.internal.aidl.service;

import com.android.actor.monitor.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ResultBody {
    private final static String TAG = ResultBody.class.getSimpleName();

    private final JSONObject data = new JSONObject();
    private ICode code = BaseService.CODE_SUCCESS;

    public ResultBody() {
        try {
            data.put("packTime", System.currentTimeMillis());
            data.put("data", "");
        } catch (JSONException ignore) {
        }
    }

    public ResultBody(ICode code) {
        try {
            data.put("packTime", System.currentTimeMillis());
            data.put("data", "");
        } catch (JSONException ignore) {
        }
        this.code = code;
    }

    public ResultBody(Object object) {
        try {
            data.put("packTime", System.currentTimeMillis());
        } catch (JSONException ignore) {
        }
        if (object instanceof ICode) {
            this.code = (ICode) object;
        } else {
            this.setData(object);
        }
    }

    public ResultBody put(String key, Object value) {
        try {
            data.put(key, value);
        } catch (JSONException e) {
            Logger.e(TAG, e.toString());
        }
        return this;
    }

    /**
     * 手动设置返回状态码，默认构造为CODE_SUCCESS
     *
     * @return
     */
    public ResultBody setCode(ICode code) {
        this.code = code;
        return this;
    }


    /**
     * 设置提示信息，用于开发人员辅助定位错误；当状态码不足以提示时，可以设置该信息；
     * 例如缺少参数CODE_BODY_INVALID，可以在此提示具体缺少什么参数。
     *
     * @param msg 提示信息
     * @return
     */
    public ResultBody setMsg(String msg) {
        this.put("msg", msg);
        return this;
    }

    /**
     * 设置原始抓取json，应当解析该json，判断app返回是否有效数据，然后设置code
     *
     * @param obj json字符串
     *            json object
     * @return
     */
    public ResultBody setData(Object obj) {
        if (obj == null) {
            return this;
        } else if (obj instanceof String) {
            try {
                data.put("data", new JSONObject((String) obj));
            } catch (JSONException e) {
                code = BaseService.CODE_XPOSED_ERROR;
                this.put("msg", "Origin json string can't cast to json obj.");
                this.put("originJsonStr", obj);
            }
        } else if (obj instanceof JSONObject || obj instanceof JSONArray) {
            try {
                data.put("data", obj);
            } catch (JSONException ignore) {
            }
        } else {
            try {
                data.put("data", obj.toString());
            } catch (JSONException ignore) {
            }
        }
        return this;
    }

    @Override
    public String toString() {
        try {
            data.put("code", code.getCode());
            data.put("ok", code.equals(BaseService.CODE_SUCCESS));
            data.put("reason", code.getReason());
        } catch (JSONException ignore) {

        }
        return data.toString();
    }

    public ICode getCode() {
        return this.code;
    }
}
