package com.android.va.hook;

import java.lang.reflect.Method;

import com.android.va.base.PrisonCore;
import com.android.va.runtime.VActivityThread;

/**
 * Created by Prison on 2022/3/5.
 */
public class UidMethodProxy extends MethodHook {
    private final int index;
    private final String name;

    public UidMethodProxy(String name, int index) {
        this.index = index;
        this.name = name;
    }

    @Override
    protected String getMethodName() {
        return name;
    }

    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
        int uid = (int) args[index];
        if (uid == VActivityThread.getBoundUid()) {
            args[index] = PrisonCore.getUid();
        }
        return method.invoke(who, args);
    }
}
