package com.android.actor.control.api;

import static android.view.InputDevice.SOURCE_CLASS_POINTER;
import static android.view.InputDevice.SOURCE_MOUSE;
import static android.view.InputDevice.SOURCE_TOUCHSCREEN;

import android.view.InputDevice;
import android.view.MotionEvent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.android.actor.control.ActActivityManager;
import com.android.actor.device.NewStage;
import com.android.actor.utils.ReflectUtils;

import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class MotionEventGenerator {

    private static final float[] PRESSURES = new float[] {
            0.03937008f, 0.047244094f, 0.05511811f, 0.062992126f, 0.07086614f, 0.07874016f, 0.08661418f, 0.09448819f, 0.1023622f, 0.11023622f, 0.11811024f, 0.12598425f, 0.13385826f, 0.14173228f, 0.1496063f, 0.15748031f, 0.16535433f, 0.17322835f, 0.18110237f, 0.18897638f, 0.19685039f, 0.2047244f, 0.21259843f, 0.22047244f, 0.22834645f, 0.23622048f, 0.24409449f
    };
    private static final float[] SIZES = new float[] {
            0.0f, 0.003937008f, 0.007874016f, 0.011811024f, 0.015748031f, 0.01968504f, 0.023622047f, 0.027559055f, 0.031496063f, 0.03543307f, 0.03937008f, 0.04330709f, 0.047244094f
    };
    private static final float[] TOUCH_MAJORS = new float[] {
            0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f, 7.0f
    };
    private static int SCREEN_INPUT_ID = 0;

    static {
        Arrays.stream(InputDevice.getDeviceIds())
                .mapToObj(InputDevice::getDevice)
                .filter(d -> (d.getSources() & InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN)
                .findFirst()
                .ifPresent(d -> {
                    SCREEN_INPUT_ID = d.getId();
                });
    }

    static class ValuePicker {

        float[] values;
        int left = 0;
        int i;

        ValuePicker(float[] values) {
            this.values = values;
        }

        float get() {
            if (left <= 0) {
                left = RandomUtils.nextInt(5, 25);
                i = RandomUtils.nextInt(0, values.length);
            }
            --left;
            return values[i];
        }
    }

    private static ValuePicker sPressurePicker = new ValuePicker(PRESSURES);
    private static ValuePicker sSizePicker = new ValuePicker(SIZES);
    private static ValuePicker sTouchMajorPicker = new ValuePicker(TOUCH_MAJORS);

    public static MotionEvent generate(long downTime, long eventTime, int action,
                                       float x, float y) {
        int deviceId = SCREEN_INPUT_ID;
        String packageName = ActActivityManager.getInstance().getCurrentApp();
        if (!StringUtils.isEmpty(packageName)) {
            //int profileId = ManagedProfiles.instance.getAppProfileId(packageName);
            String str = NewStage.instance().getPackageParameterValue(packageName, "input_devices");
            if (!StringUtils.isEmpty(str)) {
                Optional opt = JSON.parseArray(str).stream()
                        .map(o -> (JSONObject) o)
                        .filter(j -> ((j.getIntValue("sources") & SOURCE_TOUCHSCREEN) == SOURCE_TOUCHSCREEN)
                                && (j.getIntValue("sources") & ~SOURCE_CLASS_POINTER & SOURCE_MOUSE) == 0)
                        .findFirst();
                if (opt.isPresent()) {
                    deviceId = ((JSONObject) opt.get()).getIntValue("id");
                }
            }
        }

        float pressure = sPressurePicker.get();
        float size = sSizePicker.get();
        float touchMajor = sTouchMajorPicker.get();

        MotionEvent.PointerProperties pointerProperties = new MotionEvent.PointerProperties();
        pointerProperties.id = 0;
        pointerProperties.toolType = MotionEvent.TOOL_TYPE_FINGER;

        MotionEvent.PointerCoords pointerCoords = new MotionEvent.PointerCoords();
        pointerCoords.orientation = 0;
        pointerCoords.x = x;
        pointerCoords.y = y;
        pointerCoords.pressure = pressure;
        pointerCoords.size = size;
        pointerCoords.touchMajor = touchMajor;
        pointerCoords.touchMinor = touchMajor;
        pointerCoords.toolMajor = touchMajor;
        pointerCoords.toolMinor = touchMajor;

        /*
            long downTime, long eventTime,
            int action, int pointerCount, PointerProperties[] pointerProperties,
            PointerCoords[] pointerCoords, int metaState, int buttonState,
            float xPrecision, float yPrecision, int deviceId,
            int edgeFlags, int source, int displayId, int flags
         */
        try {
            return (MotionEvent) ReflectUtils.callStaticMethod(MotionEvent.class, "obtain", new Object[] {
                    downTime, eventTime,
                    action, 1, new MotionEvent.PointerProperties[] {pointerProperties},
                    new MotionEvent.PointerCoords[] {pointerCoords}, 0, 0,
                    1f, 1f, deviceId,
                    0, InputDevice.SOURCE_TOUCHSCREEN, 0, 0
            }, new Class[] {
                    long.class, long.class,
                    int.class, int.class, MotionEvent.PointerProperties[].class,
                    MotionEvent.PointerCoords[].class, int.class, int.class,
                    float.class, float.class, int.class,
                    int.class, int.class, int.class, int.class
            });
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        /*return MotionEventHidden.obtain(
                downTime, eventTime,
                action, 1, new MotionEvent.PointerProperties[] {pointerProperties},
                new MotionEvent.PointerCoords[] {pointerCoords}, 0, 0,
                1f, 1f, deviceId,
                0, InputDevice.SOURCE_TOUCHSCREEN, 0, 0
        );*/
    }
}
