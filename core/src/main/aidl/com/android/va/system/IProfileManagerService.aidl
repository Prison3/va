// IProfileManagerService.aidl
package com.android.va.system;

import com.android.va.model.Profile;
import java.util.List;

interface IProfileManagerService {
    Profile getProfile(int profileId);
    boolean hasProfile(int profileId);
    Profile createProfile(int profileId);
    List<Profile> getProfiles();
    void deleteProfile(int profileId);
}
