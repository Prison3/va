// IJobManagerService.aidl
package com.android.va.system;

import android.content.Intent;
import android.content.ComponentName;
import android.os.IBinder;
import java.lang.String;
import android.app.job.JobInfo;
import com.android.va.model.JobRecord;

interface IJobManagerService {
    JobInfo schedule(in JobInfo info, int userId);
    JobRecord queryJobRecord(String processName, int jobId, int userId);
    void cancelAll(String processName, int userId);
    int cancel(String processName, int jobId, int userId);

}
