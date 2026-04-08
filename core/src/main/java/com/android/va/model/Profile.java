package com.android.va.model;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import com.android.va.system.ProfileStatus;

public class Profile implements Parcelable {
    public int id;
    public ProfileStatus status;
    public String name;
    public long createTime;

    public Profile() {
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.id);
        dest.writeInt(this.status == null ? -1 : this.status.ordinal());
        dest.writeString(this.name);
        dest.writeLong(this.createTime);
    }

    protected Profile(Parcel in) {
        this.id = in.readInt();
        int tmpStatus = in.readInt();
        this.status = tmpStatus == -1 ? null : ProfileStatus.values()[tmpStatus];
        this.name = in.readString();
        this.createTime = in.readLong();
    }

    public static final Creator<Profile> CREATOR = new Creator<Profile>() {
        @Override
        public Profile createFromParcel(Parcel source) {
            return new Profile(source);
        }

        @Override
        public Profile[] newArray(int size) {
            return new Profile[size];
        }
    };

    @NonNull
    @Override
    public String toString() {
        return "Profile{" +
                "id=" + id +
                ", status=" + status +
                ", name='" + name + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}
