cmake_minimum_required(VERSION 3.22.1)
project("seven_tv_webp")

set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)

set(WEBP_BUILD_ANIM_UTILS OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_CWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_DWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_GIF2WEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_IMG2WEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_VWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_WEBPINFO OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_WEBPMUX OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_EXTRAS OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    libwebp
    GIT_REPOSITORY https://github.com/webmproject/libwebp.git
    GIT_TAG        v1.4.0
    GIT_SHALLOW    TRUE
)

FetchContent_MakeAvailable(libwebp)

add_library(
    seven_tv_webp
    SHARED
    webp_anim_encoder.cpp
)

target_include_directories(
    seven_tv_webp
    PRIVATE
    ${libwebp_SOURCE_DIR}/src
)

if(TARGET libwebpmux AND NOT TARGET webpmux)
    add_library(webpmux ALIAS libwebpmux)
endif()

if(TARGET libwebpdemux AND NOT TARGET webpdemux)
    add_library(webpdemux ALIAS libwebpdemux)
endif()

if(TARGET libwebp AND NOT TARGET webp)
    add_library(webp ALIAS libwebp)
endif()

target_link_libraries(
    seven_tv_webp
    PRIVATE
    webp
    webpmux
    webpdemux
    jnigraphics
    log
)
