package com.android.actor.script.lua;

import com.android.actor.ActApp;
import com.android.actor.utils.downloader.DownloadManager;
import com.android.actor.monitor.Logger;
import com.android.actor.utils.BlockingReference;
import com.android.actor.utils.FileHelper;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class LuaIO {

    private static final String TAG = LuaIO.class.getSimpleName();
    private static File sDir = ActApp.getInstance().getExternalFilesDir("lua_io");

    public DataFileWriter openFile(String name, boolean append) throws FileNotFoundException {
        return new DataFileWriter(name, append);
    }

    public void writeBytesToFile(String name, byte[] bytes) throws IOException {
        File file = new File(sDir, name);
        FileUtils.writeByteArrayToFile(file, bytes);
    }

    public String readFileString(String name) throws IOException {
        DataFileReader reader = new DataFileReader(name);
        return reader.read();
    }

    public byte[] readFileBytes(String name) throws IOException {
        return FileUtils.readFileToByteArray(new File(sDir, name));
    }

    public LuaTable unzip(String fileName) {
        LuaTable table = new LuaTable();
        try {
            List<String> outputs = FileHelper.unzipFile(fileName);
            for (int i = 0; i < outputs.size(); ++i) {
                table.insert(i + 1, CoerceJavaToLua.coerce(outputs.get(i)));
            }
        } catch (IOException e) {
            Logger.e(TAG, e.toString(), e);
        }
        return table;
    }

    public LuaTable getPriority(String path) {
        File file = new File(path);
        LuaTable table = new LuaTable();
        table.set("r", CoerceJavaToLua.coerce(file.canRead()));
        table.set("w", CoerceJavaToLua.coerce(file.canWrite()));
        table.set("x", CoerceJavaToLua.coerce(file.canExecute()));
        return table;
    }

    public void setPriority(String path, LuaTable table) {
        File file = new File(path);
        file.setReadable(table.get("r").toboolean(), false);
        file.setWritable(table.get("w").toboolean(), false);
        file.setExecutable(table.get("x").toboolean(), false);
    }

    public String downloadFromUrl(String url, String path) throws InterruptedException {
        BlockingReference<File> ref = new BlockingReference<>();
        String folder = null, filename = null;
        if (path != null) {
            if (path.endsWith("/")) {
                folder = path;
            } else {
                folder = FilenameUtils.getFullPath(path);
                filename = FilenameUtils.getName(path);
            }
        }
        File file = DownloadManager.instance().getFile(url, folder, filename);
        if (file == null) {
            DownloadManager.instance().addDownload(url, folder, filename, (_url, _file, reason) -> {
                ref.put(_file);
            });
        } else {
            ref.put(file);
        }
        file = ref.take();
        return file != null ? file.getPath() : null;
    }

    public LuaTable listFile(String path) {
        File file = new File(path);
        if (!file.isDirectory()) {
            Logger.e(TAG, "listFile " + path + " not directory.");
            return null;
        }
        File[] files = file.listFiles();
        LuaTable table = new LuaTable();
        int index = 0;
        for (File value : files) {
            String name = value.getName();
            if (!name.startsWith(".")) {
                table.insert(index, CoerceJavaToLua.coerce(name));
                index = index + 1;
            }
        }
        return table;
    }

    public String listFileLatest(String path) {
        File file = new File(path);
        if (!file.isDirectory()) {
            Logger.e(TAG, "listFile " + path + " not directory.");
            return null;
        }
        File[] files = file.listFiles();
        // 使用匿名内部类创建一个自定义的 Comparator 来按照最后修改时间排序
        if (files == null || files.length == 0) {
            return null;
        }else {
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File file1, File file2) {
                    long lastModified1 = file1.lastModified();
                    long lastModified2 = file2.lastModified();

                    // file1 在 file2 之后
                    // 最后修改时间相同
                    return Long.compare(lastModified1, lastModified2); // file1 在 file2 之前
                }
            });
        }
        Logger.i(TAG, "listFileLatest " + files[files.length - 1].getName());
        return files[files.length - 1].getName();
    }

    public boolean moveFile(String fromPath, String toPath) {
        Logger.i(TAG, "moveFile " + fromPath + " to " + toPath);
        File fromFile = new File(fromPath);
        File toFile = new File(toPath);
        if (!fromFile.exists()) {
            Logger.e(TAG, "moveFile fromFile not exist, " + fromPath);
            return false;
        }
        if (fromFile.isDirectory() && !toPath.endsWith("/")) {
            Logger.e(TAG, "moveFile can't move folder to file.");
            return false;
        }
        if (toPath.endsWith("/")) {
            toFile = new File(toFile, fromFile.getName());
        }
        return fromFile.renameTo(toFile);
    }

    public boolean deleteFile(String path) throws IOException {
        Logger.i(TAG, "deleteFile " + path);
        File file = new File(path);
        if (!file.exists()) {
            return true;
        }
        Path filePath = file.toPath().toRealPath();
        Path dataPath = ActApp.getInstance().getDataDir().toPath().toRealPath();
        if (filePath.equals(dataPath) || !filePath.startsWith(dataPath)) {
            Logger.w(TAG, "deleteFile " + path + " is not allowed.");
            return false;
        }
        return FileUtils.deleteQuietly(new File(path));
    }

    public static class DataFileWriter {
        private final File mFile;
        private final FileOutputStream mOutput;

        public DataFileWriter(String name, boolean append) throws FileNotFoundException {
            mFile = new File(sDir, name);
            mOutput = new FileOutputStream(mFile, append);
        }

        public void appendText(String text) throws IOException {
            IOUtils.write(text, mOutput);
        }

        public void appendTextLine(String text) throws IOException {
            IOUtils.write(text + '\n', mOutput);
        }

        public void close() throws IOException {
            mOutput.close();
        }
    }

    public static class DataFileReader {
        private final File mFile;
        private final FileInputStream mInput;

        public DataFileReader(String name) throws FileNotFoundException {
            mFile = new File(sDir, name);
            mInput = new FileInputStream(mFile);
        }

        public String read() throws IOException {
            return FileUtils.readFileToString(mFile, "UTF-8");
        }

        public void close() throws IOException {
            mInput.close();
        }
    }
}
