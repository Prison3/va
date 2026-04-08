package com.android.va.system;

import android.os.Parcel;
import android.os.RemoteException;

import androidx.core.util.AtomicFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.android.va.runtime.VEnvironment;
import com.android.va.model.Profile;
import com.android.va.utils.CloseUtils;
import com.android.va.utils.FileUtils;

public class ProfileManagerService extends IProfileManagerService.Stub implements ISystemService {
    private static ProfileManagerService sService = new ProfileManagerService();
    public final HashMap<Integer, Profile> mUsers = new HashMap<>();
    public final Object mUserLock = new Object();

    public static ProfileManagerService get() {
        return sService;
    }

    @Override
    public void systemReady() {
        scanUserL();
    }

    @Override
    public Profile getProfile(int profileId) {
        synchronized (mUserLock) {
            return mUsers.get(profileId);
        }
    }

    @Override
    public boolean hasProfile(int profileId) {
        synchronized (mUsers) {
            return mUsers.get(profileId) != null;
        }
    }

    @Override
    public Profile createProfile(int profileId) throws RemoteException {
        synchronized (mUserLock) {
            if (hasProfile(profileId)) {
                return getProfile(profileId);
            }
            return createProfileLocked(profileId);
        }
    }

    @Override
    public List<Profile> getProfiles() {
        synchronized (mUsers) {
            ArrayList<Profile> bUsers = new ArrayList<>();
            for (Profile value : mUsers.values()) {
                if (value.id >= 0) {
                    bUsers.add(value);
                }
            }
            return bUsers;
        }
    }

    public List<Profile> getAllUsers() {
        synchronized (mUsers) {
            return new ArrayList<>(mUsers.values());
        }
    }

    @Override
    public void deleteProfile(int profileId) throws RemoteException {
        synchronized (mUserLock) {
            synchronized (mUsers) {
                PackageManagerService.get().deleteUser(profileId);

                mUsers.remove(profileId);
                saveUserInfoLocked();
                FileUtils.deleteDir(VEnvironment.getUserDir(profileId));
                FileUtils.deleteDir(VEnvironment.getExternalUserDir(profileId));
            }
        }
    }

    private Profile createProfileLocked(int profileId) {
        Profile profile = new Profile();
        profile.id = profileId;
        profile.status = ProfileStatus.ENABLE;
        mUsers.put(profileId, profile);
        synchronized (mUsers) {
            saveUserInfoLocked();
        }
        return profile;
    }

    private void saveUserInfoLocked() {
        Parcel parcel = Parcel.obtain();
        AtomicFile atomicFile = new AtomicFile(VEnvironment.getUserInfoConf());
        FileOutputStream fileOutputStream = null;
        try {
            ArrayList<Profile> bUsers = new ArrayList<>(mUsers.values());
            parcel.writeTypedList(bUsers);
            try {
                fileOutputStream = atomicFile.startWrite();
                FileUtils.writeParcelToOutput(parcel, fileOutputStream);
                atomicFile.finishWrite(fileOutputStream);
            } catch (IOException e) {
                e.printStackTrace();
                atomicFile.failWrite(fileOutputStream);
            } finally {
                CloseUtils.close(fileOutputStream);
            }
        } finally {
            parcel.recycle();
        }
    }

    private void scanUserL() {
        synchronized (mUserLock) {
            Parcel parcel = Parcel.obtain();
            InputStream is = null;
            try {
                File userInfoConf = VEnvironment.getUserInfoConf();
                if (!userInfoConf.exists()) {
                    return;
                }
                is = new FileInputStream(VEnvironment.getUserInfoConf());
                byte[] bytes = FileUtils.toByteArray(is);
                parcel.unmarshall(bytes, 0, bytes.length);
                parcel.setDataPosition(0);

                ArrayList<Profile> loadUsers = parcel.createTypedArrayList(Profile.CREATOR);
                if (loadUsers == null)
                    return;
                synchronized (mUsers) {
                    mUsers.clear();
                    for (Profile loadUser : loadUsers) {
                        mUsers.put(loadUser.id, loadUser);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                parcel.recycle();
                CloseUtils.close(is);
            }
        }
    }
}
