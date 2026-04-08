package com.android.va.runtime;

import android.os.DeadObjectException;
import android.os.RemoteException;

import java.util.Collections;
import java.util.List;

import com.android.va.system.ServiceManager;
import com.android.va.system.IProfileManagerService;
import com.android.va.model.Profile;
import com.android.va.utils.Logger;

public class VProfileManager extends VManager<IProfileManagerService> {
    private static final String TAG = VProfileManager.class.getSimpleName();
    private static final long RETRY_DELAY_MS = 100L;
    private static final VProfileManager sVProfileManager = new VProfileManager();

    @FunctionalInterface
    private interface ProfileCall<T> {
        T run(IProfileManagerService s) throws RemoteException;
    }

    @FunctionalInterface
    private interface ProfileVoidCall {
        void run(IProfileManagerService s) throws RemoteException;
    }

    public static VProfileManager get() {
        return sVProfileManager;
    }

    @Override
    protected String getServiceName() {
        return ServiceManager.USER_MANAGER;
    }

    public Profile createProfile(int profileId) {
        return invoke(s -> s.createProfile(profileId), null, "createProfile");
    }

    public void deleteProfile(int profileId) {
        invokeVoid(s -> s.deleteProfile(profileId), "deleteProfile");
    }

    public List<Profile> getProfiles() {
        return invoke(IProfileManagerService::getProfiles, Collections.emptyList(), "getProfiles");
    }

    /** 默认 profile ID：列表第一个；无则 0。 */
    public int defaultProfileId() {
        List<Profile> profiles = getProfiles();
        return profiles.isEmpty() ? 0 : profiles.get(0).id;
    }

    private <T> T invoke(ProfileCall<T> call, T defaultValue, String op) {
        try {
            IProfileManagerService s = getService();
            if (s != null) {
                return call.run(s);
            }
            Logger.w(TAG, "Profile service unavailable: " + op);
        } catch (DeadObjectException e) {
            Logger.w(TAG, "Profile service died, retry: " + op, e);
            clearServiceCache();
            return retryAfterDelay(call, defaultValue, op);
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException: " + op, e);
        } catch (Exception e) {
            Logger.e(TAG, "Unexpected error: " + op, e);
        }
        return defaultValue;
    }

    private <T> T retryAfterDelay(ProfileCall<T> call, T defaultValue, String op) {
        try {
            Thread.sleep(RETRY_DELAY_MS);
            IProfileManagerService s = getService();
            if (s != null) {
                return call.run(s);
            }
            Logger.w(TAG, "Profile service unavailable after retry: " + op);
        } catch (DeadObjectException e) {
            Logger.w(TAG, "Profile service still dead after retry: " + op, e);
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException after retry: " + op, e);
        } catch (Exception e) {
            Logger.e(TAG, "Retry failed: " + op, e);
        }
        return defaultValue;
    }

    private void invokeVoid(ProfileVoidCall call, String op) {
        try {
            IProfileManagerService s = getService();
            if (s != null) {
                call.run(s);
                return;
            }
            Logger.w(TAG, "Profile service unavailable: " + op);
        } catch (DeadObjectException e) {
            Logger.w(TAG, "Profile service died, retry: " + op, e);
            clearServiceCache();
            retryVoidAfterDelay(call, op);
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException: " + op, e);
        } catch (Exception e) {
            Logger.e(TAG, "Unexpected error: " + op, e);
        }
    }

    private void retryVoidAfterDelay(ProfileVoidCall call, String op) {
        try {
            Thread.sleep(RETRY_DELAY_MS);
            IProfileManagerService s = getService();
            if (s != null) {
                call.run(s);
                return;
            }
            Logger.w(TAG, "Profile service unavailable after retry: " + op);
        } catch (DeadObjectException e) {
            Logger.w(TAG, "Profile service still dead after retry: " + op, e);
        } catch (RemoteException e) {
            Logger.e(TAG, "RemoteException after retry: " + op, e);
        } catch (Exception e) {
            Logger.e(TAG, "Retry failed: " + op, e);
        }
    }
}
