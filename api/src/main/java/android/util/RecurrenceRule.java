package android.util;

import android.os.Parcel;
import android.os.Parcelable;

import java.time.ZonedDateTime;

public class RecurrenceRule implements Parcelable {

    public ZonedDateTime start;

    protected RecurrenceRule(Parcel in) {
    }

    public static final Creator<RecurrenceRule> CREATOR = new Creator<RecurrenceRule>() {
        @Override
        public RecurrenceRule createFromParcel(Parcel in) {
            return new RecurrenceRule(in);
        }

        @Override
        public RecurrenceRule[] newArray(int size) {
            return new RecurrenceRule[size];
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
