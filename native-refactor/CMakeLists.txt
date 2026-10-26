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

option(LCPATCH_BUILD_TESTS "Build native text-loader regression tests" OFF)
if(LCPATCH_BUILD_TESTS)
    add_executable(managed_localize_test tests/managed_localize_test.cpp)
    target_include_directories(managed_localize_test PRIVATE "${CMAKE_CURRENT_LIST_DIR}")
    target_compile_options(managed_localize_test PRIVATE -Wall -Wextra -Werror)
    add_library(il2cpp SHARED tests/fake_il2cpp.cpp)
    target_compile_options(il2cpp PRIVATE -O0 -fno-inline -fno-optimize-sibling-calls)
endif()
if(DEFINED LCPATCH_TEST_LOCALIZE_DIRECTORY)
    if(NOT LCPATCH_BUILD_TESTS)
        message(FATAL_ERROR "Test path override requires LCPATCH_BUILD_TESTS=ON")
    endif()
    target_compile_definitions(lcpatch_core PRIVATE
        LCPATCH_TEST_LOCALIZE_DIRECTORY="${LCPATCH_TEST_LOCALIZE_DIRECTORY}")
endif()
