package com.android.actor.utils.downloader;

import java.io.File;

public interface DownloadCallback {

    void onEnd(String url, File file, String reason);
}
