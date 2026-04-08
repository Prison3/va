package com.android.va.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.AtomicFile;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.android.va.runtime.VEnvironment;
import com.android.va.system.VPackageInfo;
import com.android.va.system.VPackageUserState;
import com.android.va.system.VUserHandle;
import com.android.va.utils.CloseUtils;
import com.android.va.utils.FileUtils;

public class VPackageSettings implements Parcelable {
    public VPackageInfo pkg;
    public int appId;
    public InstallOption installOption;
    public Map<Integer, VPackageUserState> userState = new HashMap<>();
    static final VPackageUserState DEFAULT_USER_STATE = new VPackageUserState();

    // 记录首次安装时间和最后更新时间
    public long firstInstallTime;
    public long lastUpdateTime;

    public VPackageSettings() {
    }

    public List<VPackageUserState> getUserState() {
        return new ArrayList<>(userState.values());
    }

    public List<Integer> getUserIds() {
        return new ArrayList<>(userState.keySet());
    }

    public void setInstalled(boolean inst, int userId) {
        modifyUserState(userId).installed = inst;
    }

    public boolean getInstalled(int userId) {
        return readUserState(userId).installed;
    }

    public boolean getStopped(int userId) {
        return readUserState(userId).stopped;
    }

    public void setStopped(boolean stop, int userId) {
        modifyUserState(userId).stopped = stop;
    }

    public boolean getHidden(int userId) {
        return readUserState(userId).hidden;
    }

    public void setHidden(boolean hidden, int userId) {
        modifyUserState(userId).hidden = hidden;
    }

    public void removeUser(int userId) {
        userState.remove(userId);
    }

    public VPackageUserState readUserState(int userId) {
        VPackageUserState state = userState.get(userId);
        if (state == null) {
            state = new VPackageUserState();
        }
        state = new VPackageUserState(state);
        if (userId == VUserHandle.USER_ALL) {
            state.installed = true;
        }
        return state;
    }

    private VPackageUserState modifyUserState(int userId) {
        VPackageUserState state = userState.get(userId);
        if (state == null) {
            state = new VPackageUserState();
            userState.put(userId, state);
        }
        return state;
    }

    public boolean save() {
        synchronized (this) {
            Parcel parcel = Parcel.obtain();
            AtomicFile atomicFile = new AtomicFile(VEnvironment.getPackageConf(pkg.packageName));
            FileOutputStream fileOutputStream = null;
            try {
                writeToParcel(parcel, 0);
                parcel.setDataPosition(0);
                fileOutputStream = atomicFile.startWrite();
                FileUtils.writeParcelToOutput(parcel, fileOutputStream);
                atomicFile.finishWrite(fileOutputStream);
                return true;
            } catch (Throwable e) {
                e.printStackTrace();
                atomicFile.failWrite(fileOutputStream);
                return false;
            } finally {
                parcel.recycle();
                CloseUtils.close(fileOutputStream);
            }
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.pkg, flags);
        dest.writeInt(this.appId);
        dest.writeParcelable(this.installOption, flags);
        dest.writeInt(this.userState.size());
        for (Map.Entry<Integer, VPackageUserState> entry : this.userState.entrySet()) {
            dest.writeValue(entry.getKey());
            dest.writeParcelable(entry.getValue(), flags);
        }
        dest.writeLong(this.firstInstallTime);
        dest.writeLong(this.lastUpdateTime);
    }

    public VPackageSettings(Parcel in) {
        this.pkg = in.readParcelable(VPackageInfo.class.getClassLoader());
        this.appId = in.readInt();
        this.installOption = in.readParcelable(InstallOption.class.getClassLoader());
        int userStateSize = in.readInt();
        this.userState = new HashMap<Integer, VPackageUserState>(userStateSize);
        for (int i = 0; i < userStateSize; i++) {
            Integer key = (Integer) in.readValue(Integer.class.getClassLoader());
            VPackageUserState value = in.readParcelable(VPackageUserState.class.getClassLoader());
            this.userState.put(key, value);
        }
        this.firstInstallTime = in.readLong();
        this.lastUpdateTime = in.readLong();
    }

    public static final Creator<VPackageSettings> CREATOR = new Creator<VPackageSettings>() {
        @Override
        public VPackageSettings createFromParcel(Parcel source) {
            return new VPackageSettings(source);
        }

        @Override
        public VPackageSettings[] newArray(int size) {
            return new VPackageSettings[size];
        }
    };
}
