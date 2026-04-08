package android.content.pm;

public interface IPackageDataObserver extends android.os.IInterface {

    void onRemoveCompleted(String packageName, boolean succeeded);

    public static abstract class Stub extends android.os.Binder implements IPackageDataObserver {

        @Override public android.os.IBinder asBinder()
        {
            return this;
        }
    }
}
