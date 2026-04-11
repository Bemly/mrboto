cmake_minimum_required(VERSION 3.31)
project(mrboto LANGUAGES C)

# ──────────────────────────────────────────────────────────────
# mruby prebuilt static library (ABI-specific)
# ──────────────────────────────────────────────────────────────
set(MRUBY_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/mruby/lib/${ANDROID_ABI})

add_library(mruby STATIC IMPORTED)
set_target_properties(mruby PROPERTIES
    IMPORTED_LOCATION ${MRUBY_LIB_DIR}/libmruby.a
    INTERFACE_INCLUDE_DIRECTORIES ${CMAKE_CURRENT_SOURCE_DIR}/mruby/include
)

# ──────────────────────────────────────────────────────────────
# JNI native library
# ──────────────────────────────────────────────────────────────
add_library(mrboto-native SHARED native-lib.c)

target_include_directories(mrboto-native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/mruby/include
)

target_link_libraries(mrboto-native PRIVATE
    mruby
    log
    android
)

# Link math library (mruby uses math functions)
# Note: libm is implicitly linked on Android, but be explicit for clarity
target_link_options(mrboto-native PRIVATE "-lm")

# ──────────────────────────────────────────────────────────────
# Compiler flags for the JNI bridge
# ──────────────────────────────────────────────────────────────
target_compile_options(mrboto-native PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
    -fno-strict-aliasing
)
