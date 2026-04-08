package com.android.actor;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.actor.monitor.Logger;
import com.android.actor.utils.shell.Libsu;

public class LaunchActivity extends Activity {

    private static final String TAG = "LaunchActivity";
    private TextView mTextView;
    private Runnable mAcquireRootRunnable = new Runnable() {
        @Override
        public void run() {
           // if (Root.acquireRoot() && Libsu.isReady()) {
                log("Got root, goto MainActivity.");
                ActApp.removeCallbacks(this);
                //GlobalChain.setup();
//                try {
//                    if (!Libsu.exists("/data/new_stage")) {
//                        Libsu.mkdir("/data/new_stage");
//                    }
//                } catch (IOException e) {
//                    Logger.e(TAG, "Error to mkdir /data/new_stage", e);
//                }
               // DeviceNumber.setAsSerial();
                startActivity(new Intent(LaunchActivity.this, MainActivity.class));
                finish();
                Libsu.exec("svc power stayon true");
                Libsu.exec("dumpsys deviceidle whitelist +com.android.alpha");
//            } else {
//                Toast.makeText(LaunchActivity.this, "Wait root.", Toast.LENGTH_SHORT).show();
//                ActApp.postDelayed(this, 5000);
//            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.d(TAG, "onCreate.");
        mTextView = new TextView(this);
        setContentView(R.layout.activity_main);
        Libsu.connectService();
        ActApp.post(mAcquireRootRunnable);
    }

    private void log(String msg) {
        Logger.v(TAG, msg);
        mTextView.setText(mTextView.getText() + "\n" + msg);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Logger.d(TAG, "onDestroy");
    }
}
