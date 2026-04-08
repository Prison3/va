/*
 * Copyright (c) 2024 Prison Project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * GitHub: https://github.com/Prison3/prison
 * 996.ICU: https://996.icu | https://github.com/996icu/996.ICU
 */

// Created for blocking problematic resource files via libc hooks
// Also hooks libc syscall() so callers that bypass openat/open symbols still get path
// redirection (see SeccompTrap.h for why pure SECCOMP_RET_TRAP is not enabled here).
//
// Important: Do not call xdl_sym("syscall") before DobbyHook and use that pointer for
// the real syscall — the hooked entry is patched in-place, so that pointer becomes
// new_syscall and causes infinite recursion / stack overflow. Use orig_syscall only.
//
// SQLite and other code use stat/fstatat/access to probe paths before open; those must
// be hooked too or logical /data/user/... paths stay ENOENT.


#include <sys/stat.h>
#include <fcntl.h>
#include <stdarg.h>
#include <cstring>
#include <errno.h>
#include <limits.h>
#include <syscall.h>
#include <cstdio>
#include "Dobby/dobby.h"
#include "Foundation/SandboxFs.h"
#include "Foundation/Logger.h"
#include "Hooks.h"
#include "Foundation/xdl/xdl.h"
#include "SeccompTrap.h"
#include <unistd.h>

namespace {

int do_openat_relocated(int fd, const char *relocated_path, int flags, int mode);
int do_faccessat_relocated(int dirfd, const char *relocated_path, int mode, int flags);

} // namespace

// long syscall(long number, ...); — must be HOOK_DEF'd before helpers that use orig_syscall.
HOOK_DEF(long, syscall, long number, ...) {
    if (number == __NR_openat) {
        va_list ap;
        va_start(ap, number);
        int fd = static_cast<int>(va_arg(ap, long));
        const char *pathname = va_arg(ap, const char *);
        int flags = static_cast<int>(va_arg(ap, long));
        int mode = static_cast<int>(va_arg(ap, long));
        va_end(ap);

        char temp[PATH_MAX];
        const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
        if (!relocated_path) {
            errno = EACCES;
            return -1;
        }
        if ((flags & O_ACCMODE) == O_WRONLY) {
            flags &= ~O_ACCMODE;
            flags |= O_RDWR;
        }
        return do_openat_relocated(fd, relocated_path, flags, mode);
    }

    if (number == __NR_faccessat) {
        va_list ap;
        va_start(ap, number);
        int dirfd = static_cast<int>(va_arg(ap, long));
        const char *pathname = va_arg(ap, const char *);
        int mode = static_cast<int>(va_arg(ap, long));
        int flags = static_cast<int>(va_arg(ap, long));
        va_end(ap);

        char temp[PATH_MAX];
        const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
        if (!relocated_path || (mode & W_OK && isReadOnly(relocated_path))) {
            errno = EACCES;
            return -1;
        }
        return do_faccessat_relocated(dirfd, relocated_path, mode, flags);
    }

#if defined(__NR_newfstatat) || defined(__NR_fstatat)
    if (
#if defined(__NR_newfstatat)
        number == __NR_newfstatat
#else
        number == __NR_fstatat
#endif
    ) {
        va_list ap;
        va_start(ap, number);
        int dirfd = static_cast<int>(va_arg(ap, long));
        const char *pathname = va_arg(ap, const char *);
        struct stat *buf = va_arg(ap, struct stat *);
        int flag = static_cast<int>(va_arg(ap, long));
        va_end(ap);

        char temp[PATH_MAX];
        const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
        if (!relocated_path) {
            errno = EACCES;
            return -1;
        }
        if (orig_syscall == nullptr) {
            errno = ENOSYS;
            return -1;
        }
        return orig_syscall(
#if defined(__NR_newfstatat)
            __NR_newfstatat,
#else
            __NR_fstatat,
#endif
            static_cast<long>(dirfd), reinterpret_cast<long>(relocated_path),
            reinterpret_cast<long>(buf), static_cast<long>(flag), 0L, 0L);
    }
#endif

#if defined(__NR_faccessat2)
    if (number == __NR_faccessat2) {
        va_list ap;
        va_start(ap, number);
        int dirfd = static_cast<int>(va_arg(ap, long));
        const char *pathname = va_arg(ap, const char *);
        int mode = static_cast<int>(va_arg(ap, long));
        int flags = static_cast<int>(va_arg(ap, long));
        va_end(ap);

        char temp[PATH_MAX];
        const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
        if (!relocated_path || (mode & W_OK && isReadOnly(relocated_path))) {
            errno = EACCES;
            return -1;
        }
        if (orig_syscall == nullptr) {
            errno = ENOSYS;
            return -1;
        }
        return orig_syscall(__NR_faccessat2, static_cast<long>(dirfd),
                            reinterpret_cast<long>(relocated_path), static_cast<long>(mode),
                            static_cast<long>(flags), 0L, 0L);
    }
#endif

#if defined(__arm__) && defined(__NR_open)
    if (number == __NR_open) {
        va_list ap;
        va_start(ap, number);
        const char *pathname = va_arg(ap, const char *);
        int flags = static_cast<int>(va_arg(ap, long));
        unsigned mode = static_cast<unsigned>(va_arg(ap, long));
        va_end(ap);

        char temp[PATH_MAX];
        const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
        if (!relocated_path) {
            errno = EACCES;
            return -1;
        }
        if ((flags & O_ACCMODE) == O_WRONLY) {
            flags &= ~O_ACCMODE;
            flags |= O_RDWR;
        }
        if (orig_syscall == nullptr) {
            errno = ENOSYS;
            return -1;
        }
        return orig_syscall(__NR_open, reinterpret_cast<long>(relocated_path),
                            static_cast<long>(flags), static_cast<long>(mode), 0L, 0L, 0L);
    }
#endif

#if defined(__GNUC__) && !defined(__clang__)
    return orig_syscall(number, __builtin_va_arg_pack());
#else
    va_list ap;
    va_start(ap, number);
    long a0 = va_arg(ap, long);
    long a1 = va_arg(ap, long);
    long a2 = va_arg(ap, long);
    long a3 = va_arg(ap, long);
    long a4 = va_arg(ap, long);
    long a5 = va_arg(ap, long);
    va_end(ap);
    return orig_syscall(number, a0, a1, a2, a3, a4, a5);
#endif
}

