package com.android.internal.aidl.service;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SharedMemory;
import android.system.ErrnoException;
import android.system.OsConstants;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ResponseV0 implements Parcelable {
    private boolean success;
    private int code;
    private String reason;
    private SharedMemory sharedMemory;
    private static SharedMemory lastSharedMemory;
    private String body; // use body if < 300K
    private boolean hasRead = false;

    ResponseV0() {
    }

    public boolean getSuccess() {
        return success;
    }

    public String getReason() {
        return reason;
    }

    public int getCode() {
        return code;
    }

    /**
     * Get body string from shared memory and close it immediately.
     */
    public String getBody() throws ErrnoException {
        if (hasRead) {
            return null;
        }
        String ret;
        if (body != null) {
            ret = body;
        } else {
            ByteBuffer buffer = sharedMemory.mapReadOnly();
            ret = StandardCharsets.UTF_8.decode(buffer).toString();
            SharedMemory.unmap(buffer);
            sharedMemory.close();
        }
        hasRead = true;
        return ret;
    }

    /**
     * Get body string without release memory.
     * You should use getBody() to release memory instead of using this method.
     */
    public String getBodyWithoutClose() throws ErrnoException {
        if (hasRead) {
            return null;
        }
        ByteBuffer buffer = sharedMemory.mapReadOnly();
        return StandardCharsets.UTF_8.decode(buffer).toString();
    }

    void setSuccess(boolean success) {
        this.success = success;
    }

    void setCode(int code) {
        this.code = code;
    }

    void setReason(String reason) {
        this.reason = reason;
    }

    void setBody(String body) throws ErrnoException {
        if (body.length() < 300 * 1024) {
            this.body = body;
        } else {
            if (lastSharedMemory != null) {
                lastSharedMemory.close();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            sharedMemory = SharedMemory.create("ResponseV0", bytes.length);
            ByteBuffer buffer = sharedMemory.mapReadWrite();
            buffer.put(bytes);
            sharedMemory.setProtect(OsConstants.PROT_READ);
            SharedMemory.unmap(buffer);
            lastSharedMemory = sharedMemory;
        }
    }

    protected ResponseV0(Parcel in) {
        super();
        this.setSuccess(in.readByte() != 0);
        this.setCode(in.readInt());
        this.setReason(in.readString());
        this.sharedMemory = in.readParcelable(ClassLoader.getSystemClassLoader());
        this.body = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @TargetApi(Build.VERSION_CODES.O_MR1)
    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeByte((byte) (success ? 1 : 0));
        parcel.writeInt(code);
        parcel.writeString(reason);
        parcel.writeParcelable(sharedMemory, CONTENTS_FILE_DESCRIPTOR);
        parcel.writeString(body);
    }

    public static final Creator<ResponseV0> CREATOR = new Creator<ResponseV0>() {
        @Override
        public ResponseV0 createFromParcel(Parcel in) {
            return new ResponseV0(in);
        }

        @Override
        public ResponseV0[] newArray(int size) {
            return new ResponseV0[size];
        }
    };

    @SuppressLint("DefaultLocale")
    @Override
    public String toString() {
        return String.format("[ResponseV0] %s, code %d, reason: %s, sharedMemory length: %s, body length: %s",
                success ? "success" : "fail",
                code,
                reason == null ? "null" : reason,
                sharedMemory == null ? -1 : sharedMemory.getSize(),
                body == null ? -1 : body.length());
    }
}
