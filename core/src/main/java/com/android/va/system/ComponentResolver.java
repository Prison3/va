package com.android.va.system;

import com.android.va.model.VPackageSettings;

import android.content.ComponentName;
import com.android.va.utils.Logger;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.util.ArrayMap;

import java.util.ArrayList;
import java.util.List;


public class ComponentResolver {
    public static final String TAG = ComponentResolver.class.getSimpleName();

    private final Object mLock = new Object();

    /**
     * All available activities, for your resolving pleasure.
     */
    private final ActivityIntentResolver mActivities = new ActivityIntentResolver();

    /**
     * All available providers, for your resolving pleasure.
     */
    private final ProviderIntentResolver mProviders = new ProviderIntentResolver();

    /**
     * All available receivers, for your resolving pleasure.
     */
    private final ActivityIntentResolver mReceivers = new ActivityIntentResolver();

    /**
     * All available services, for your resolving pleasure.
     */
    private final ServiceIntentResolver mServices = new ServiceIntentResolver();
    /**
     * Mapping from provider authority [first directory in content URI codePath) to provider.
     */
    private final ArrayMap<String, VPackageInfo.Provider> mProvidersByAuthority = new ArrayMap<>();

    public ComponentResolver() {
    }

    void addAllComponents(VPackageInfo pkg) {
        final ArrayList<VPackageInfo.ActivityIntentInfo> newIntents = new ArrayList<>();
        synchronized (mLock) {
            addActivitiesLocked(pkg, newIntents);
            addServicesLocked(pkg);
            addProvidersLocked(pkg);
            addReceiversLocked(pkg);
        }
    }

    void removeAllComponents(VPackageInfo pkg) {
        synchronized (mLock) {
            removeAllComponentsLocked(pkg);
        }
    }

    private void removeAllComponentsLocked(VPackageInfo pkg) {
        int componentSize;
        StringBuilder r;
        int i;

        componentSize = pkg.activities.size();
        r = null;
        for (i = 0; i < componentSize; i++) {
            VPackageInfo.Activity a = pkg.activities.get(i);
            mActivities.removeActivity(a, "activity");
        }
        componentSize = pkg.providers.size();
        r = null;
        for (i = 0; i < componentSize; i++) {
            VPackageInfo.Provider p = pkg.providers.get(i);
            mProviders.removeProvider(p);
            if (p.info.authority == null) {
                // Another content provider with this authority existed when this app was
                // installed, so this authority is null. Ignore it as we don't have to
                // unregister the provider.
                continue;
            }
            String[] names = p.info.authority.split(";");
            for (int j = 0; j < names.length; j++) {
                if (mProvidersByAuthority.get(names[j]) == p) {
                    mProvidersByAuthority.remove(names[j]);
                }
            }
            mProvidersByAuthority.remove(p.info.authority);
        }

        componentSize = pkg.receivers.size();
        r = null;
        for (i = 0; i < componentSize; i++) {
            VPackageInfo.Activity a = pkg.receivers.get(i);
            mReceivers.removeActivity(a, "receiver");
        }

        componentSize = pkg.services.size();
        r = null;
        for (i = 0; i < componentSize; i++) {
            VPackageInfo.Service s = pkg.services.get(i);
            mServices.removeService(s);
        }
    }

    private void addActivitiesLocked(VPackageInfo pkg,
                                     List<VPackageInfo.ActivityIntentInfo> newIntents) {
        final int activitiesSize = pkg.activities.size();
        for (int i = 0; i < activitiesSize; i++) {
            VPackageInfo.Activity a = pkg.activities.get(i);
            a.info.processName =
                    PackageManagerService.fixProcessName(pkg.applicationInfo.processName, a.info.processName);
            mActivities.addActivity(a, "activity", newIntents);
        }
    }

