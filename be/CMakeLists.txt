# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

cmake_minimum_required(VERSION 3.5.1)

# set CMAKE_C_COMPILER, this must set before project command
if (DEFINED ENV{CC} AND DEFINED ENV{CXX})
    set(CMAKE_C_COMPILER "$ENV{CC}")
    set(CMAKE_CXX_COMPILER "$ENV{CXX}")
elseif (DEFINED ENV{STARROCKS_GCC_HOME})
    # prefer GCC
    set(CMAKE_C_COMPILER "$ENV{STARROCKS_GCC_HOME}/bin/gcc")
    set(CMAKE_CXX_COMPILER "$ENV{STARROCKS_GCC_HOME}/bin/g++")
elseif (DEFINED ENV{STARROCKS_LLVM_HOME})
    set(CMAKE_C_COMPILER "$ENV{STARROCKS_LLVM_HOME}/bin/clang")
    set(CMAKE_CXX_COMPILER "$ENV{STARROCKS_LLVM_HOME}/bin/clang++")
else()
    message(FATAL_ERROR "STARROCKS_GCC_HOME environment variable is not set")
endif()

project(starrocks CXX C)

# set CMAKE_BUILD_TYPE
if (NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE RELEASE)
endif()

string(TOUPPER ${CMAKE_BUILD_TYPE} CMAKE_BUILD_TYPE)
message(STATUS "Build type is ${CMAKE_BUILD_TYPE}")

# set CMAKE_BUILD_TARGET_ARCH
# use `lscpu | grep 'Architecture' | awk '{print $2}'` only support system which language is en_US.UTF-8
execute_process(COMMAND bash "-c" "uname -m"
                OUTPUT_VARIABLE
                CMAKE_BUILD_TARGET_ARCH
                OUTPUT_STRIP_TRAILING_WHITESPACE)
message(STATUS "Build target arch is ${CMAKE_BUILD_TARGET_ARCH}")

# Set dirs
set(BASE_DIR "${CMAKE_CURRENT_SOURCE_DIR}")
set(BUILD_DIR "${CMAKE_CURRENT_BINARY_DIR}")
set(THIRDPARTY_DIR "$ENV{STARROCKS_THIRDPARTY}/installed/")
set(STARROCKS_THIRDPARTY "$ENV{STARROCKS_THIRDPARTY}")
set(GENSRC_DIR "${BASE_DIR}/../gensrc/build/")
set(SRC_DIR "${BASE_DIR}/src/")
set(TEST_DIR "${CMAKE_SOURCE_DIR}/test/")
set(OUTPUT_DIR "${BASE_DIR}/output")
set(CONTRIB_DIR "${BASE_DIR}/../contrib/")
set(CMAKE_EXPORT_COMPILECOMMANDS ON)

if (APPLE)
    set(MAKE_TEST "ON")
else()
    option(MAKE_TEST "ON for make unit test or OFF for not" OFF)
endif()
message(STATUS "make test: ${MAKE_TEST}")

option(WITH_GCOV "Build binary with gcov to get code coverage" OFF)

option(WITH_COMPRESS "Build binary with compresss debug section" ON)

option(WITH_BLOCK_CACHE "Build binary with block cache feature" OFF)

option(WITH_BENCH "Build binary with bench" OFF)

option(USE_STAROS "Use StarOS to manager tablet info" OFF)

# Check gcc
if (CMAKE_CXX_COMPILER_ID STREQUAL "GNU")
    if (CMAKE_CXX_COMPILER_VERSION VERSION_LESS "4.8.2")
        message(FATAL_ERROR "Need GCC version at least 4.8.2")
    endif()

    if (CMAKE_CXX_COMPILER_VERSION VERSION_GREATER "10.3.0")
        message(STATUS "GCC version ${CMAKE_CXX_COMPILER_VERSION} is greater than 10.3.0, disable -Werror. Be careful with compile warnings.")
    else()
        #   -Werror: compile warnings should be errors when using the toolchain compiler.
        set(CXX_GCC_FLAGS "${CXX_GCC_FLAGS} -Werror")
    endif()
