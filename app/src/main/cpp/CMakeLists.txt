cmake_minimum_required(VERSION 3.22.1)

project("ah_engine" CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(
    ah_engine
    SHARED
    ah_engine.cpp
    render/GpuRenderEngine.cpp
    render/NextGenGpuCompositionEngine.cpp
    native_bridge.cpp
)

find_library(gles-lib GLESv3)
find_library(egl-lib EGL)
find_library(android-lib android)
find_library(log-lib log)

target_link_libraries(
    ah_engine
    ${gles-lib}
    ${egl-lib}
    ${android-lib}
    ${log-lib}
)
