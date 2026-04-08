package com.android.actor.utils.downloader;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.regions.ServiceAbbreviations;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.android.actor.device.DeviceInfoManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.FileHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class AmazonS3Manager {
    private final static String TAG = AmazonS3Manager.class.getSimpleName();
    private final static String ACCESS_KEY = "AKIAZMHU4NKMDZZQJJ47";
    private final static String SECRET_KEY = "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";
    private final static Regions MY_REGION = Regions.US_WEST_2;
    private final static String BUCKET = "sylu";
    private final static String KEY_PREFIX = "actors";

    private static AmazonS3Manager sInstance;
    private final AmazonS3Client mClient;

    private AmazonS3Manager() {
        ClientConfiguration config = new ClientConfiguration();
        AWSCredentials credentials = new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY);
        mClient = new AmazonS3Client(credentials, config);
        Region region = Region.getRegion(MY_REGION);
        mClient.setEndpoint(region.getServiceEndpoint(ServiceAbbreviations.S3));
        Logger.d(TAG, "Init aws s3 client with endpoint: " + mClient.getEndpoint());
    }

    public static AmazonS3Manager getInstance() {
        if (sInstance == null) {
            sInstance = new AmazonS3Manager();
        }
        return sInstance;
    }

    public InputStream downloadFromS3(String key) {
        Logger.d(TAG, "Download from s3: " + key);
        if (mClient == null) {
            Logger.e(TAG, "client is null");
            return null;
        }
        S3Object object = null;
        try {
            object = mClient.getObject(BUCKET, key);
        } catch (Exception e) {
            Logger.e(TAG, "cannot download: " + e.toString());
        }
        if (object != null) {
            return object.getObjectContent();
        }
        return null;
    }

    public String downloadFromS3AsString(String key) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            InputStream is = downloadFromS3(key);
            if (is != null) {
                int count = 0;
                byte[] buffer = new byte[2048];
                while ((count = is.read(buffer)) > 0) {
                    baos.write(buffer, 0, count);
                }
                return new String(baos.toByteArray());
            }
        } catch (IOException e) {
            Logger.e(TAG, "cannot upload: " + e.toString());
        }
        return null;
    }

    public void downloadFromS3AsFile(String key, String path) {
        try {
            FileOutputStream fos = new FileOutputStream(path);
            InputStream is = downloadFromS3(key);
            if (is != null) {
                FileHelper.copyFile(is, fos);
            }
        } catch (IOException e) {
            Logger.e(TAG, e.toString(), e);
        }
    }

    public String uploadToS3(File file, String key, boolean publicRead) {
        Logger.d(TAG, "Upload to s3: " + key + ", " + file);
        if (mClient == null) {
            Logger.e(TAG, "client is null");
            return "Client is null";
        }
        if (!file.exists()) {
            Logger.e(TAG, "File not exists: " + file);
            return "File not exists";
        }
        try {
            PutObjectRequest request = new PutObjectRequest(BUCKET, addPrefix(key), file);
            if (publicRead) {
                request.setCannedAcl(CannedAccessControlList.PublicRead);
            }
            PutObjectResult result = mClient.putObject(request);
            Logger.i(TAG, "upload to s3 success: " + result.getETag());
            return "https://" + BUCKET + "." + mClient.getEndpoint() + "/" + addPrefix(key);
        } catch (Exception e) {
            Logger.w(TAG, "upload to s3 failed: " + e.toString());
            return "upload to s3 failed: " + e.toString();
        }
    }

    public String uploadToS3(String fullPath, String key, boolean publicRead) {
        return uploadToS3(new File(fullPath), key, publicRead);
    }


    public List<String> listS3Object(String prefix) {
        if (mClient == null) {
            Logger.e(TAG, "client is null");
            return null;
        }
        ObjectListing list = mClient.listObjects(BUCKET, prefix);
        List<String> summaryList = new ArrayList<>();
        for (S3ObjectSummary objectSummary : list.getObjectSummaries()) {
            summaryList.add(objectSummary.toString());
        }
        return summaryList;
    }

    public List<String> listS3Key(String prefix) {
        if (mClient == null) {
            Logger.e(TAG, "client is null");
            return null;
        }
        ObjectListing list = mClient.listObjects(BUCKET, prefix);
        List<String> keyList = new ArrayList<>();
        for (S3ObjectSummary objectSummary : list.getObjectSummaries()) {
            keyList.add(objectSummary.getKey());
        }
        return keyList;
    }

    private static String addPrefix(String key) {
        return KEY_PREFIX + "/" + DeviceInfoManager.getInstance().getSerial() + "/" + key;
    }
}
