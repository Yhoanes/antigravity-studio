cmake_minimum_required(VERSION 3.22.1)

project("antigravity-pty")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(
    antigravity-pty
    SHARED
    native-pty.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    antigravity-pty
    ${log-lib}
)
