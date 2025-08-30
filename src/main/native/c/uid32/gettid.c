#include "gettid.h"

#if defined(_WIN32) || defined(_WIN64)
  #include <windows.h>
  uint32_t gettid() {
      return GetCurrentThreadId();
  }
#else
#include <unistd.h>
#include <sys/syscall.h>
uint32_t gettid() {
      return (uint32_t)syscall(SYS_gettid);
  }
#endif