elseif (CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
elseif (NOT APPLE)
    message(FATAL_ERROR "Compiler should be GNU")
endif()

set(PIC_LIB_PATH "${THIRDPARTY_DIR}")
if(PIC_LIB_PATH)
    message(STATUS "defined PIC_LIB_PATH")
    set(CMAKE_SKIP_RPATH TRUE)
    set(Boost_USE_STATIC_LIBS ON)
    set(Boost_USE_STATIC_RUNTIME ON)
    set(LIBBZ2 ${PIC_LIB_PATH}/lib/libbz2.a)
    set(LIBZ ${PIC_LIB_PATH}/lib/libz.a)
    set(LIBEVENT ${PIC_LIB_PATH}/lib/libevent.a)
else()
    message(STATUS "undefined PIC_LIB_PATH")
    set(Boost_USE_STATIC_LIBS ON)
    set(Boost_USE_STATIC_RUNTIME ON)
    set(LIBBZ2 -lbz2)
    set(LIBZ -lz)
    set(LIBEVENT event)
endif()


# Compile generated source if necessary
message(STATUS "build gensrc if necessary")
execute_process(COMMAND make -C ${BASE_DIR}/../gensrc/
                RESULT_VARIABLE MAKE_GENSRC_RESULT)
if(NOT ${MAKE_GENSRC_RESULT} EQUAL 0 AND NOT APPLE)
    message(FATAL_ERROR "Failed to build ${BASE_DIR}/../gensrc/")
endif()

#
set(BUILD_VERSION_CC ${CMAKE_BINARY_DIR}/build_version.cc)
configure_file(${SRC_DIR}/common/build_version.cc.in ${BUILD_VERSION_CC} @ONLY)
add_library(build_version OBJECT ${BUILD_VERSION_CC})
target_include_directories(build_version PRIVATE ${SRC_DIR}/common)

# Add common cmake prefix path and link library path
list(APPEND CMAKE_PREFIX_PATH ${THIRDPARTY_DIR}/lib/cmake)
list(APPEND CMAKE_PREFIX_PATH ${THIRDPARTY_DIR}/lib64/cmake)
link_directories(${THIRDPARTY_DIR}/lib ${THIRDPARTY_DIR}/lib64)

# Set Boost
set(Boost_DEBUG FALSE)
set(Boost_USE_MULTITHREADED ON)
set(Boost_NO_BOOST_CMAKE ON)
set(BOOST_ROOT ${THIRDPARTY_DIR})
# boost suppress warning is supported on cmake 3.20
# https://cmake.org/cmake/help/latest/module/FindBoost.html
set(Boost_NO_WARN_NEW_VERSIONS ON)

if (NOT APPLE)
    find_package(Boost 1.75.0 REQUIRED COMPONENTS thread regex program_options filesystem context)
else()
    find_package(Boost 1.75.0 COMPONENTS thread regex program_options filesystem context)
endif()
include_directories(${Boost_INCLUDE_DIRS})
message(STATUS ${Boost_LIBRARIES})

set(GPERFTOOLS_HOME "${THIRDPARTY_DIR}/gperftools")

# Set all libraries

add_library(gflags STATIC IMPORTED)
set_target_properties(gflags PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libgflags.a)

add_library(glog STATIC IMPORTED)
set_target_properties(glog PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libglog.a)

add_library(re2 STATIC IMPORTED)
set_target_properties(re2 PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libre2.a)

add_library(pprof STATIC IMPORTED)
set_target_properties(pprof PROPERTIES IMPORTED_LOCATION
    ${GPERFTOOLS_HOME}/lib/libprofiler.a)

add_library(tcmalloc STATIC IMPORTED)
set_target_properties(tcmalloc PROPERTIES IMPORTED_LOCATION
    ${GPERFTOOLS_HOME}/lib/libtcmalloc.a)

add_library(protobuf STATIC IMPORTED)
set_target_properties(protobuf PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libprotobuf.a)

add_library(protoc STATIC IMPORTED)
set_target_properties(protoc PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libprotoc.a)

add_library(gtest STATIC IMPORTED)
set_target_properties(gtest PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libgtest.a)

add_library(gmock STATIC IMPORTED)
set_target_properties(gmock PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libgmock.a)

add_library(snappy STATIC IMPORTED)
set_target_properties(snappy PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libsnappy.a)

add_library(curl STATIC IMPORTED)
set_target_properties(curl PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libcurl.a)

add_library(lz4 STATIC IMPORTED)
set_target_properties(lz4 PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/liblz4.a)

add_library(thrift STATIC IMPORTED)
set_target_properties(thrift PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libthrift.a)

add_library(thriftnb STATIC IMPORTED)
set_target_properties(thriftnb PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libthriftnb.a)

add_library(mysql STATIC IMPORTED)
set_target_properties(mysql PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/mariadb/libmariadbclient.a)

add_library(hdfs STATIC IMPORTED)
set_target_properties(hdfs PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libhdfs.a)

# Allow FindOpenSSL() to find correct static libraries
set(OPENSSL_ROOT_DIR ${THIRDPARTY_DIR} CACHE STRING "root directory of an OpenSSL installation")
message(STATUS "Using OpenSSL Root Dir: ${OPENSSL_ROOT_DIR}")
add_library(crypto STATIC IMPORTED)
set_target_properties(crypto PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libcrypto.a)

set(AWSSDK_ROOT_DIR ${THIRDPARTY_DIR})
set(AWSSDK_COMMON_RUNTIME_LIBS "aws-crt-cpp;aws-c-auth;aws-c-cal;aws-c-common;aws-c-compression;aws-c-event-stream;aws-c-http;aws-c-io;aws-c-mqtt;aws-c-s3;aws-checksums;s2n;aws-c-sdkutils")
foreach(lib IN ITEMS ${AWSSDK_COMMON_RUNTIME_LIBS})
    list(APPEND CMAKE_PREFIX_PATH ${THIRDPARTY_DIR}/lib/${lib}/cmake)
    list(APPEND CMAKE_PREFIX_PATH ${THIRDPARTY_DIR}/lib64/${lib}/cmake)
endforeach()
find_package(AWSSDK REQUIRED COMPONENTS core s3 s3-crt transfer identity-management sts)
include_directories(${AWSSDK_INCLUDE_DIRS})

add_library(libevent STATIC IMPORTED)
set_target_properties(libevent PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libevent.a)

add_library(openssl STATIC IMPORTED)
set_target_properties(openssl PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libssl.a)

add_library(leveldb STATIC IMPORTED)
set_target_properties(leveldb PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libleveldb.a)

add_library(jemalloc STATIC IMPORTED)
set_target_properties(jemalloc PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libjemalloc_for_starrocks.a)

add_library(jemalloc_for_arrow STATIC IMPORTED)
set_target_properties(jemalloc_for_arrow PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib64/libjemalloc.a)

add_library(brotlicommon STATIC IMPORTED)
set_target_properties(brotlicommon PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib64/libbrotlicommon.a)

add_library(brotlidec STATIC IMPORTED)
set_target_properties(brotlidec PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib64/libbrotlidec.a)

add_library(brotlienc STATIC IMPORTED)
set_target_properties(brotlienc PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib64/libbrotlienc.a)

add_library(zstd STATIC IMPORTED)
set_target_properties(zstd PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib64/libzstd.a)

add_library(streamvbyte STATIC IMPORTED)
set_target_properties(streamvbyte PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libstreamvbyte_static.a)

add_library(arrow STATIC IMPORTED)
set_target_properties(arrow PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib64/libarrow.a)

add_library(parquet STATIC IMPORTED)
set_target_properties(parquet PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib64/libparquet.a)

add_library(brpc STATIC IMPORTED)
set_target_properties(brpc PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib64/libbrpc.a)

add_library(rocksdb STATIC IMPORTED)
set_target_properties(rocksdb PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/librocksdb.a)

add_library(librdkafka_cpp STATIC IMPORTED)
set_target_properties(librdkafka_cpp PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/librdkafka++.a)

add_library(librdkafka STATIC IMPORTED)
set_target_properties(librdkafka PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/librdkafka.a)

add_library(libpulsar STATIC IMPORTED)
set_target_properties(libpulsar PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libpulsar.a)

add_library(libs2 STATIC IMPORTED)
set_target_properties(libs2 PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libs2.a)

add_library(bitshuffle STATIC IMPORTED)
set_target_properties(bitshuffle PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libbitshuffle.a)

add_library(roaring STATIC IMPORTED)
set_target_properties(roaring PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libroaring.a)

add_library(cctz STATIC IMPORTED)
set_target_properties(cctz PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libcctz.a)

add_library(benchmark STATIC IMPORTED)
set_target_properties(benchmark PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib64/libbenchmark.a)

add_library(benchmark_main STATIC IMPORTED)
set_target_properties(benchmark_main PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib64/libbenchmark_main.a)

add_library(fmt STATIC IMPORTED)
set_target_properties(fmt PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib64/libfmt.a)

add_library(ryu STATIC IMPORTED)
set_target_properties(ryu PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib64/libryu.a)

# Disable minidump on aarch64, hence breakpad is not needed.
if ("${CMAKE_BUILD_TARGET_ARCH}" STREQUAL "x86" OR "${CMAKE_BUILD_TARGET_ARCH}" STREQUAL "x86_64")
    add_library(breakpad STATIC IMPORTED)
    set_target_properties(breakpad PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libbreakpad_client.a)
endif()

add_library(hyperscan STATIC IMPORTED)
set_target_properties(hyperscan PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libhs.a)

add_library(simdjson STATIC IMPORTED)
set_target_properties(simdjson PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libsimdjson.a)

add_library(velocypack STATIC IMPORTED)
set_target_properties(velocypack PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib/libvelocypack.a)

find_program(THRIFT_COMPILER thrift ${CMAKE_SOURCE_DIR}/bin)

add_library(http_client_curl STATIC IMPORTED GLOBAL)
set_target_properties(http_client_curl PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib64/libhttp_client_curl.a)

add_library(opentelemetry_common STATIC IMPORTED GLOBAL)
set_target_properties(opentelemetry_common PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib64/libopentelemetry_common.a)

add_library(opentelemetry_trace STATIC IMPORTED GLOBAL)
set_target_properties(opentelemetry_trace PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib64/libopentelemetry_trace.a)

add_library(opentelemetry_resources STATIC IMPORTED GLOBAL)
set_target_properties(opentelemetry_resources PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib64/libopentelemetry_resources.a)


add_library(opentelemetry_exporter_jaeger_trace STATIC IMPORTED GLOBAL)
set_target_properties(opentelemetry_exporter_jaeger_trace PROPERTIES IMPORTED_LOCATION ${THIRDPARTY_DIR}/lib64/libopentelemetry_exporter_jaeger_trace.a)

# cachelib
set(CACHELIB_DEPENDENCIES
    cachelib_common
    cachelib_allocator
    cachelib_datatype
    cachelib_navy
    cachelib_shm
    folly
    fmtd
    thriftcpp2
    thrift-core
    thriftmetadata
    thriftfrozen2
    thriftprotocol
    thrifttype
    transport
    rpcmetadata
    thriftanyrep
    thrifttyperep
    thriftannotation
    concurrency
    async
    wangle
)

if (${WITH_BLOCK_CACHE} STREQUAL "ON")
    LINK_LIBRARIES(${THIRDPARTY_DIR}/cachelib/deps/lib64/libunwind.so)
    LINK_LIBRARIES(${THIRDPARTY_DIR}/cachelib/deps/lib64/liblzma.so)
    foreach(dep ${CACHELIB_DEPENDENCIES})
        add_library(${dep} STATIC IMPORTED)
        set(location "${THIRDPARTY_DIR}/cachelib/lib/lib${dep}.a")
        if(NOT EXISTS ${location})
            set(location "${THIRDPARTY_DIR}/cachelib/lib64/lib${dep}.a")
        endif()
        set_target_properties(${dep} PROPERTIES IMPORTED_LOCATION "${location}")
        message(STATUS "add cachelib dependency: ${location}")
    endforeach()
endif()

if ("${USE_STAROS}" STREQUAL "ON")
     find_package(protobuf CONFIG REQUIRED)
     message(STATUS "Using protobuf ${protobuf_VERSION}")
     message(STATUS "protobuf include path: ${protobuf_INCLUDE_DIRS}")
     message(STATUS "protobuf libs: ${protobuf_LIBS}")

     find_package(gRPC CONFIG REQUIRED)
     message(STATUS "Using gRPC ${gRPC_VERSION}")
     get_target_property(gRPC_INCLUDE_DIR gRPC::grpc INTERFACE_INCLUDE_DIRECTORIES)
     include_directories(SYSTEM ${gRPC_INCLUDE_DIR})

     find_package(starlet CONFIG REQUIRED)
     message(STATUS "Using starlet ${starlet_VERSION}")
     message(STATUS "starlet inc dir: ${STARLET_INCLUDE_DIRS}")
     message(STATUS "starlet lib dir: ${STARLET_LIBS}")
     include_directories(SYSTEM ${STARLET_INCLUDE_DIRS})
     add_library(starlet STATIC IMPORTED)
     set_target_properties(starlet PROPERTIES IMPORTED_LOCATION ${STARLET_LIBS}/libstarlet.a)

     find_package (prometheus-cpp CONFIG REQUIRED)
     get_target_property(prometheus-cpp_INCLUDE_DIR prometheus-cpp::core INTERFACE_INCLUDE_DIRECTORIES)
     message(STATUS "Using prometheus-cpp")
     message(STATUS "  include: ${prometheus-cpp_INCLUDE_DIR}")
     include_directories(SYSTEM ${prometheus-cpp_INCLUDE_DIR})

     add_library(starlet_fslib_all STATIC IMPORTED GLOBAL)
     set_target_properties(starlet_fslib_all PROPERTIES IMPORTED_LOCATION ${STARLET_LIBS}/libstarlet_fslib_all.a)

     set(STARROCKS_DEPENDENCIES
        ${STARROCKS_DEPENDENCIES}
        starlet
        gRPC::grpc++_reflection
        gRPC::grpc++
        starlet_fslib_all
        prometheus-cpp::core
        )
endif()

# Check if functions are supported in this platform. All flags will generated
# in gensrc/build/common/env_config.h.
# You can check funcion here which depends on platform. Don't forget add this
# to be/src/common/env_config.h.in
include(CheckFunctionExists)
check_function_exists(sched_getcpu HAVE_SCHED_GETCPU)

# support to pass cxx flags from environment.
set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} $ENV{STARROCKS_CXX_COMMON_FLAGS}")