namespace {

int do_openat_relocated(int fd, const char *relocated_path, int flags, int mode) {
    if (orig_syscall == nullptr) {
        errno = ENOSYS;
        return -1;
    }
    return static_cast<int>(orig_syscall(__NR_openat, static_cast<long>(fd),
                                         reinterpret_cast<long>(relocated_path),
                                         static_cast<long>(flags), static_cast<long>(mode), 0L, 0L));
}

int do_faccessat_relocated(int dirfd, const char *relocated_path, int mode, int flags) {
    if (orig_syscall == nullptr) {
        errno = ENOSYS;
        return -1;
    }
    return static_cast<int>(orig_syscall(__NR_faccessat, static_cast<long>(dirfd),
                                         reinterpret_cast<long>(relocated_path),
                                         static_cast<long>(mode), static_cast<long>(flags), 0L, 0L));
}

} // namespace

// int openat(int fd, const char *pathname, int flags, int mode);
HOOK_DEF(int, openat, int fd, const char *pathname, int flags, int mode) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (relocated_path) {
        if ((flags & O_ACCMODE) == O_WRONLY) {
            flags &= ~O_ACCMODE;
            flags |= O_RDWR;
        }
        return do_openat_relocated(fd, relocated_path, flags, mode);
    }
    errno = EACCES;
    return -1;
}


HOOK_DEF(int, faccessat, int dirfd, const char *pathname, int mode, int flags) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (relocated_path && !(mode & W_OK && isReadOnly(relocated_path))) {
        return do_faccessat_relocated(dirfd, relocated_path, mode, flags);
    }
    errno = EACCES;
    return -1;
}

HOOK_DEF(int, stat, const char *pathname, struct stat *buf) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (!relocated_path) {
        errno = EACCES;
        return -1;
    }
    return orig_stat(relocated_path, buf);
}

HOOK_DEF(int, lstat, const char *pathname, struct stat *buf) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (!relocated_path) {
        errno = EACCES;
        return -1;
    }
    return orig_lstat(relocated_path, buf);
}

HOOK_DEF(int, fstatat, int dirfd, const char *pathname, struct stat *buf, int flags) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (!relocated_path) {
        errno = EACCES;
        return -1;
    }
    return orig_fstatat(dirfd, relocated_path, buf, flags);
}

HOOK_DEF(int, access, const char *pathname, int mode) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (!relocated_path) {
        errno = EACCES;
        return -1;
    }
    if ((mode & W_OK) != 0 && isReadOnly(relocated_path)) {
        errno = EACCES;
        return -1;
    }
    return orig_access(relocated_path, mode);
}

// int open(const char *pathname, int flags, ...);
HOOK_DEF(int, open, const char *pathname, int flags, ...) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (relocated_path) {
        mode_t mode = 0;
        if (flags & O_CREAT) {
            va_list args;
            va_start(args, flags);
            mode = va_arg(args, mode_t);
            va_end(args);
        }
        
        if ((flags & O_ACCMODE) == O_WRONLY) {
            flags &= ~O_ACCMODE;
            flags |= O_RDWR;
        }
        return do_openat_relocated(AT_FDCWD, relocated_path, flags, mode);
    }
    errno = EACCES;
    return -1;
}

// FILE* fopen(const char *pathname, const char *mode);
HOOK_DEF(FILE*, fopen, const char *pathname, const char *mode) {
    char temp[PATH_MAX];
    const char *relocated_path = relocate_path(pathname, temp, sizeof(temp));
    if (relocated_path) {
        FILE* ret = orig_fopen(relocated_path, mode);
        return ret;
    }
    errno = EACCES;
    return nullptr;
}

void LibcHook::install() {
    ALOGD("LibcHook: Initializing file system hooks");
    
    void* handle = xdl_open("libc.so", XDL_DEFAULT);
    if (handle) {
        // syscall must be hooked first so orig_syscall is valid for do_*_relocated helpers.
        HOOK_SYMBOL(handle, syscall);
        HOOK_SYMBOL(handle, stat);
        HOOK_SYMBOL(handle, lstat);
        HOOK_SYMBOL(handle, fstatat);
        HOOK_SYMBOL(handle, access);
        HOOK_SYMBOL(handle, faccessat);
        HOOK_SYMBOL(handle, openat);
        HOOK_SYMBOL(handle, open);
        HOOK_SYMBOL(handle, fopen);
    }
    
    xdl_close(handle);
}
