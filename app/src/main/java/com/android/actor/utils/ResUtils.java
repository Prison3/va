package com.android.actor.utils;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;

import com.android.actor.ActApp;
import com.android.actor.control.RocketComponent;
import com.android.actor.monitor.Logger;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.constants.GpsTagConstants;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.DelegateFileFilter;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.function.BiFunction;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ResUtils {

    private static final String TAG = ResUtils.class.getSimpleName();

    public static String readAsset(String asset) throws Throwable {
        InputStream input = ActApp.getInstance().getAssets().open(asset);
        String str = IOUtils.toString(input);
        input.close();
        return str;
    }

    public static List<String> listLAssets(String _path) throws Throwable {
        return listAssets(RocketComponent.PKG_NEW_STAGE, _path);
    }

    public static List<String> listAssets(String packageName, String _path) throws Throwable {
        String path = "assets/" + _path;
        Logger.d(TAG, "List asset " + path);
        List<String> list = new ArrayList<>();
        traverseAsset(packageName, (zip, entry) -> {
            String name = entry.getName();
            if (name.startsWith(path + "/")) {
                list.add(name.substring(path.length() + 1));
            }
            return false;
        });
        if (list.size() == 0) {
            throw new Exception("No asset dir " + path);
        }
        return list;
    }

    public static void traverseAsset(String packageName, BiFunction<ZipFile, ZipEntry, Boolean> process) throws Throwable {
        PackageManager packageManager = ActApp.getInstance().getPackageManager();
        ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
        ZipFile zip = new ZipFile(info.sourceDir);
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            if (process.apply(zip, entry)) {
                break;
            }
        }
        zip.close();
    }

    public static void extractAssetToFileIfNotExist(String asset, String to) throws IOException {
        File file = new File(to);
        if (!file.exists()) {
            File tmp = new File(to + ".tmp");
            if (tmp.exists()) {
                FileUtils.forceDelete(tmp);
            }
            InputStream inputStream = ActApp.getInstance().getAssets().open(asset);
            FileUtils.copyInputStreamToFile(inputStream, tmp);
            FileUtils.moveFile(tmp, file);
        }
    }

    public static boolean extractAsset(String name, String toPath) throws Throwable {
        InputStream input = ActApp.getInstance().getAssets().open(name);
        byte[] bytes = IOUtils.toByteArray(input);
        input.close();
        boolean update = true;

        File file = new File(toPath);
        if (file.exists()) {
            byte[] existBytes = FileUtils.readFileToByteArray(file);
            update = !Arrays.equals(bytes, existBytes);
        }
        if (update) {
            Logger.i(TAG, "Extract new " + name + ", len " + bytes.length + ", to " + toPath);
            File tmpFile = new File(toPath + ".tmp");
            FileUtils.writeByteArrayToFile(tmpFile, bytes);
            if (!tmpFile.setExecutable(true)) {
                throw new RuntimeException("Failed to set executable.");
            }
            if (!tmpFile.renameTo(file)) {
                throw new RuntimeException("Failed to rename.");
            }
        }
        return update;
    }

    public static boolean extractAsset(String name, String toPath, String... formatArgs) throws Throwable {
        InputStream input = ActApp.getInstance().getAssets().open(name);
        String content = String.format(IOUtils.toString(input), formatArgs);
        input.close();
        boolean update = true;

        File file = new File(toPath);
        if (file.exists()) {
            String existContent = FileUtils.readFileToString(file);
            update = !content.equals(existContent);
        }
        if (update) {
            Logger.i(TAG, "Extract new " + name + ", len " + content.length() + ", to " + toPath);
            File tmpFile = new File(toPath + ".tmp");
            FileUtils.writeStringToFile(tmpFile, content);
            if (!tmpFile.renameTo(file)) {
                throw new RuntimeException("Failed to rename.");
            }
        }
        return update;
    }

    public static void updateMedia(String path) {
        Logger.d(TAG, "updateMedia: "+ path);
        List<File> files = new ArrayList<>();
        File file = new File(path);
        if (file.isFile()) {
            files.add(file);
        } else {
            files.addAll(FileUtils.listFiles(file, new DelegateFileFilter((dir, name) -> {
                name = name.toLowerCase();
                if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                    return true;
                }
                return false;
            }), TrueFileFilter.INSTANCE));
        }

        for (File f : files) {
            try {
                Logger.d(TAG, "Check " + f);
                ImageMetadata metadata = Imaging.getMetadata(f);
                if (metadata instanceof JpegImageMetadata) {
                    JpegImageMetadata jpegMetadata = (JpegImageMetadata) metadata;
                    if (jpegMetadata.findEXIFValue(GpsTagConstants.GPS_TAG_GPS_LATITUDE) != null) {
                        Logger.v(TAG, "Strip GPS info from " + f);
                        File tmpFile = new File(f.getPath() + ".tmp");
                        if (tmpFile.exists()) {
                            FileUtils.forceDelete(tmpFile);
                        }
                        FileOutputStream out = new FileOutputStream(tmpFile);
                        new ExifRewriter().removeExifMetadata(f, out);
                        if (!tmpFile.renameTo(f)) {
                            throw new Exception("Can't rename " + tmpFile + " to " + f);
                        }
                    }
                }
            } catch (Throwable e) {
                Logger.e(TAG, "Exception on exif check " + f, e);
            }
        }

        MediaScannerConnection.scanFile(ActApp.getInstance(),
                new String[]{path},
                null,
                (path1, uri) -> Logger.d(TAG, "onScanCompleted " + path1 + " " + uri));
    }
}
