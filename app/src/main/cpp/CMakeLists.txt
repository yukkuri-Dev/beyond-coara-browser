cmake_minimum_required(VERSION 3.4.1)
project("highlight_native")

set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -DNDEBUG -fstack-protector-strong -D_FORTIFY_SOURCE=2 -fPIE -pie")

add_library(highlight_native SHARED
            highlight_native.cpp)