# -no-pie: Disable Position Independent Executables (PIE) as certain libraries cannot be linked against PIE executables.
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -no-pie")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} $ENV{STARROCKS_CXX_LINKER_FLAGS}")

# compiler flags that are common across debug/release builds
#  -Wall: Enable all warnings.
#  -Wno-sign-compare: suppress warnings for comparison between signed and unsigned
#    integers
#   -fno-strict-aliasing: disable optimizations that assume strict aliasing. This
#       is unsafe to do if the code uses casts (which we obviously do).
#  -Wno-unknown-pragmas: suppress warnings for unknown (compiler specific) pragmas
#  -Wno-deprecated: gutil contains deprecated headers
#  -Wno-vla: we use C99-style variable-length arrays
#  -pthread: enable multithreaded malloc
#  -DBOOST_DATE_TIME_POSIX_TIME_STD_CONFIG: enable nanosecond precision for boost
#  -fno-omit-frame-pointers: Keep frame pointer for functions in register
set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -Wall -Wno-sign-compare -Wno-unknown-pragmas -pthread -Wno-register")
set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -Wno-strict-aliasing -fno-omit-frame-pointer")
set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -std=gnu++17 -D__STDC_FORMAT_MACROS")
set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -Wno-deprecated -Wno-vla -Wno-comment")

