cmake_minimum_required(VERSION 3.22.1)
project("sandbox_native")

add_library(
    sandbox_native
    SHARED
    sandbox_native.cpp
)

target_compile_features(sandbox_native PRIVATE cxx_std_17)
target_compile_options(sandbox_native PRIVATE -Wall -Wextra -Werror)