    private void addProvidersLocked(VPackageInfo pkg) {
        final int providersSize = pkg.providers.size();
        for (int i = 0; i < providersSize; i++) {
            VPackageInfo.Provider p = pkg.providers.get(i);
            p.info.processName = PackageManagerService.fixProcessName(pkg.applicationInfo.processName,
                    p.info.processName);
            mProviders.addProvider(p);
            if (p.info.authority != null) {
                String[] names = p.info.authority.split(";");
                p.info.authority = null;
                for (String name : names) {
                    if (!mProvidersByAuthority.containsKey(name)) {
                        mProvidersByAuthority.put(name, p);
                        if (p.info.authority == null) {
                            p.info.authority = name;
                        } else {
                            p.info.authority = p.info.authority + ";" + name;
                        }
                    } else {
                        final VPackageInfo.Provider other =
                                mProvidersByAuthority.get(name);
                        final ComponentName component =
                                (other != null && other.getComponentName() != null)
                                        ? other.getComponentName() : null;
                        final String packageName =
                                component != null ? component.getPackageName() : "?";
                        Logger.w(TAG, "Skipping provider name " + name
                                + " (in package " + pkg.applicationInfo.packageName + ")"
                                + ": name already used by " + packageName);
                    }
                }
            }
        }
    }

    private void addReceiversLocked(VPackageInfo pkg) {
        final int receiversSize = pkg.receivers.size();
        for (int i = 0; i < receiversSize; i++) {
            VPackageInfo.Activity a = pkg.receivers.get(i);
            a.info.processName = PackageManagerService.fixProcessName(pkg.applicationInfo.processName,
                    a.info.processName);
            mReceivers.addActivity(a, "receiver", null);
        }
    }

    private void addServicesLocked(VPackageInfo pkg) {
        final int servicesSize = pkg.services.size();
        for (int i = 0; i < servicesSize; i++) {
            VPackageInfo.Service s = pkg.services.get(i);
            s.info.processName = PackageManagerService.fixProcessName(pkg.applicationInfo.processName,
                    s.info.processName);
            mServices.addService(s);
        }
    }


    /**
     * Returns the given activity
     */
    VPackageInfo.Activity getActivity(ComponentName component) {
        synchronized (mLock) {
            return mActivities.mActivities.get(component);
        }
    }

    /**
     * Returns the given provider
     */
    VPackageInfo.Provider getProvider(ComponentName component) {
        synchronized (mLock) {
            return mProviders.mProviders.get(component);
        }
    }

    /**
     * Returns the given receiver
     */
    VPackageInfo.Activity getReceiver(ComponentName component) {
        synchronized (mLock) {
            return mReceivers.mActivities.get(component);
        }
    }

    /**
     * Returns the given service
     */
    VPackageInfo.Service getService(ComponentName component) {
        synchronized (mLock) {
            return mServices.mServices.get(component);
        }
    }

    List<ResolveInfo> queryActivities(Intent intent, String resolvedType, int flags, int userId) {
        synchronized (mLock) {
            return mActivities.queryIntent(intent, resolvedType, flags, userId);
        }
    }

    List<ResolveInfo> queryActivities(Intent intent, String resolvedType, int flags,
                                      List<VPackageInfo.Activity> activities, int userId) {
        synchronized (mLock) {
            return mActivities.queryIntentForPackage(
                    intent, resolvedType, flags, activities, userId);
        }
    }

    List<ResolveInfo> queryProviders(Intent intent, String resolvedType, int flags, int userId) {
        synchronized (mLock) {
            return mProviders.queryIntent(intent, resolvedType, flags, userId);
        }
    }

    List<ResolveInfo> queryProviders(Intent intent, String resolvedType, int flags,
                                     List<VPackageInfo.Provider> providers, int userId) {
        synchronized (mLock) {
            return mProviders.queryIntentForPackage(intent, resolvedType, flags, providers, userId);
        }
    }

