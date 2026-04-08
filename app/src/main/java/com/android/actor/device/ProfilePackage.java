package com.android.actor.device;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

public class ProfilePackage {

    public String packageName;
    public int profileId;

    public ProfilePackage(String _packageName) {
        String[] parts = StringUtils.split(_packageName, '-');
        packageName = parts[0];
        profileId = parts.length == 2 ? Integer.parseInt(parts[1]) : 0;
    }

    public ProfilePackage(String packageName, int profileId) {
        this.packageName = packageName;
        this.profileId = profileId;
    }

    public static ProfilePackage create(String _packageName) {
        return new ProfilePackage(_packageName);
    }

    public static ProfilePackage create(String packageName, int profileId) {
        return new ProfilePackage(packageName, profileId);
    }

    @NonNull
    @Override
    public String toString() {
        return packageName + '-' + profileId;
    }

    public String toDisplay() {
        return packageName + " - " + profileId;
    }

    public String toStore() {
        return packageName + (profileId == 0 ? "" : "-" + profileId);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof ProfilePackage) {
            ProfilePackage p = (ProfilePackage) obj;
            return packageName.equals(p.packageName) && profileId == p.profileId;
        }
        return false;
    }
}
