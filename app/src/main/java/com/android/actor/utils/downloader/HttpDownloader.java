package com.android.actor.utils.downloader;

import com.android.actor.BuildConfig;
import com.android.actor.control.SelfUpdater;
import com.android.actor.device.DeviceNumber;
import com.android.actor.monitor.Logger;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.utils.DateUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class HttpDownloader {

    private static final String TAG = HttpDownloader.class.getSimpleName();
    private static OkHttpClient sHttpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private static final int REPORT_INTERVAL = 10 * 1000;

    private File mFile;
    protected String mUrl;
    private String mLastModified;
    private InputStream mResponseStream;
    private long mLastReport;
    private long mLength;

    public HttpDownloader(File file, String url) throws IOException {
        mFile = file;
        mUrl = url;
    }

    public boolean prepare() throws IOException {
        if (mFile.exists()) {
            if (mFile.length() == 0) {
                Logger.w(TAG, "File " + mFile + " exists but 0 bytes, try re download");
            } else {
                mLastModified = DateUtils.formatDate(new Date(mFile.lastModified()));
                Logger.v(TAG, "File " + mFile + " exists, mLastModified " + mLastModified);
            }
        }

        Request.Builder builder = new Request.Builder()
                .url(mUrl)
                .header("User-Agent", "RocketActor/" + BuildConfig.VERSION_CODE + " HttpDownloader");
        if (DeviceNumber.has()) {
            builder.header("number", DeviceNumber.get());
        }
        if (SelfUpdater.instance().isNoDelay()) {
            builder.header("no-delay", String.valueOf(true));
        }
        if (mLastModified != null) {
            builder.header("If-Modified-Since", mLastModified);
        }
        Request request = builder.build();
        Response response = sHttpClient.newCall(request).execute();

        if (response.code() == 304) {
            Logger.v(TAG, "File " + mFile.getName() + "(" + mUrl + ") not modified.");
            return false;
        }
        if (response.code() != 200) {
            throw new IOException("File " + mFile.getName() + " response code " + response.code());
        }

        mLength = response.body().contentLength();
        Logger.v(TAG, "File " + mFile.getName() + "(" + mUrl + "), length " + mLength);
        mResponseStream = response.body().byteStream();
        mLastModified = response.header("Last-Modified");
        mLastReport = System.currentTimeMillis();
        return true;
    }

    public void download() throws IOException {
        if (mFile.exists()) {
            mFile.delete();
        }
        File fileDl = new File(mFile.getAbsolutePath() + ".dl");
        if (fileDl.exists()) {
            fileDl.delete();
        }
        FileOutputStream output = FileUtils.openOutputStream(fileDl);

        long currentLength = 0;
        byte[] bytes = new byte[40960];
        int len;
        while ((len = mResponseStream.read(bytes)) > 0) {
            output.write(bytes, 0, len);
            currentLength += len;
            if (mLastReport + REPORT_INTERVAL < System.currentTimeMillis()) {
                Logger.v(TAG, "File " + mFile.getName() + " progress " + currentLength + "/" + mLength
                        + "(" + (currentLength / (float) mLength * 100) + "%)");
                mLastReport = System.currentTimeMillis();
            }
        }
        output.flush();
        output.getFD().sync();
        output.close();
        Logger.v(TAG, "File " + mFile.getName() + " progress " + currentLength + "/" + mLength
                + "(100%), mLastModified " + mLastModified);

        fileDl.setLastModified(DateUtils.parseDate(mLastModified).getTime());
        if (!fileDl.renameTo(mFile)) {
            throw new IOException("Can't rename " + fileDl + " to " + mFile);
        }
    }

    public String getLastModified() {
        return mLastModified;
    }
}
