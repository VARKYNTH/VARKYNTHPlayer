cmake_minimum_required(VERSION 3.22.1)
project(varkynth_audio)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(varkynth_aaudio SHARED aaudio_player.cpp)

find_library(log-lib log)
# Линкуем AAudio + log
target_link_libraries(varkynth_aaudio ${log-lib} aaudio)

# Требуем минимум API 26 (AAudio доступен с 26)
set_target_properties(varkynth_aaudio PROPERTIES ANDROID_PLATFORM 26)