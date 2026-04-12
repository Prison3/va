# Android 生物识别：`IAuthService` 与 `IBiometricService` 分层说明

本文记录 `frameworks/base` 中与 `BiometricManager`、认证策略、内部协调相关的 Binder 分层，便于日后改代码时对号入座。

---

## 1. 一句话结论

| 接口 | 实现类（system_server） | 典型调用方 |
|------|-------------------------|------------|
| **`IAuthService`** | `AuthService`（`AuthServiceImpl`） | **应用 / 框架公开路径**：`BiometricManager`、`BiometricPrompt` 等经此进入系统 |
| **`IBiometricService`** | `BiometricService`（`BiometricServiceWrapper` 等） | **系统内部**：`AuthService`、指纹/人脸等模块；**不**对普通应用暴露 |

`IBiometricService` 可理解为 **`IAuthService` 背后的内部一层**，负责聚合各 `IBiometricAuthenticator`、会话与强度等；应用开发者只应接触 **`IAuthService` 这条线**。

---

## 2. 调用链（自顶向下）

```
应用
  └─ BiometricManager / BiometricPrompt
        └─ IAuthService（服务名 Context.AUTH_SERVICE）→ AuthService
              └─ IBiometricService → BiometricService
                    └─ IBiometricAuthenticator（指纹 / 人脸 / …）
```

- **`SystemServiceRegistry`**：`Context.BIOMETRIC_SERVICE` 返回的是 `BiometricManager`，但其内部 Binder 来自 **`ServiceManager.getService(Context.AUTH_SERVICE)`**，再 **`IAuthService.Stub.asInterface`**。
- 因此：**类名叫 `BiometricManager`，绑的却是 `AUTH_SERVICE` / `IAuthService`**，容易与 `BiometricService` 混淆，需注意命名。

---

## 3. AIDL 文件位置

| AIDL | 路径（相对 `frameworks/base`） |
|------|--------------------------------|
| `IAuthService` | `core/java/android/hardware/biometrics/IAuthService.aidl` |
| `IBiometricService` | `core/java/android/hardware/biometrics/IBiometricService.aidl` |

AIDL 注释摘要：

- **`IAuthService`**：`BiometricPrompt` 与 `BiometricManager` 到 **AuthService** 的通道；不暴露具体模态。
- **`IBiometricService`**：**AuthService 到 BiometricService** 的通道（内部）。

---

## 4. 服务端实现位置

| 服务 | 路径（相对 `frameworks/base`） |
|------|--------------------------------|
| `AuthService` | `services/core/java/com/android/server/biometrics/AuthService.java` |
| `BiometricService` | `services/core/java/com/android/server/biometrics/BiometricService.java` |

---

## 5. 为何两套接口里都有 `canAuthenticate`？

二者不是给应用「二选一」的重复 API，而是 **分层委托**：

- **`IAuthService.canAuthenticate(opPackageName, userId, authenticators)`**  
  对外边界：做完调用者权限、包名等校验后，通常会 **转发** 到 BiometricService 一侧。

- **`IBiometricService.canAuthenticate(opPackageName, userId, callingUserId, authenticators)`**  
  多一个 **`callingUserId`**，用于系统内部区分 **调用者用户** 与 **业务 user**（多用户、代调用等）；方法上标有 **`USE_BIOMETRIC_INTERNAL`**，**普通应用无此权限**。

改行为时：

- 改 **应用可见语义**：先看 **`AuthService` + `IAuthService`**。
- 改 **聚合逻辑 / 多传感器 / callingUserId 语义**：看 **`BiometricService` + `IBiometricService`**，并确认 **AuthService 是否仍正确转发**。

---

## 6. 权限与可见性

- **`IAuthService`**：面向经框架转发的调用，配合 `USE_BIOMETRIC` 等应用侧可见能力（具体以各方法注解为准）。
- **`IBiometricService`**：接口侧大量使用 **`@EnforcePermission("USE_BIOMETRIC_INTERNAL")`**，仅供 **system / 同进程或持有内部权限的组件** 使用，**不是公开 SDK 给 app 直连的 Binder**。

---

## 7. 与「孵化器 / Zygote 加载」话题的区别（备忘）

若将敏感 dex 加载从 Zygote 挪到 **SystemServer**，则加载逻辑仅在 **system_server** 进程；**不会**随 Zygote fork 进入每个 app 进程。这与 **IAuth / IBiometric 分层** 是不同维度的问题：前者是 **进程与内存暴露面**，后者是 **Binder API 分层**。

---

## 8. 参考检索关键词

- `Context.AUTH_SERVICE`、`Context.BIOMETRIC_SERVICE`
- `SystemServiceRegistry` 中 `BiometricManager` 注册处
- `AuthServiceImpl`、`BiometricServiceWrapper`、`IBiometricService.Stub`

---

*文档为工程笔记，与上游 AOSP 某一版本源码对应；升级大版本时请对照当前 `*.aidl` 与实现类再核对。*