    List<ProviderInfo> queryProviders(String processName, String metaDataKey, int flags,
                                      int userId) {
        List<ProviderInfo> providerList = new ArrayList<>();
        synchronized (mLock) {
            for (int i = mProviders.mProviders.size() - 1; i >= 0; --i) {
                final VPackageInfo.Provider p = mProviders.mProviders.valueAt(i);
                final VPackageSettings ps = (VPackageSettings) p.owner.mExtras;
                if (ps == null) {
                    continue;
                }
                if (p.info.authority == null) {
                    continue;
                }
                if (processName != null && (!p.info.processName.equals(processName))) {
                    continue;
                }
                // See PM.queryContentProviders()'s javadoc for why we have the metaData parameter.
                if (metaDataKey != null
                        && (p.metaData == null || !p.metaData.containsKey(metaDataKey))) {
                    continue;
                }
                final ProviderInfo info = PackageManagerCompat.generateProviderInfo(p, flags, ps.readUserState(userId), userId);
                if (info == null) {
                    continue;
                }
//                if (providerList == null) {
//                    providerList = new ArrayList<>(i + 1);
//                }
                providerList.add(info);
            }
        }
        return providerList;
    }

    ProviderInfo queryProvider(String authority, int flags, int userId) {
        synchronized (mLock) {
            final VPackageInfo.Provider p = mProvidersByAuthority.get(authority);
            if (p == null) {
                return null;
            }
            VPackageSettings ps = p.owner.mExtras;
            return PackageManagerCompat.generateProviderInfo(p, flags, ps.readUserState(userId), userId);
        }
    }

    List<ResolveInfo> queryReceivers(Intent intent, String resolvedType, int flags, int userId) {
        synchronized (mLock) {
            return mReceivers.queryIntent(intent, resolvedType, flags, userId);
        }
    }

    List<ResolveInfo> queryReceivers(Intent intent, String resolvedType, int flags,
                                     List<VPackageInfo.Activity> receivers, int userId) {
        synchronized (mLock) {
            return mReceivers.queryIntentForPackage(intent, resolvedType, flags, receivers, userId);
        }
    }

    List<ResolveInfo> queryServices(Intent intent, String resolvedType, int flags, int userId) {
        synchronized (mLock) {
            return mServices.queryIntent(intent, resolvedType, flags, userId);
        }
    }

    List<ResolveInfo> queryServices(Intent intent, String resolvedType, int flags,
                                    List<VPackageInfo.Service> services, int userId) {
        synchronized (mLock) {
            return mServices.queryIntentForPackage(intent, resolvedType, flags, services, userId);
        }
    }


    private static final class ServiceIntentResolver extends IntentResolver<VPackageInfo.ServiceIntentInfo, ResolveInfo> {

        @Override
        public List<ResolveInfo> queryIntent(Intent intent, String resolvedType,
                                             boolean defaultOnly, int userId) {
            mFlags = defaultOnly ? PackageManager.MATCH_DEFAULT_ONLY : 0;
            return super.queryIntent(intent, resolvedType, defaultOnly, userId);
        }

        List<ResolveInfo> queryIntent(Intent intent, String resolvedType, int flags,
                                      int userId) {
            mFlags = flags;
            return super.queryIntent(intent, resolvedType,
                    (flags & PackageManager.MATCH_DEFAULT_ONLY) != 0,
                    userId);
        }

        List<ResolveInfo> queryIntentForPackage(Intent intent, String resolvedType,
                                                int flags, List<VPackageInfo.Service> packageServices, int userId) {
            if (packageServices == null) {
                return null;
            }
            mFlags = flags;
            final boolean defaultOnly = (flags & PackageManager.MATCH_DEFAULT_ONLY) != 0;
            final int servicesSize = packageServices.size();
            ArrayList<VPackageInfo.ServiceIntentInfo[]> listCut = new ArrayList<>(servicesSize);

            ArrayList<VPackageInfo.ServiceIntentInfo> intentFilters;
            for (int i = 0; i < servicesSize; ++i) {
                intentFilters = packageServices.get(i).intents;
                if (intentFilters != null && intentFilters.size() > 0) {
                    VPackageInfo.ServiceIntentInfo[] array =
                            new VPackageInfo.ServiceIntentInfo[intentFilters.size()];
                    intentFilters.toArray(array);
                    listCut.add(array);
                }
            }
            return super.queryIntentFromList(intent, resolvedType, defaultOnly, listCut, userId);
        }

