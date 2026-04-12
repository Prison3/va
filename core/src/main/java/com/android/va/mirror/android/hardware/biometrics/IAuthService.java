package com.android.va.mirror.android.hardware.biometrics;

import android.os.IBinder;
import android.os.IInterface;

import com.android.reflection.annotation.BClassName;
import com.android.reflection.annotation.BStaticMethod;

/**
 * Mirror of {@code android.hardware.biometrics.IAuthService} (AIDL Stub).
 *
 * @see <a href="https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/hardware/biometrics/IAuthService.aidl">IAuthService.aidl</a>
 */
@BClassName("android.hardware.biometrics.IAuthService")
public interface IAuthService {

    @BClassName("android.hardware.biometrics.IAuthService$Stub")
    interface Stub {
        @BStaticMethod
        IInterface asInterface(IBinder binder);
    }
}
