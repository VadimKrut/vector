#include "get_core_id.h"

#if defined(_WIN32) || defined(_WIN64)
    #include <windows.h>
    uint32_t get_core_id() {
        return (uint32_t)GetCurrentProcessorNumber();
    }

#elif defined(__linux__)
#include <sched.h>
uint32_t get_core_id() {
        return (uint32_t)sched_getcpu();
    }

#else
#error "Unsupported OS"
#endif