        void addService(VPackageInfo.Service s) {
            mServices.put(s.getComponentName(), s);
            final int intentsSize = s.intents.size();
            int j;
            for (j = 0; j < intentsSize; j++) {
                VPackageInfo.ServiceIntentInfo intent = s.intents.get(j);
                addFilter(intent);
            }
        }

        void removeService(VPackageInfo.Service s) {
            mServices.remove(s.getComponentName());
            final int intentsSize = s.intents.size();
            int j;
            for (j = 0; j < intentsSize; j++) {
                VPackageInfo.ServiceIntentInfo intent = s.intents.get(j);
                removeFilter(intent);
            }
        }

        @Override
        protected boolean isPackageForFilter(String packageName,
                                             VPackageInfo.ServiceIntentInfo info) {
            return packageName.equals(info.service.owner.packageName);
        }

        @Override
        protected VPackageInfo.ServiceIntentInfo[] newArray(int size) {
            return new VPackageInfo.ServiceIntentInfo[size];
        }

        @Override
        protected ResolveInfo newResult(VPackageInfo.ServiceIntentInfo filter, int match, int userId) {
            final VPackageInfo.ServiceIntentInfo info = (VPackageInfo.ServiceIntentInfo) filter;
            final VPackageInfo.Service service = info.service;
            VPackageSettings ps = (VPackageSettings) service.owner.mExtras;
            if (ps == null) {
                return null;
            }
            ServiceInfo si = PackageManagerCompat.generateServiceInfo(service, mFlags, ps.readUserState(userId), userId);

            final ResolveInfo res = new ResolveInfo();
            res.serviceInfo = si;
            if ((mFlags & PackageManager.GET_RESOLVED_FILTER) != 0) {
                res.filter = filter.intentFilter;
            }
            res.priority = info.intentFilter.getPriority();
            res.preferredOrder = service.owner.mPreferredOrder;
            res.match = match;
            res.isDefault = info.hasDefault;
            res.labelRes = info.labelRes;
            res.nonLocalizedLabel = info.nonLocalizedLabel;
            res.icon = info.icon;
            return res;
        }

        // Keys are String (activity class name), values are Activity.
        private final ArrayMap<ComponentName, VPackageInfo.Service> mServices = new ArrayMap<>();
        private int mFlags;
    }


    private static final class ActivityIntentResolver extends IntentResolver<VPackageInfo.ActivityIntentInfo, ResolveInfo> {

        @Override
        public List<ResolveInfo> queryIntent(Intent intent, String resolvedType,
                                             boolean defaultOnly, int userId) {
            mFlags = (defaultOnly ? PackageManager.MATCH_DEFAULT_ONLY : 0);
            return super.queryIntent(intent, resolvedType, defaultOnly, userId);
        }

        List<ResolveInfo> queryIntent(Intent intent, String resolvedType, int flags,
                                      int userId) {
            mFlags = flags;
            return super.queryIntent(intent, resolvedType,
                    (flags & PackageManager.MATCH_DEFAULT_ONLY) != 0,
                    userId);
        }

