// IStorageManagerService.aidl
package com.android.va.system;

import android.os.storage.StorageVolume;
import java.lang.String;
import android.net.Uri;

interface IStorageManagerService {
      StorageVolume[] getVolumeList(int uid, String packageName, int flags, int userId);
      Uri getUriForFile(String file);
}
