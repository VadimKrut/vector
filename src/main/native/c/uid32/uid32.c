#include "uid32.h"
#include "tsc.h"
#include "get_core_id.h"
#include "gettid.h"

#include <stdint.h>

static uint32_t g_machine_id = 0;

void set_machine_id(uint32_t id) {
    g_machine_id = id;
}

static inline void fill_uid32(uint8_t* uid) {
    uint64_t tsc    = read_tsc();
    uintptr_t stack = (uintptr_t)&tsc;
    uint32_t machine = g_machine_id;
    uint32_t core_id = get_core_id();
    uint32_t tid     = gettid();
    uint32_t mix     = (uint32_t)(tsc ^ stack ^ machine ^ core_id ^ tid);

    *((uint64_t*)(uid + 0))  = tsc;
    *((uint64_t*)(uid + 8))  = stack;
    *((uint32_t*)(uid + 16)) = machine;
    *((uint32_t*)(uid + 20)) = core_id;
    *((uint32_t*)(uid + 24)) = tid;
    *((uint32_t*)(uid + 28)) = mix;
}

void generate_uid32_into(uint8_t* dst) {
    fill_uid32(dst);
}