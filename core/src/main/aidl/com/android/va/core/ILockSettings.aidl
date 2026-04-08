package com.android.va.core;

interface ILockSettings {
    void setRecoverySecretTypes(in int[] secretTypes);
    int[] getRecoverySecretTypes();
}