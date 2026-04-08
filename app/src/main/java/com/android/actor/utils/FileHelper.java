package com.android.actor.utils;

import com.android.actor.monitor.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FileHelper {
    private final static String TAG = FileHelper.class.getSimpleName();

    public static void copyFile(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = is.read(buffer)) != -1) {
            os.write(buffer, 0, read);
        }
    }

    public static void writeToFile(String path, String body) throws IOException {
        FileOutputStream fos = new FileOutputStream(path);
        byte[] bytes = body.getBytes();
        fos.write(bytes);
        fos.close();
    }

    public static String readFromFile(String path) throws IOException {
        FileInputStream fis = new FileInputStream(path);
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int read;
        while ((read = fis.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        String ret = baos.toString();
        baos.close();
        return ret;
    }

    public static List<String> unzipFile(File zip) throws IOException {
        ZipFile zipFile = new ZipFile(zip);
        Logger.d(TAG, "unzip: " + zip);
        Enumeration<?> e = zipFile.entries();
        List<String> outputs = new ArrayList<>();
        while (e.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) e.nextElement();
            if (entry.isDirectory()) {
                File dir = new File(zip.getParent() + File.separator + entry.getName());
                boolean b = dir.mkdirs();
//                Logger.d(TAG, "create folder " + (b ? "success" : "fail") + ": " + dir);
            } else {
                File targetFile = new File(zip.getParent() + "/" + entry.getName());
                if (!targetFile.getParentFile().exists()) {
                    boolean b = targetFile.getParentFile().mkdirs();
//                    Logger.d(TAG, "create folder " + (b ? "success" : "fail") + ": " + targetFile.getParentFile());
                }
                boolean b = targetFile.createNewFile();
//                Logger.d(TAG, "unzip file " + (b ? "success" : "fail") + ": " + targetFile);
                InputStream is = zipFile.getInputStream(entry);
                FileOutputStream fos = new FileOutputStream(targetFile);
                int len;
                byte[] buf = new byte[2048];
                while ((len = is.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
                fos.close();
                is.close();
                outputs.add(targetFile.getAbsolutePath());
            }
        }
        zipFile.close();
        return outputs;
    }

    public static List<String> unzipFile(String fileName) throws IOException {
        File file = new File(fileName);
        if (file.exists()) {
            return unzipFile(file);
        }
        throw new IOException("file not exists");
    }
}