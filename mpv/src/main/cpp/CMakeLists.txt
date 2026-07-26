cmake_minimum_required(VERSION 3.22.1)

project("mpvplayer")

# 只有在 ARM 架構下才進行編譯和連結，避免 x86 同步失敗
if (ANDROID_ABI STREQUAL "armeabi-v7a" OR ANDROID_ABI STREQUAL "arm64-v8a")

    # Path to prebuilt libraries
    set(JNI_LIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

    # Define imported libraries
    add_library(avcodec SHARED IMPORTED)
    set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION ${JNI_LIBS_DIR}/libavcodec.so)

    add_library(avutil SHARED IMPORTED)
    set_target_properties(avutil PROPERTIES IMPORTED_LOCATION ${JNI_LIBS_DIR}/libavutil.so)

    add_library(avformat SHARED IMPORTED)
    set_target_properties(avformat PROPERTIES IMPORTED_LOCATION ${JNI_LIBS_DIR}/libavformat.so)

    add_library(swscale SHARED IMPORTED)
    set_target_properties(swscale PROPERTIES IMPORTED_LOCATION ${JNI_LIBS_DIR}/libswscale.so)

    add_library(mpv SHARED IMPORTED)
    set_target_properties(mpv PROPERTIES IMPORTED_LOCATION ${JNI_LIBS_DIR}/libmpv.so)

    # Include directories
    include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

    add_library(player SHARED
            main.cpp
            render.cpp
            log.cpp
            jni_utils.cpp
            property.cpp
            event.cpp
            thumbnail.cpp
    )

    target_link_libraries(player
            mpv
            avcodec
            avutil
            avformat
            swscale
            log
            android
    )
else()
    # 對於 x86 等架構，建立一個空庫避免 Gradle 報錯
    add_library(player SHARED UNUSED.cpp)
endif()
