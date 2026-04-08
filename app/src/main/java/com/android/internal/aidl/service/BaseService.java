package com.android.internal.aidl.service;

public abstract class BaseService {

    public final static ICode CODE_SUCCESS = ICode.newCode(10001, "success"); // 返回目标数据，各app应当解析请求app的返回内容
    public final static ICode CODE_ACTION_NOT_FOUND = ICode.newCode(10002, "no such action"); // 没有相关action
    public final static ICode CODE_BODY_INVALID = ICode.newCode(10003, "body invalid"); // 请求body json解析错误
    public final static ICode CODE_XPOSED_ERROR = ICode.newCode(10010, "xposed error");
    public final static ICode CODE_APP_NOT_REGISTERED = ICode.newCode(10011, "app not registered");
    public final static ICode CODE_APP_DIED = ICode.newCode(10012, "app died");
    public final static ICode CODE_NO_DEX_INFO = ICode.newCode(10013, "no dex info");
    public final static ICode CODE_APP_TIME_OUT = ICode.newCode(10021, "app_req time out"); // 请求app超时
    public final static ICode CODE_APP_ERROR = ICode.newCode(10022, "app_req return error"); // 请求app错误，例如app返回错误这些
    public final static ICode CODE_USER_BLOCKED = ICode.newCode(10023, "account is blocked by mt or proxy error, please check it out"); // 账号可能被风控
    public final static ICode CODE_SYSTEM_NOT_READY = ICode.newCode(10024, "system is not ready.");
}