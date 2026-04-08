package com.android.actor.utils.db;

import androidx.annotation.NonNull;

public class TUnit {
    public long rsv;
    public long snd;
    public int time;

    public TUnit(long rsv, long snd, int time) {
        this.rsv = rsv;
        this.snd = snd;
        this.time = time;
    }

    public boolean decrease(TUnit next) {
        return next.time >= this.time && (next.rsv < this.rsv || next.snd < this.snd);
    }

    @NonNull
    @Override
    public String toString() {
        return "{" + rsv + ", " + snd + ", " + time+ "}";
    }
}
