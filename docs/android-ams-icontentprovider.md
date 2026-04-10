# Android AMS、`IContentProvider` 与 VA 相关说明

本文档整理 **ActivityManager / `IActivityManager`**、**`IContentProvider`**、**Settings / `android_id`** 调用关系，以及本仓库（VA）中的对应代码位置，便于后续维护与排查。

---

## 1. `ActivityManager` 与 `IActivityManagerSingleton`

| 概念 | 说明 |
|------|------|
| **`android.app.ActivityManager`** | 应用可见的 API 类，用于查询任务、内存等；本身不直接实现跨进程逻辑。 |
| **`IActivityManager`** | **AIDL 接口**，应用进程内的 **Binder 代理** 通过它与 **system_server** 里的 **`ActivityManagerService`（AMS）** 通信。 |
| **`IActivityManagerSingleton`** | **`ActivityManager` 的静态字段**（类型多为 `android.util.Singleton<IActivityManager>`），**进程内单例、懒加载**，内部通过 `ServiceManager.getService(Context.ACTIVITY_SERVICE)` 取 Binder 并 `asInterface`。 |
| **`ActivityManager.getService()`**（API 26+） | 从上述 Singleton 取出 **`IActivityManager`** 代理。 |

**关系**：`ActivityManager` 是门面；`IActivityManagerSingleton` 用来缓存指向 AMS 的 **`IActivityManager`** 代理。

**本仓库**：`core/.../mirror/android/app/ActivityManagerOreo.java` 镜像 `IActivityManagerSingleton`；`IActivityManagerProxy` 通过 `BRSingleton` 替换 Singleton 内实例以劫持对 AMS 的调用。

---

## 2. `IActivityManager` 与四大组件

| 组件 | 与 AMS / `IActivityManager` 的关系 |
|------|--------------------------------------|
| **Activity** | 启动、任务栈、可见性等由 **AMS** 调度，应用通过 **`IActivityManager`** 类方法发起请求。 |
| **Service** | 启动、绑定、停止由 **AMS** 调度。 |
| **BroadcastReceiver** | 广播发送与派发由 **AMS** 侧广播子系统处理（与 AMS 强相关）。 |
| **ContentProvider** | **数据读写**走 **`IContentProvider`**；**authority 解析、跨进程连接、安装** 常由 **AMS** 参与（例如 `getContentProvider`）。 |

**记忆**：`IActivityManager` 名字里有 Activity，但实质是 **AMS 对应用暴露的 Binder 面**；Provider 的**数据面**单独记 **`IContentProvider`**。

---

## 3. 系统里 `IActivityManager`「接口有哪些」

- **完整定义**：以 AOSP 中 **`IActivityManager.aidl`**（及随版本拆分）为准，方法很多且随版本变化。
- **实现类**：`ActivityManagerService`（`system_server`）。
- **本仓库镜像**（仅反射会用到的子集）：  
  `core/src/main/java/com/android/va/mirror/android/app/IActivityManager.java`（及 `IActivityManagerL` / `IActivityManagerN`），含 `startActivity`、`getTaskForActivity`、`ContentProviderHolder` 等。

---

## 4. 读取 `android_id` 到 `IContentProvider` 的调用链（概要）

典型路径：**`Settings.Secure.getString(resolver, ANDROID_ID)`**（`ANDROID_ID` 即 `"android_id"`）。

1. **`Settings.Secure.getString` → `getStringForUser`**（内部实现随版本略有差异）。
2. **`ContentResolver`**：对 **`content://settings/secure`** 发起 **`call`**（或部分路径的 **`query`**）。
3. **`ContentResolver`** 解析 **authority = `settings`**，取得（或经 AMS 安装后缓存）**`IContentProvider`**（对端为 **`SettingsProvider`**，在 **system_server**）。
4. **`IContentProvider.call(...)`**：method / arg 携带 **`android_id`** 等；返回 **`Bundle`**，常见键 **`value`**。
5. 若此前当前进程尚未连接 **settings** Provider，**会先**经 **AMS** 完成 **`getContentProvider` / `getContentProviderExternal`** 等，把 **`IContentProvider`** 装入 **`ActivityThread`** 的 Provider 映射；**之后**读设置主要走 **`IContentProvider.call`**，不必每次再走完整 AMS 解析。

**VA 拦截点**：在 **`SystemProviderStub` / `ContentProviderStub`** 的 **`call`** 返回后，**`SettingsInterception`** 可对 **`android_id`** 替换 **`Bundle`** 中的 **`value`**。

---

## 5. 谁会调用 `IActivityManager.getContentProvider`（类方法）

- **直接调用方**：一般在**应用进程**的 **`ActivityThread`**（及 **`ContentResolver` 解析 authority** 相关路径），**不是**业务 App 直接写 `IActivityManager`。
- **触发场景**：第一次（或按系统策略）访问某 **authority** 的 **`ContentResolver`** 操作，需要向 **AMS** 申请 **`ContentProviderHolder` / `IContentProvider`** 连接时。
- **业务代码**：通常只调用 **`ContentResolver`**，间接触发上述路径。

**本仓库**：`IActivityManagerProxy` 中的 **`GetContentProvider`** 对该调用做 hook；对 **settings / media / telephony** 及 GMS 等 authority 会走 **`ContentProviderDelegate.update`**，其余虚拟包路径套 **`ContentProviderStub`**。

---

## 6. 本仓库相关代码索引

| 主题 | 路径 |
|------|------|
| 劫持 AMS | `core/.../hook/system/IActivityManagerProxy.java`（含 **`GetContentProvider`**） |
| 镜像 `IActivityManager` | `core/.../mirror/android/app/IActivityManager*.java` |
| 镜像 `ActivityManager` / Singleton | `core/.../mirror/android/app/ActivityManagerOreo.java` |
| Provider 包装与 `update` | `core/.../hook/content/ContentProviderDelegate.java` |
| 系统 Provider 代理（settings 等） | `core/.../hook/content/SystemProviderStub.java` |
| 通用 Provider 代理 | `core/.../hook/content/ContentProviderStub.java` |
| `android_id` 与 Settings 日志 | `core/.../hook/content/SettingsInterception.java` |
| 安装 Provider 后 `update` | `core/.../runtime/VActivityThread.java`（`installProvider` 等） |

---

## 7. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-04 | 初稿：整理 AMS、`IContentProvider`、`android_id` 与 VA  hook 关系 |

（后续可在表格中追加修订说明。）