if (CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
    set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -Wno-unused-parameter -Wno-documentation -Wno-weak-vtables")
    set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -Wno-reserved-identifier -Wno-suggest-destructor-override")
    set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -Wno-documentation-unknown-command -Wno-old-style-cast")
    set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -Wno-c++20-designator -Wno-mismatched-tags")
    if (CMAKE_CXX_COMPILER_VERSION VERSION_GREATER_EQUAL "14.0.0")
        set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -Wno-bitwise-instead-of-logical")
    endif()
endif()

set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -DBOOST_DATE_TIME_POSIX_TIME_STD_CONFIG")
set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -DBOOST_SYSTEM_NO_DEPRECATED -DBOOST_UUID_RANDOM_PROVIDER_FORCE_POSIX")
set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -Werror=return-type -Werror=switch")
if (${USE_STAROS} STREQUAL "ON")
    set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -DUSE_STAROS")
endif()
if (${WITH_BLOCK_CACHE} STREQUAL "ON")
    set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -DWITH_BLOCK_CACHE")
endif()

# When LLVM is used, should give GCC_HOME to get c++11 header to use new string and list
if (CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
    if (DEFINED ENV{STARROCKS_GCC_HOME})
        set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} --gcc-toolchain=$ENV{STARROCKS_GCC_HOME}")
    else()
        message(WARNING "STARROCKS_GCC_HOME evnironment variable is not set, ")
    endif()
endif()

if ("${CMAKE_BUILD_TARGET_ARCH}" STREQUAL "x86" OR "${CMAKE_BUILD_TARGET_ARCH}" STREQUAL "x86_64")
    if (USE_SSE4_2)
        set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -msse4.2")
    endif()
    if (USE_AVX2)
        set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -mavx2")
    endif()
elseif ("${CMAKE_BUILD_TARGET_ARCH}" STREQUAL "aarch64")
    set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -march=armv8-a+crc")
endif()
set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS}  -Wno-attributes -DS2_USE_GFLAGS -DS2_USE_GLOG")

