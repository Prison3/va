rpc.exports = {
    init(stage, parameters) {
        parameters = JSON.stringify(parameters);
        console.log("parameters " + parameters);
        parameters = JSON.parse(parameters);
        var apkPath = parameters['apkPath'];
        loadInJava(apkPath);
    },
    dispose() {
        console.log('[dispose]');
    }
};

function loadInJava(apkPath) {
    Java.perform(function() {
        Java.use("android.util.Log").i("Rocket_Actor", "Load my dex.");
        try {
            loadDex(apkPath);
        } catch (err) {
            console.log(err);
        }
    }, 0);
}

function loadDex(apkPath) {
    const ActivityThread = Java.use('android.app.ActivityThread');
    var currentApplication = null;
    while (currentApplication == null) {
        Thread.sleep(1);
        currentApplication = ActivityThread.currentApplication();
        console.log("currentApplication", currentApplication);
    }
    
    //const pkgManager = currentApplication.getPackageManager();
    //const appInfo = pkgManager.getApplicationInfo("com.android.alpha", 0);
    //const apkPath = appInfo.sourceDir;
    console.log("apkPath", apkPath);

    var myDex = Java.openClassFile(apkPath);
    console.log("myDex:", myDex);
    myDex.load();
    var fiEntryPoint = Java.use("com.android.actor.fi.FIEntryPoint");
    console.log("fiEntryPoint " + fiEntryPoint);
    fiEntryPoint.main();
}
