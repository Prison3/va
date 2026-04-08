package com.android.actor.control;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import com.android.actor.ActApp;

public class ActClipboardManager {
    private final static String TAG = ActPackageManager.class.getSimpleName();
    private static ActClipboardManager sInstance;
    private final Context mContext;
    ClipboardManager mClipboardManager;
    public static ActClipboardManager getInstance() {
        if (sInstance == null) {
            synchronized (ActPackageManager.class) {
                if (sInstance == null) {
                    sInstance = new ActClipboardManager(ActApp.getInstance());
                }
            }
        }
        return sInstance;
    }

    private ActClipboardManager(Context context) {
        mContext = context;
        mClipboardManager =  (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    /**
     * 复制
     * @param data    内容
     */
    public  void copy(String data) {
        ClipData clipData = ClipData.newPlainText(null, data);
        mClipboardManager.setPrimaryClip(clipData);
    }

    /**
     * 粘贴
     */
    public String  paste() {
        ClipData clipData = mClipboardManager.getPrimaryClip();
        if (clipData != null && clipData.getItemCount() > 0) {
            CharSequence text = clipData.getItemAt(0).getText();
            return text.toString();
        }
        return "";
    }

    public void clear(){
        mClipboardManager.clearPrimaryClip();
    }
}
