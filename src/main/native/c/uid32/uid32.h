#ifndef UID32_H
#define UID32_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

void set_machine_id(uint32_t id);
void generate_uid32_into(uint8_t* dst);

#ifdef __cplusplus
}
#endif

#endif