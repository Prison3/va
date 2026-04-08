package com.android.internal;

interface ILibsuService {
    int getPid();
    int getUid();
    String getUUID();
    IBinder getFileSystemService();
    int getPathUid(String path);
    void startTethering();
    void stopTethering();
}