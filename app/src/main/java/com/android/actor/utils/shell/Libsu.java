package com.android.actor.utils.shell;

import android.app.Activity;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import com.android.actor.ActApp;
import com.android.actor.monitor.Logger;
import com.android.actor.ui.Dialogs;
import com.android.actor.utils.BlockingReference;
import com.android.internal.ILibsuService;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.Shell.Result;
import com.topjohnwu.superuser.ipc.RootService;
import com.topjohnwu.superuser.nio.ExtendedFile;
import com.topjohnwu.superuser.nio.FileSystemManager;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Libsu {

    private static final String TAG = Libsu.class.getSimpleName();
    private static AIDLConnection sAIDLConnection;
    private static FileSystemManager sRemoteFS;
    private static ILibsuService sIPC;

    public static void waitReady(Runnable runnable) {
        ActApp.getMainHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isReady()) {
                    ActApp.getMainHandler().postDelayed(this, 1000);
                    return;
                }
                runnable.run();
            }
        }, 1000);
    }

    public static boolean isReady() {
        return sRemoteFS != null;
    }

    public static void reboot() {
        Logger.i(TAG, "Sleep 5s then reboot.");
        exec("sync; sleep 5; reboot;");
    }

    public static void rebootWithConfirmation(Activity activity) {
        Dialogs.showConfirmDialog(activity, "Reboot?", (dialog, which) -> reboot());
    }

    public static void rebootWithNecessary(Activity activity) {
        Dialogs.showConfirmDialog(activity, "Need reboot to take effect, reboot now?", (dialog, which) -> reboot());
    }

    public static Result exec(String... commands) {
        Logger.v(TAG, "exec: " + ArrayUtils.toString(commands));
        Result result = Shell.cmd(commands).exec();
        if (result.getCode() != 0) {
            Logger.e(TAG, "result: " + result.getCode() + ", " + result.getErr() + ", " + result.getOut());
        }
        return result;
    }

    public static FileSystemManager fs() {
        if (sRemoteFS == null) {
            throw new RuntimeException("remoteFS is null");
        }
        return sRemoteFS;
    }

    public static String[] listNames(String path) throws IOException {
        return fs().getFile(path).list();
    }

    public static ExtendedFile[] listFiles(String path) throws IOException {
        return fs().getFile(path).listFiles();
    }

    public static boolean exists(String path) throws IOException {
        return fs().getFile(path).exists();
    }

    public static int getPathUid(String path) throws IOException {
        try {
            return sIPC.getPathUid(path);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public static void mkdir(String path) throws IOException {
        if (!fs().getFile(path).mkdir()) {
            throw new IOException("Can't create " + path);
        }
    }

    public static void rename(String from, String to) throws IOException {
        if (!fs().getFile(from).renameTo(fs().getFile(to))) {
            throw new IOException("Can't rename " + from + " to " + to);
        }
    }

    public static String readFileToString(String path) throws IOException {
        return readFileToString(fs().getFile(path));
    }

    public static String readFileToString(ExtendedFile file) throws IOException {
        if (!file.exists()) {
            return null;
        }
        InputStream in = file.newInputStream();
        String content = IOUtils.toString(in);
        in.close();
        return content;
    }

    public static byte[] readFileToByteArray(ExtendedFile file) throws IOException {
        if (!file.exists()) {
            return null;
        }
        InputStream in = file.newInputStream();
        byte[] bytes = IOUtils.toByteArray(in);
        in.close();
        return bytes;
    }

    public static void writeStringToFile(String path, String content) throws IOException {
        writeStringToFile(fs().getFile(path), content);
    }

    public static void writeStringToFile(ExtendedFile file, String content) throws IOException {
        OutputStream out = file.newOutputStream();
        IOUtils.write(content, out);
        out.close();
        file.setReadable(true, false);
    }

    public static void startTethering() throws RemoteException {
        sIPC.startTethering();
    }

    public static void stopTethering() throws RemoteException {
        sIPC.stopTethering();
    }

    public static void connectService() {
        Logger.d(TAG, "Connect libsu service.");
        Intent intent = new Intent(ActApp.getInstance(), LibsuService.class);
        intent.addCategory(RootService.CATEGORY_DAEMON_MODE);
        RootService.bind(intent, new AIDLConnection());
    }

    static class AIDLConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Logger.d(TAG, "onServiceConnected");
            sAIDLConnection = this;
            sIPC = ILibsuService.Stub.asInterface(service);
            try {
                IBinder binder = sIPC.getFileSystemService();
                sRemoteFS = FileSystemManager.getRemote(binder);
                Logger.d(TAG, "sRemoteFS " + sRemoteFS);
            } catch (RemoteException e) {
                throw new RuntimeException("Can't get remoteFS");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            throw new RuntimeException("libsu onServiceDisconnected");
        }
    }
}
