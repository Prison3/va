// IPackageInstallerService.aidl
package com.android.va.system;

import com.android.va.model.VPackageSettings;
import com.android.va.model.InstallOption;

interface IPackageInstallerService {
    int installPackageAsUser(in VPackageSettings ps, int userId);
    int uninstallPackageAsUser(in VPackageSettings ps, boolean removeApp, int userId);
    int clearPackage(in VPackageSettings ps, int userId);
    int updatePackage(in VPackageSettings ps);
}