if (CMAKE_CXX_COMPILER_VERSION VERSION_GREATER 7.0)
    set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -faligned-new")
endif()

# For any gcc builds:
#   -g: Enable symbols for profiler tools
#   -Wno-unused-local-typedefs: Do not warn for local typedefs that are unused.
set(CXX_GCC_FLAGS "${CXX_GCC_FLAGS} -g -Wno-unused-local-typedefs")

if (WITH_GCOV)
    # To generate flags to code coverage
    set(CXX_GCC_FLAGS "${CXX_GCC_FLAGS} -fprofile-arcs -ftest-coverage")
endif()

if (WITH_COMPRESS)
    # to compresss debug section. https://gcc.gnu.org/onlinedocs/gcc/Debugging-Options.html
    set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -gz=zlib")
endif()

if (ENABLE_QUERY_DEBUG_TRACE)
    set(CXX_COMMON_FLAGS "${CXX_COMMON_FLAGS} -DENABLE_QUERY_DEBUG_TRACE")
    message(STATUS "enable query debug trace")
endif()

# For CMAKE_BUILD_TYPE=Debug
#   -ggdb: Enable gdb debugging
# Debug information is stored as dwarf2 to be as compatible as possible
#   -Werror: compile warnings should be errors when using the toolchain compiler.
# Only enable for debug builds because this is what we test in pre-commit tests.
set(CXX_FLAGS_DEBUG "${CXX_GCC_FLAGS} -ggdb -O0 -gdwarf-4 -DDEBUG")

# For CMAKE_BUILD_TYPE=Release
#   -O3: Enable all compiler optimizations
#   -DNDEBUG: Turn off dchecks/asserts/debug only code.
#   -gdwarf-4: Debug information is stored as dwarf2 to be as compatible as possible
set(CXX_FLAGS_RELEASE "${CXX_GCC_FLAGS} -O3 -gdwarf-4 -DNDEBUG")

SET(CXX_FLAGS_ASAN "${CXX_GCC_FLAGS} -ggdb3 -O0 -gdwarf-4 -fsanitize=address -DADDRESS_SANITIZER")
SET(CXX_FLAGS_LSAN "${CXX_GCC_FLAGS} -ggdb3 -O0 -gdwarf-4 -fsanitize=leak -DLEAK_SANITIZER")

# Set the flags to the undefined behavior sanitizer, also known as "ubsan"
# Turn on sanitizer and debug symbols to get stack traces:
SET(CXX_FLAGS_UBSAN "${CXX_FLAGS_RELEASE} -fsanitize=undefined")
# Ignore a number of noisy errors with too many false positives:
# TODO(zc):
# SET(CXX_FLAGS_UBSAN "${CXX_FLAGS_UBSAN} -fno-sanitize=alignment,function,vptr,float-divide-by-zero,float-cast-overflow")
# Don't enforce wrapped signed integer arithmetic so that the sanitizer actually sees

# Set the flags to the thread sanitizer, also known as "tsan"
# Turn on sanitizer and debug symbols to get stack traces:
SET(CXX_FLAGS_TSAN "${CXX_GCC_FLAGS} -O0 -ggdb3 -fsanitize=thread -DTHREAD_SANITIZER")

# Set compile flags based on the build type.
if ("${CMAKE_BUILD_TYPE}" STREQUAL "DEBUG")
    SET(CMAKE_CXX_FLAGS ${CXX_FLAGS_DEBUG})
elseif ("${CMAKE_BUILD_TYPE}" STREQUAL "RELEASE")
    SET(CMAKE_CXX_FLAGS ${CXX_FLAGS_RELEASE})
elseif ("${CMAKE_BUILD_TYPE}" STREQUAL "ASAN")
    SET(CMAKE_CXX_FLAGS "${CXX_FLAGS_ASAN}")
elseif ("${CMAKE_BUILD_TYPE}" STREQUAL "LSAN")
    SET(CMAKE_CXX_FLAGS "${CXX_FLAGS_LSAN}")
elseif ("${CMAKE_BUILD_TYPE}" STREQUAL "UBSAN")
    SET(CMAKE_CXX_FLAGS "${CXX_FLAGS_UBSAN}")
