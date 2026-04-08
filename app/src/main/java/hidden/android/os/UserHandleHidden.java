package hidden.android.os;

import android.os.UserHandle;

import com.android.actor.utils.ReflectUtils;

public class UserHandleHidden {

    public static UserHandle of(int userId) {
        try {
            return (UserHandle) ReflectUtils.callStaticMethod(UserHandle.class, "of", new Object[] {
                    userId
            }, new Class[] {
                    int.class
            });
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
