package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.RecurrenceRule;

public class NetworkPolicy implements Parcelable {

    public RecurrenceRule cycleRule;
    public long warningBytes;
    public boolean metered;
    public boolean inferred;

    protected NetworkPolicy(Parcel in) {
    }

    public boolean hasCycle() {
        return false;
    }

    public static final Creator<NetworkPolicy> CREATOR = new Creator<NetworkPolicy>() {
        @Override
        public NetworkPolicy createFromParcel(Parcel in) {
            return new NetworkPolicy(in);
        }

        @Override
        public NetworkPolicy[] newArray(int size) {
            return new NetworkPolicy[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
    }
}