elseif ("${CMAKE_BUILD_TYPE}" STREQUAL "TSAN")
    SET(CMAKE_CXX_FLAGS "${CXX_FLAGS_TSAN}")
else()
    message(FATAL_ERROR "Unknown build type: ${CMAKE_BUILD_TYPE}")
endif()

# Add flags that are common across build types
SET(CMAKE_CXX_FLAGS "${CXX_COMMON_FLAGS} ${CMAKE_CXX_FLAGS}")

message(STATUS "Compiler Flags: ${CMAKE_CXX_FLAGS}")

# Thrift requires these two definitions for some types that we use
add_definitions(-DHAVE_INTTYPES_H -DHAVE_NETINET_IN_H)

# Set include dirs
include_directories(
    BEFORE
    ${SRC_DIR}/formats/orc/apache-orc/c++/include/
)
include_directories(
    ${SRC_DIR}/
    ${TEST_DIR}/
    ${GENSRC_DIR}/
    SYSTEM ${THIRDPARTY_DIR}/include
    SYSTEM ${GPERFTOOLS_HOME}/include
    SYSTEM ${THIRDPARTY_DIR}/include/thrift/
    SYSTEM ${THIRDPARTY_DIR}/include/event/
)


if ("${CMAKE_BUILD_TARGET_ARCH}" STREQUAL "x86" OR "${CMAKE_BUILD_TARGET_ARCH}" STREQUAL "x86_64")
    include_directories(${THIRDPARTY_DIR}/include/breakpad/)
endif()

set(WL_START_GROUP "-Wl,--start-group")
set(WL_END_GROUP "-Wl,--end-group")

# Set starrocks libraries
set(STARROCKS_LINK_LIBS
    ${WL_START_GROUP}
    Agent
    Common
    Column
    Connector
    Exec
    Exprs
    FileSystem
    Formats
    Gutil
    IO
    Serde
    Storage
    Rowset
    Runtime
    Service
    ServiceBE
    ServiceCN
    Types
    Udf
    Util
    StarRocksGen
    Webserver
    TestUtil
    Tools
    Geo
    ${WL_END_GROUP}
)

if (${WITH_BLOCK_CACHE} STREQUAL "ON")
    set(STARROCKS_LINK_LIBS ${STARROCKS_LINK_LIBS} BlockCache)
endif()

set(STARROCKS_DEPENDENCIES ${STARROCKS_DEPENDENCIES}
    mysql
)


