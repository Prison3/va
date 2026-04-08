package android.hardware;

import android.os.RemoteException;

public interface ICameraService {
    int setMockSource(String url, int type) throws RemoteException;
}
