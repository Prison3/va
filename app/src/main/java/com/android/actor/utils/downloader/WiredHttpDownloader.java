package com.android.actor.utils.downloader;

import android.net.Uri;

import com.android.actor.device.DeviceInfoManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.ActStringUtils;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

public class WiredHttpDownloader extends HttpDownloader {

    private static final String TAG = WiredHttpDownloader.class.getSimpleName();
    private boolean mReachable = false;

    public WiredHttpDownloader(File file, String url) throws IOException {
        super(file, url);
    }

    @Override
    public boolean prepare() throws IOException {
        String host = Uri.parse(mUrl).getHost();
        if (!ActStringUtils.isIP(host)) {
            throw new IOException(host + " is not ip.");
        }
        String gateway = DeviceInfoManager.getInstance().getConnManager().getWired().getGateway();
        if (ActStringUtils.isEmpty(gateway)) {
            throw new IOException("no wired gateway.");
        }
        InetAddress addr = InetAddress.getByName(gateway);
        if (!addr.isReachable(2000)) {
            throw new IOException("gateway " + gateway + " unreachable.");
        }
        mReachable = true;
        mUrl = mUrl.replace(host, gateway);
        Logger.d(TAG, "Url is replaced to " + mUrl);
        return super.prepare();
    }

    public boolean isReachable() {
        return mReachable;
    }
}
