cmake_minimum_required(VERSION 3.22.1)
project("bittv_native")

add_library(bittv_native SHARED
    native-lib.cpp
)

target_compile_features(bittv_native PRIVATE cxx_std_17)

target_compile_options(bittv_native PRIVATE
    -fvisibility=hidden
    -fno-exceptions
    -fno-rtti
)

set_target_properties(bittv_native PROPERTIES
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN YES
)
