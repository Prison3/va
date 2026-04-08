package android.webkit;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

public interface IWebViewUpdateService extends IInterface {

    String changeProviderAndSetting(String str) throws RemoteException;

    public static abstract class Stub extends Binder implements IWebViewUpdateService {

        public static IWebViewUpdateService asInterface(IBinder iBinder) {
            return null;
        }
    }
}
