cmake_minimum_required(VERSION 3.22)
project(lcpatch_core LANGUAGES C CXX ASM)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(DOBBY_GENERATE_SHARED OFF CACHE BOOL "" FORCE)
set(DOBBY_DEBUG OFF CACHE BOOL "" FORCE)
set(DOBBY_BUILD_EXAMPLE OFF CACHE BOOL "" FORCE)
set(DOBBY_BUILD_TEST OFF CACHE BOOL "" FORCE)
set(Plugin.SymbolResolver OFF CACHE BOOL "" FORCE)
set(Plugin.ImportTableReplace OFF CACHE BOOL "" FORCE)
set(Plugin.Android.BionicLinkerUtil OFF CACHE BOOL "" FORCE)

add_subdirectory(
    "${CMAKE_CURRENT_LIST_DIR}/../app/src/main/cpp/third_party/Dobby"
    "${CMAKE_CURRENT_BINARY_DIR}/dobby"
    EXCLUDE_FROM_ALL
)

add_library(lcpatch_core SHARED lcpatch_core.cpp)
target_include_directories(lcpatch_core PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/../app/src/main/cpp/third_party/Dobby/include"
)
target_compile_options(lcpatch_core PRIVATE
    -Wall -Wextra -Werror -fvisibility=hidden -fstack-protector-strong
)
target_link_options(lcpatch_core PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
target_link_libraries(lcpatch_core PRIVATE dobby_static log dl)
