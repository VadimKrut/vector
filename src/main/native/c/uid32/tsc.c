#include "tsc.h"

#if defined(_WIN32) || defined(_WIN64)
  #include <windows.h>
  #include <intrin.h>

  uint64_t read_tsc() {
      return __rdtsc();
  }

#elif defined(__x86_64__) || defined(__i386__)
uint64_t read_tsc() {
      uint32_t lo, hi;
      __asm__ __volatile__("rdtsc" : "=a"(lo), "=d"(hi));
      return ((uint64_t)hi << 32) | lo;
  }

#elif defined(__aarch64__)
uint64_t read_tsc() {
      uint64_t cnt;
      asm volatile("mrs %0, cntvct_el0" : "=r"(cnt));
      return cnt;
  }

#else
#error "Unsupported architecture for read_tsc"
#endif