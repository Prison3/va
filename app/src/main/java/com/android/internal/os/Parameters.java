package com.android.internal.os;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.SharedMemory;
import android.system.ErrnoException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class Parameters implements Parcelable {

    private SharedMemory mSharedMemory;

    public Parameters(String parameters) throws ErrnoException {
        byte[] bytes = parameters.getBytes(StandardCharsets.UTF_8);
        mSharedMemory = SharedMemory.create("Parameters", bytes.length);
        ByteBuffer buffer = mSharedMemory.mapReadWrite();
        buffer.put(bytes);
        SharedMemory.unmap(buffer);
    }

    protected Parameters(Parcel in) {
        mSharedMemory = in.readParcelable(ClassLoader.getSystemClassLoader());
    }

    public String getString() throws ErrnoException {
        if (mSharedMemory == null) {
            return null;
        }
        ByteBuffer buffer = mSharedMemory.mapReadOnly();
        String ret = StandardCharsets.UTF_8.decode(buffer).toString();
        SharedMemory.unmap(buffer);
        mSharedMemory.close();
        return ret;
    }

    public static final Creator<Parameters> CREATOR = new Creator<Parameters>() {
        @Override
        public Parameters createFromParcel(Parcel in) {
            return new Parameters(in);
        }

        @Override
        public Parameters[] newArray(int size) {
            return new Parameters[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mSharedMemory, CONTENTS_FILE_DESCRIPTOR);
    }
}
