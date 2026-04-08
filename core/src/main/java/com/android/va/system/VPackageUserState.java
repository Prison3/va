package com.android.va.system;

import android.os.Parcel;
import android.os.Parcelable;

public class VPackageUserState implements Parcelable {
    public boolean installed;
    public boolean stopped;
    public boolean hidden;

    public VPackageUserState() {
        this.installed = false;
        this.stopped = true;
        this.hidden = false;
    }

    public static VPackageUserState create() {
        VPackageUserState state = new VPackageUserState();
        state.installed = true;
        return state;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(this.installed ? (byte) 1 : (byte) 0);
        dest.writeByte(this.stopped ? (byte) 1 : (byte) 0);
        dest.writeByte(this.hidden ? (byte) 1 : (byte) 0);
    }

    protected VPackageUserState(Parcel in) {
        this.installed = in.readByte() != 0;
        this.stopped = in.readByte() != 0;
        this.hidden = in.readByte() != 0;
    }

    public VPackageUserState(VPackageUserState state) {
        this.installed = state.installed;
        this.stopped = state.stopped;
        this.hidden = state.hidden;
    }

    public static final Parcelable.Creator<VPackageUserState> CREATOR = new Parcelable.Creator<VPackageUserState>() {
        @Override
        public VPackageUserState createFromParcel(Parcel source) {
            return new VPackageUserState(source);
        }

        @Override
        public VPackageUserState[] newArray(int size) {
            return new VPackageUserState[size];
        }
    };
}
