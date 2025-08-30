#ifndef TSC_H
#define TSC_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

    uint64_t read_tsc();

#ifdef __cplusplus
}
#endif

#endif