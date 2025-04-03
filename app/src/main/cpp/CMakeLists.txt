cmake_minimum_required(VERSION 3.4.1)
project("highlight_native")

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Os -DNDEBUG -fstack-protector-strong -D_FORTIFY_SOURCE=2 -fPIE -pie -ffunction-sections -fdata-sections")

set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -flto")

add_library(highlight_native SHARED
            highlight_native.cpp)
