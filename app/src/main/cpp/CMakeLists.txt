cmake_minimum_required(VERSION 3.10.2)

project("aegismesh")

# Add all our C++ files into one shared library
add_library(
        aegismesh-native
        SHARED
        native-lib.cpp
        gesture_processing.cpp
        ble_mesh.cpp
        wifi_direct.cpp ) # We can implement wifi_direct.cpp later

# Find libraries provided by the Android NDK
find_library( log-lib log )
find_library( android-lib android )

# Link our native library against the NDK libraries
target_link_libraries(
        aegismesh-native
        ${log-lib}
        ${android-lib} )