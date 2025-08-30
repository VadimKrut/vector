cmake_minimum_required(VERSION 3.30)
project(uid C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_C_EXTENSIONS OFF)

set(CMAKE_BUILD_TYPE Release)

add_library(uid SHARED
        tsc.c
        gettid.c
        get_core_id.c
        uid32.c
)

set_target_properties(uid PROPERTIES OUTPUT_NAME "uid")