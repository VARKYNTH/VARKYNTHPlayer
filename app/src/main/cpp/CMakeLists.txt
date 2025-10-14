cmake_minimum_required(VERSION 3.22.1)
project(varkynth_audio LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(varkynth_audiotrack SHARED audiotrack_player.cpp)

find_library(log-lib     log)
find_library(android-lib android)
find_library(aaudio-lib  aaudio)

# arm32 часто требует atomic; на arm64 не мешает
target_link_libraries(varkynth_aaudio
    ${log-lib}
    ${android-lib}
    ${aaudio-lib}
    atomic
)

# AAudio: с API 24
set_target_properties(varkynth_audiotrack PROPERTIES ANDROID_PLATFORM 28)

# install — чтобы .so гарантированно попал в артефакты
include(GNUInstallDirs)
install(TARGETS varkynth_audiotrack
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
)