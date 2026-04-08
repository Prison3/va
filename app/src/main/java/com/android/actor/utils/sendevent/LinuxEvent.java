package com.android.actor.utils.sendevent;

public class LinuxEvent {
    public static int EV_SYN = 0;
    public static int EV_ABS = 3;
    public static int SYN_REPORT = 0;
    public static int ABS_MT_SLOT = 47;
    public static int ABS_MT_TOUCH_MAJOR = 48;
    public static int ABS_MT_POSITION_X = 53;
    public static int ABS_MT_POSITION_Y = 54;
    public static int ABS_MT_TRACKING_ID = 57;
    public static int ABS_MT_PRESSURE = 58;
    public static String EVENT_PATH = "/dev/input/";

    /**
     * usage: sendevent DEVICE TYPE CODE VALUE
     * event reference: http://source.android.com/devices/input/touch-devices.html
     *
     * @param device event1, event2... (ls /dev/input/)
     * @return 'sendevent /dev/input/DEVICE TYPE CODE VALUE'
     */
    public static String sendEventStr(String device, int type, int code, int value) {
        return "sendevent " + EVENT_PATH + device + " " + type + " " + code + " " + value;
    }
}
