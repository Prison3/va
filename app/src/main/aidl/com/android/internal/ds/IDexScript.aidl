package com.android.internal.ds;
import com.android.internal.ds.IDexDriver;
import com.android.internal.ds.IDexActivity;
import com.android.internal.ds.IDexPackage;
import com.android.internal.ds.IDexAI;
import com.android.internal.ds.IDexCallback;

interface IDexScript {

    IDexCallback setup(IDexDriver driver, IDexActivity activity, IDexPackage package_, IDexAI ai);

    String run(String name, in Map<String, String> params);
}