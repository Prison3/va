package com.android.internal.aidl.service;

public abstract class ICode {
    public abstract int getCode();

    public abstract String getReason();

    public static ICode newCode(int code, String reason) {
        return new ICode() {
            @Override
            public int getCode() {
                return code;
            }

            @Override
            public String getReason() {
                return reason;
            }
        };
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ICode && getCode() == ((ICode) obj).getCode();
    }
}