        List<ResolveInfo> queryIntentForPackage(Intent intent, String resolvedType,
                                                int flags, List<VPackageInfo.Activity> packageActivities, int userId) {
            if (packageActivities == null) {
                return null;
            }
            mFlags = flags;
            final boolean defaultOnly = (flags & PackageManager.MATCH_DEFAULT_ONLY) != 0;
            final int activitiesSize = packageActivities.size();
            ArrayList<VPackageInfo.ActivityIntentInfo[]> listCut = new ArrayList<>(activitiesSize);

            ArrayList<VPackageInfo.ActivityIntentInfo> intentFilters;
            for (int i = 0; i < activitiesSize; ++i) {
                intentFilters = packageActivities.get(i).intents;
                if (intentFilters != null && intentFilters.size() > 0) {
                    VPackageInfo.ActivityIntentInfo[] array =
                            new VPackageInfo.ActivityIntentInfo[intentFilters.size()];
                    intentFilters.toArray(array);
                    listCut.add(array);
                }
            }
            return super.queryIntentFromList(intent, resolvedType, defaultOnly, listCut, userId);
        }

        private void addActivity(VPackageInfo.Activity a, String type,
                                 List<VPackageInfo.ActivityIntentInfo> newIntents) {
            mActivities.put(a.getComponentName(), a);
            final int intentsSize = a.intents.size();
            for (int j = 0; j < intentsSize; j++) {
                VPackageInfo.ActivityIntentInfo intent = a.intents.get(j);
                if (newIntents != null && "activity".equals(type)) {
                    newIntents.add(intent);
                }
                addFilter(intent);
            }
        }

        private void removeActivity(VPackageInfo.Activity a, String type) {
            mActivities.remove(a.getComponentName());
            final int intentsSize = a.intents.size();
            for (int j = 0; j < intentsSize; j++) {
                VPackageInfo.ActivityIntentInfo intent = a.intents.get(j);
                removeFilter(intent);
            }
        }

        @Override
        protected boolean isPackageForFilter(String packageName,
                                             VPackageInfo.ActivityIntentInfo info) {
            return packageName.equals(info.activity.owner.packageName);
        }

        @Override
        protected VPackageInfo.ActivityIntentInfo[] newArray(int size) {
            return new VPackageInfo.ActivityIntentInfo[size];
        }

        @Override
        protected ResolveInfo newResult(VPackageInfo.ActivityIntentInfo info, int match, int userId) {
            final VPackageInfo.Activity activity = info.activity;
            VPackageSettings ps = (VPackageSettings) activity.owner.mExtras;
            if (ps == null) {
                return null;
            }
            ActivityInfo ai =
                    PackageManagerCompat.generateActivityInfo(activity, mFlags, ps.readUserState(userId), userId);

            final ResolveInfo res = new ResolveInfo();
            res.activityInfo = ai;
            if ((mFlags & PackageManager.GET_RESOLVED_FILTER) != 0) {
                res.filter = info.intentFilter;
            }
            res.priority = info.intentFilter.getPriority();
            res.preferredOrder = activity.owner.mPreferredOrder;
            //System.out.println("Result: " + res.activityInfo.className +
            //                   " = " + res.priority);
            res.match = match;
            res.isDefault = info.hasDefault;
            res.labelRes = info.labelRes;
            res.nonLocalizedLabel = info.nonLocalizedLabel;
            res.icon = info.icon;
            return res;
        }

        // Keys are String (activity class name), values are Activity.
        private final ArrayMap<ComponentName, VPackageInfo.Activity> mActivities =
                new ArrayMap<>();
        private int mFlags;
    }

