/*
 * Seccomp-bpf + SECCOMP_RET_TRAP + SIGSYS 说明（未默认启用）
 *
 * 在「同一进程」里若对 __NR_openat 使用 SECCOMP_RET_TRAP，SIGSYS 处理函数里再调用
 * libc 的 syscall()/openat() 会再次命中同一条 seccomp 规则，形成递归；除非改 ucontext
 * 重入同一条 svc（实现依赖内核/架构细节），或使用 SECCOMP_RET_USER_NOTIF 由特权
 * 父进程代执行，否则不适合作为与 LibcHook 并行的通用重定向方案。
 *
 * 当前工程采用的「方案 1」落地实现：在 LibcHook 中额外 hook libc 的 syscall()，
 * 对 openat/faccessat 等与 openat 路径类调用走 relocate_path，并用 __builtin_syscall
 * 直调内核，从而拦截「走 syscall() 帮助函数」的绕过 libc 符号的路径。
 */

#ifndef PRISON_SECCOMP_TRAP_H
#define PRISON_SECCOMP_TRAP_H

#endif
