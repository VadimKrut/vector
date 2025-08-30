// uid32.c
#include "uid32.h"
#include "tsc.h"
#include "get_core_id.h"
#include "gettid.h"

#include <stdint.h>

// Глобальный machine_id (4 байта)
static uint32_t g_machine_id = 0;

// Установка machine_id извне
void set_machine_id(uint64_t id) {
    g_machine_id = (uint32_t)id;
}

// Генерация 32-байтного UID
const uint8_t* generate_uid32() {
    static __thread uint8_t uid[32];

    uint64_t tsc    = read_tsc();               // 8 байт
    uintptr_t stack = (uintptr_t)&tsc;          // 8 байт
    uint32_t machine = g_machine_id;            // 4 байта
    uint32_t core_id = get_core_id();           // 4 байта
    uint32_t tid     = gettid();                // 4 байта
    uint32_t mix     = (uint32_t) (tsc ^ stack ^ machine ^ core_id ^ tid); // 4 байта

    *((uint64_t*)(uid + 0))  = tsc;
    *((uint64_t*)(uid + 8))  = stack;
    *((uint32_t*)(uid + 16)) = machine;
    *((uint32_t*)(uid + 20)) = core_id;
    *((uint32_t*)(uid + 24)) = tid;
    *((uint32_t*)(uid + 28)) = mix;

    return uid;
}