set(STARROCKS_DEPENDENCIES ${STARROCKS_DEPENDENCIES}
    hdfs
    jvm
)
set(JAVA_HOME ${THIRDPARTY_DIR}/open_jdk/)
add_library(jvm SHARED IMPORTED)
FILE(GLOB_RECURSE LIB_JVM ${JAVA_HOME}/jre/lib/*/libjvm.so)
set_target_properties(jvm PROPERTIES IMPORTED_LOCATION ${LIB_JVM})
include_directories(${JAVA_HOME}/include)
include_directories(${JAVA_HOME}/include/linux)

set(STARROCKS_DEPENDENCIES ${STARROCKS_DEPENDENCIES}
    ${AWSSDK_LINK_LIBRARIES}
)
set_target_properties(aws-cpp-sdk-core PROPERTIES INTERFACE_LINK_LIBRARIES AWS::aws-crt-cpp)

# Set thirdparty libraries
set(STARROCKS_DEPENDENCIES
    ${STARROCKS_DEPENDENCIES}
    ${WL_START_GROUP}
    build_version
    rocksdb
    libs2
    snappy
    ${Boost_LIBRARIES}
    thrift
    thriftnb
    glog
    re2
    pprof
    # Put lz4 in front of librdkafka to make sure that segment use the native lz4 library to compress/decompress page
    # Otherwise, he will use the lz4 Library in librdkafka
    lz4
    libevent
    curl
    ${LIBZ}
    ${LIBBZ2}
    gflags
    brpc
    protobuf
    openssl
    crypto
    leveldb
    bitshuffle
    roaring
    # arrow compiles jemalloc with a specific prefix, we should keep it
    jemalloc_for_arrow
    brotlicommon
    brotlidec
    brotlienc
    zstd
    streamvbyte
    arrow
    parquet
    orc
    cctz
    fmt
    ryu
    hyperscan
    simdjson
    librdkafka_cpp
    librdkafka
    libpulsar
    velocypack
    opentelemetry_trace
    http_client_curl
    opentelemetry_resources
    opentelemetry_common
    opentelemetry_exporter_jaeger_trace
    ${WL_END_GROUP}
)

if (${WITH_BLOCK_CACHE} STREQUAL "ON")
    set(STARROCKS_DEPENDENCIES
        ${STARROCKS_DEPENDENCIES}
        ${WL_START_GROUP}
        ${Boost_LIBRARIES}
        ${CACHELIB_DEPENDENCIES}
        ${WL_END_GROUP}
    )
endif()

if ("${CMAKE_BUILD_TARGET_ARCH}" STREQUAL "x86" OR "${CMAKE_BUILD_TARGET_ARCH}" STREQUAL "x86_64")
    set(STARROCKS_DEPENDENCIES
        ${STARROCKS_DEPENDENCIES}
        breakpad
    )
endif()

# Add all external dependencies. They should come after the starrocks libs.
# static link gcc's lib
set(STARROCKS_LINK_LIBS ${STARROCKS_LINK_LIBS}
    ${STARROCKS_DEPENDENCIES}
    -static-libstdc++
    -static-libgcc
)

# Add sanitize static link flags or tcmalloc
if ("${CMAKE_BUILD_TYPE}" STREQUAL "DEBUG" OR "${CMAKE_BUILD_TYPE}" STREQUAL "RELEASE")
    if (USE_JEMALLOC)
        message("use jemalloc")
        add_definitions(-DUSE_JEMALLOC)
        set(STARROCKS_LINK_LIBS ${STARROCKS_LINK_LIBS} jemalloc)
    else()
        message("use tcmalloc")
        set(STARROCKS_LINK_LIBS ${STARROCKS_LINK_LIBS} tcmalloc)
    endif()
elseif ("${CMAKE_BUILD_TYPE}" STREQUAL "ASAN")
    if (CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
        set(STARROCKS_LINK_LIBS ${STARROCKS_LINK_LIBS} -static-libsan)
    else()
        set(STARROCKS_LINK_LIBS ${STARROCKS_LINK_LIBS} -static-libasan)
    endif()
elseif ("${CMAKE_BUILD_TYPE}" STREQUAL "LSAN")
    set(STARROCKS_LINK_LIBS ${STARROCKS_LINK_LIBS} -static-liblsan)
elseif ("${CMAKE_BUILD_TYPE}" STREQUAL "UBSAN")
    if (USE_JEMALLOC)
        message("use jemalloc")
        add_definitions(-DUSE_JEMALLOC)
        set(STARROCKS_LINK_LIBS ${STARROCKS_LINK_LIBS} -static-libubsan jemalloc)
    else()
        message("use tcmalloc")
        set(STARROCKS_LINK_LIBS ${STARROCKS_LINK_LIBS} -static-libubsan tcmalloc)
    endif()
elseif ("${CMAKE_BUILD_TYPE}" STREQUAL "TSAN")
    set(STARROCKS_LINK_LIBS ${STARROCKS_LINK_LIBS} -static-libtsan)
else()
    message(FATAL_ERROR "Unknown build type: ${CMAKE_BUILD_TYPE}")
endif()

set(STARROCKS_LINK_LIBS ${STARROCKS_LINK_LIBS}
    -lbfd -liberty -lc -lm -ldl -rdynamic -pthread -Wl,-wrap=__cxa_throw
)

# link gcov if WITH_GCOV is on
if (WITH_GCOV)
    set(STARROCKS_LINK_LIBS ${STARROCKS_LINK_LIBS} -lgcov)
endif()

# Set libraries for test
set (TEST_LINK_LIBS ${STARROCKS_LINK_LIBS}
    ${WL_START_GROUP}
    gmock
    gtest
    ${WL_END_GROUP}
)

# Only build static libs
set(BUILD_SHARED_LIBS OFF)

if (NOT $ENV{STARROCKS_LINKER} STREQUAL "")
    add_link_options("-fuse-ld=$ENV{STARROCKS_LINKER}")
endif ()

if (${MAKE_TEST} STREQUAL "ON")
    SET(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -DGTEST_USE_OWN_TR1_TUPLE=0")
    add_definitions(-DBE_TEST)
else()
    # output *.a, *.so, *.dylib to output/tmp
    set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${OUTPUT_DIR}/tmp/${CMAKE_BUILD_TYPE})
    set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${OUTPUT_DIR}/tmp/${CMAKE_BUILD_TYPE})
    # output *.exe to output/lib
    set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${OUTPUT_DIR}/lib)
endif ()

# Utility CMake function to make specifying tests and benchmarks less verbose
FUNCTION(ADD_BE_TEST TEST_NAME)
    set(BUILD_OUTPUT_ROOT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/")
    # This gets the directory where the test is from (e.g. 'exprs' or 'runtime')
    get_filename_component(DIR_NAME ${CMAKE_CURRENT_SOURCE_DIR} NAME)
    get_filename_component(TEST_DIR_NAME ${TEST_NAME} PATH)
    get_filename_component(TEST_FILE_NAME ${TEST_NAME} NAME)

    ADD_EXECUTABLE(${TEST_FILE_NAME} ${TEST_NAME}.cpp)
    TARGET_LINK_LIBRARIES(${TEST_FILE_NAME} ${TEST_LINK_LIBS} gtest_main)
    SET_TARGET_PROPERTIES(${TEST_FILE_NAME} PROPERTIES COMPILE_FLAGS "-fno-access-control")
    if (NOT "${TEST_DIR_NAME}" STREQUAL "")
        SET_TARGET_PROPERTIES(${TEST_FILE_NAME} PROPERTIES RUNTIME_OUTPUT_DIRECTORY "${BUILD_OUTPUT_ROOT_DIRECTORY}/${TEST_DIR_NAME}")
    endif()
    ADD_TEST(${TEST_FILE_NAME} "${BUILD_OUTPUT_ROOT_DIRECTORY}/${TEST_NAME}")
ENDFUNCTION()

# Add a benchmark to BE
FUNCTION(ADD_BE_BENCH BENCH_NAME)
    message(STATUS "Add Be Bench")
    set(BUILD_OUTPUT_ROOT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/")
    # This gets the directory where the test is from (e.g. 'exprs' or 'runtime')
    get_filename_component(DIR_NAME ${CMAKE_CURRENT_SOURCE_DIR} NAME)
    get_filename_component(BENCH_DIR_NAME ${BENCH_NAME} PATH)
    set(BENCH_OUTPUT_DIR_NAME "output")
    get_filename_component(BENCH_FILE_NAME ${BENCH_NAME} NAME)
    
    find_package(benchmark REQUIRED)
    ADD_EXECUTABLE(${BENCH_FILE_NAME} ${BENCH_NAME}.cpp)
    TARGET_LINK_LIBRARIES(${BENCH_FILE_NAME} ${TEST_LINK_LIBS} benchmark benchmark_main)

    SET_TARGET_PROPERTIES(${BENCH_FILE_NAME} PROPERTIES COMPILE_FLAGS "-fno-access-control")
    SET_TARGET_PROPERTIES(${BENCH_FILE_NAME} PROPERTIES COMPILE_FLAGS ${CXX_FLAGS_RELEASE})
    if (NOT "${BENCH_OUTPUT_DIR_NAME}" STREQUAL "")
        SET_TARGET_PROPERTIES(${BENCH_FILE_NAME} PROPERTIES RUNTIME_OUTPUT_DIRECTORY "${BUILD_OUTPUT_ROOT_DIRECTORY}/${BENCH_OUTPUT_DIR_NAME}")
    endif()
    ADD_TEST(${BENCH_FILE_NAME} "${BUILD_OUTPUT_ROOT_DIRECTORY}/${BENCH_NAME}")
ENDFUNCTION()

add_subdirectory(${SRC_DIR}/agent)
add_subdirectory(${SRC_DIR}/common)
add_subdirectory(${SRC_DIR}/connector)
add_subdirectory(${SRC_DIR}/column)
add_subdirectory(${SRC_DIR}/formats)
add_subdirectory(${SRC_DIR}/fs)
add_subdirectory(${SRC_DIR}/exec)
add_subdirectory(${SRC_DIR}/exprs)
add_subdirectory(${SRC_DIR}/gen_cpp)
add_subdirectory(${SRC_DIR}/geo)
add_subdirectory(${SRC_DIR}/gutil)
add_subdirectory(${SRC_DIR}/http)
add_subdirectory(${SRC_DIR}/io)
add_subdirectory(${SRC_DIR}/storage)
add_subdirectory(${SRC_DIR}/runtime)
add_subdirectory(${SRC_DIR}/serde)
add_subdirectory(${SRC_DIR}/service)
add_subdirectory(${SRC_DIR}/testutil)
add_subdirectory(${SRC_DIR}/types)
add_subdirectory(${SRC_DIR}/udf)

add_subdirectory(${SRC_DIR}/tools)
add_subdirectory(${SRC_DIR}/util)

if (WITH_BENCH STREQUAL "ON")
     message(STATUS "Build binary with bench")
    add_subdirectory(${SRC_DIR}/bench)
endif()

if (${WITH_BLOCK_CACHE} STREQUAL "ON")
    add_subdirectory(${SRC_DIR}/block_cache)
endif()

if (${MAKE_TEST} STREQUAL "ON")
    add_subdirectory(test)
endif ()

# Install be
install(DIRECTORY DESTINATION ${OUTPUT_DIR})
install(DIRECTORY DESTINATION ${OUTPUT_DIR}/bin)
install(DIRECTORY DESTINATION ${OUTPUT_DIR}/conf)
install(DIRECTORY DESTINATION ${OUTPUT_DIR}/lib)

install(FILES
    ${BASE_DIR}/../bin/common.sh
    ${BASE_DIR}/../bin/start_backend.sh
    ${BASE_DIR}/../bin/start_be.sh
    ${BASE_DIR}/../bin/start_cn.sh
    ${BASE_DIR}/../bin/stop_be.sh
    ${BASE_DIR}/../bin/stop_cn.sh
    ${BASE_DIR}/../bin/show_be_version.sh
    ${BASE_DIR}/../bin/meta_tool.sh
    PERMISSIONS OWNER_READ OWNER_WRITE OWNER_EXECUTE
    GROUP_READ GROUP_WRITE GROUP_EXECUTE
    WORLD_READ WORLD_EXECUTE
    DESTINATION ${OUTPUT_DIR}/bin)

install(FILES
    ${BASE_DIR}/../conf/be.conf
    ${BASE_DIR}/../conf/be_test.conf
    ${BASE_DIR}/../conf/cn.conf
    ${BASE_DIR}/../conf/hadoop_env.sh
    ${BASE_DIR}/../conf/log4j.properties
    DESTINATION ${OUTPUT_DIR}/conf)

if ("${CMAKE_BUILD_TYPE}" STREQUAL "ASAN" OR "${CMAKE_BUILD_TYPE}" STREQUAL "LSAN")
install(FILES
    ${BASE_DIR}/../conf/asan_suppressions.conf
    DESTINATION ${OUTPUT_DIR}/conf)
endif()

install(DIRECTORY
    ${BASE_DIR}/../webroot/be/
    DESTINATION ${OUTPUT_DIR}/www)