    private static final class ProviderIntentResolver
            extends IntentResolver<VPackageInfo.ProviderIntentInfo, ResolveInfo> {
        @Override
        public List<ResolveInfo> queryIntent(Intent intent, String resolvedType,
                                             boolean defaultOnly, int userId) {
            mFlags = defaultOnly ? PackageManager.MATCH_DEFAULT_ONLY : 0;
            return super.queryIntent(intent, resolvedType, defaultOnly, userId);
        }

        List<ResolveInfo> queryIntent(Intent intent, String resolvedType, int flags,
                                      int userId) {
            mFlags = flags;
            return super.queryIntent(intent, resolvedType,
                    (flags & PackageManager.MATCH_DEFAULT_ONLY) != 0,
                    userId);
        }

        List<ResolveInfo> queryIntentForPackage(Intent intent, String resolvedType,
                                                int flags, List<VPackageInfo.Provider> packageProviders, int userId) {
            if (packageProviders == null) {
                return null;
            }
            mFlags = flags;
            final boolean defaultOnly = (flags & PackageManager.MATCH_DEFAULT_ONLY) != 0;
            final int providersSize = packageProviders.size();
            ArrayList<VPackageInfo.ProviderIntentInfo[]> listCut = new ArrayList<>(providersSize);

            ArrayList<VPackageInfo.ProviderIntentInfo> intentFilters;
            for (int i = 0; i < providersSize; ++i) {
                intentFilters = packageProviders.get(i).intents;
                if (intentFilters != null && intentFilters.size() > 0) {
                    VPackageInfo.ProviderIntentInfo[] array =
                            new VPackageInfo.ProviderIntentInfo[intentFilters.size()];
                    intentFilters.toArray(array);
                    listCut.add(array);
                }
            }
            return super.queryIntentFromList(intent, resolvedType, defaultOnly, listCut, userId);
        }

        void addProvider(VPackageInfo.Provider p) {
            mProviders.put(p.getComponentName(), p);
            final int intentsSize = p.intents.size();
            int j;
            for (j = 0; j < intentsSize; j++) {
                VPackageInfo.ProviderIntentInfo intent = p.intents.get(j);
                addFilter(intent);
            }
        }

        void removeProvider(VPackageInfo.Provider p) {
            mProviders.remove(p.getComponentName());
            final int intentsSize = p.intents.size();
            int j;
            for (j = 0; j < intentsSize; j++) {
                VPackageInfo.ProviderIntentInfo intent = p.intents.get(j);
                removeFilter(intent);
            }
        }

        @Override
        protected boolean allowFilterResult(
                VPackageInfo.ProviderIntentInfo filter, List<ResolveInfo> dest) {
            ProviderInfo filterPi = filter.provider.info;
            for (int i = dest.size() - 1; i >= 0; i--) {
                ProviderInfo destPi = dest.get(i).providerInfo;
                if (destPi.name.equals(filterPi.name)
                        && destPi.packageName.equals(filterPi.packageName)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected VPackageInfo.ProviderIntentInfo[] newArray(int size) {
            return new VPackageInfo.ProviderIntentInfo[size];
        }

        @Override
        protected boolean isPackageForFilter(String packageName,
                                             VPackageInfo.ProviderIntentInfo info) {
            return packageName.equals(info.provider.owner.packageName);
        }

        @Override
        protected ResolveInfo newResult(VPackageInfo.ProviderIntentInfo filter, int match, int userId) {
            final VPackageInfo.ProviderIntentInfo info = filter;
            final VPackageInfo.Provider provider = info.provider;
            VPackageSettings ps = (VPackageSettings) provider.owner.mExtras;
            if (ps == null) {
                return null;
            }

            ProviderInfo pi = PackageManagerCompat.generateProviderInfo(provider, mFlags, ps.readUserState(userId), userId);
            final ResolveInfo res = new ResolveInfo();
            res.providerInfo = pi;
            if ((mFlags & PackageManager.GET_RESOLVED_FILTER) != 0) {
                res.filter = filter.intentFilter;
            }
            res.priority = info.intentFilter.getPriority();
            res.preferredOrder = provider.owner.mPreferredOrder;
            res.match = match;
            res.isDefault = info.hasDefault;
            res.labelRes = info.labelRes;
            res.nonLocalizedLabel = info.nonLocalizedLabel;
            res.icon = info.icon;
            return res;
        }

        private final ArrayMap<ComponentName, VPackageInfo.Provider> mProviders = new ArrayMap<>();
        private int mFlags;
    }
}
