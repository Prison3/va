package com.android.va.system;

import android.accounts.Account;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Prison on 2022/3/3.
 */
public class VUserAccounts implements Parcelable {
    public final Object lock = new Object();

    public int userId;
    public List<VAccount> accounts = new ArrayList<>();

    public Account[] toAccounts() {
        List<Account> local = new ArrayList<>();
        for (VAccount account : accounts) {
            local.add(account.account);
        }
        return local.toArray(new Account[]{});
    }

    public VAccount addAccount(Account account) {
        VAccount vAccount = new VAccount();
        vAccount.account = account;
        accounts.add(vAccount);
        return vAccount;
    }

    public VAccount getAccount(Account account) {
        for (VAccount vAccount : accounts) {
            if (vAccount.isMatch(account))
                return vAccount;
        }
        return null;
    }

    public boolean delAccount(Account account) {
        VAccount vAccount = getAccount(account);
        return accounts.remove(vAccount);
    }


    public Map<String, Integer> getVisibility(Account account) {
        VAccount vAccount = getAccount(account);
        if (vAccount == null)
            return new HashMap<>();
        return vAccount.visibility;
    }

    public Map<String, String> getAccountUserData(Account account) {
        VAccount vAccount = getAccount(account);
        if (vAccount == null)
            return new HashMap<>();
        return vAccount.accountUserData;
    }

    public Map<String, String> getAuthToken(Account account) {
        VAccount vAccount = getAccount(account);
        if (vAccount == null)
            return new HashMap<>();
        return vAccount.authTokens;
    }

    public Account[] getAccountsByType(String type) {
        List<Account> local = new ArrayList<>();
        for (VAccount account : accounts) {
            if (account.account.type.equals(type)) {
                local.add(account.account);
            }
        }
        return local.toArray(new Account[]{});
    }

    public void updateLastAuthenticatedTime(Account account) {
        VAccount vAccount = getAccount(account);
        if (vAccount != null) {
            vAccount.updateLastAuthenticatedTime = System.currentTimeMillis();
        }
    }

    public long findAccountLastAuthenticatedTime(Account account) {
        VAccount vAccount = getAccount(account);
        if (vAccount != null) {
            return vAccount.updateLastAuthenticatedTime;
        }
        return -1;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.userId);
        dest.writeTypedList(this.accounts);
    }

    public void readFromParcel(Parcel source) {
        this.userId = source.readInt();
        this.accounts = source.createTypedArrayList(VAccount.CREATOR);
    }

    public VUserAccounts() {
    }

    protected VUserAccounts(Parcel in) {
        this.userId = in.readInt();
        this.accounts = in.createTypedArrayList(VAccount.CREATOR);
    }

    public static final Creator<VUserAccounts> CREATOR = new Creator<VUserAccounts>() {
        @Override
        public VUserAccounts createFromParcel(Parcel source) {
            return new VUserAccounts(source);
        }

        @Override
        public VUserAccounts[] newArray(int size) {
            return new VUserAccounts[size];
        }
    };
}
