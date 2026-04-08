package com.android.internal.aidl.service;

import android.os.Parcel;
import android.os.Parcelable;

public class RequestV0 implements Parcelable {

    public static final int TYPE_INFO = 1;
    public static final int TYPE_PING = 2;
    public static final int TYPE_ACTION = 3;
    public static final int TYPE_INFO_LIST = 4;

    private String app;
    private int type;
    private String action;
    private String body;

    public String getApp() {
        return app;
    }

    public int getType() {
        return type;
    }

    public String getAction() {
        return action;
    }

    public String getBody() {
        return body;
    }

    void setApp(String app) {
        this.app = app;
    }

    void setType(int type) {
        this.type = type;
    }

    void setAction(String action) {
        this.action = action;
    }

    void setBody(String body) {
        this.body = body;
    }

    RequestV0() {
    }

    protected RequestV0(Parcel in) {
        this.setApp(in.readString());
        this.setType(in.readInt());
        this.setAction(in.readString());
        this.setBody(in.readString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(app);
        parcel.writeInt(type);
        parcel.writeString(action);
        parcel.writeString(body);
    }

    public static final Creator<RequestV0> CREATOR = new Creator<RequestV0>() {
        @Override
        public RequestV0 createFromParcel(Parcel in) {
            return new RequestV0(in);
        }

        @Override
        public RequestV0[] newArray(int size) {
            return new RequestV0[size];
        }
    };

    @Override
    public String toString() {
        return String.format("[RequestV0-%d] app: %s, action: %s, body length: %s",
                type,
                app == null ? "null" : app,
                action == null ? "null" : action,
                body == null ? 0 : body.length());
    }
}
