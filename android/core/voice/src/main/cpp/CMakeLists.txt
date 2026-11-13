cmake_minimum_required(VERSION 3.22)
project(flowfolio_ort LANGUAGES CXX)

# The OCR bridge (ADR-0054) to Microsoft's ONNX Runtime (ADR-0059). ORT_INCLUDE (its C headers) and ORT_JNI (the
# extracted AAR's jni/ tree, one directory per ABI) are passed from the flowfolio.onnxruntime Gradle plugin.
add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES IMPORTED_LOCATION "${ORT_JNI}/${ANDROID_ABI}/libonnxruntime.so")

add_library(flowfolio_ort SHARED ort_bridge.cpp)
target_include_directories(flowfolio_ort PRIVATE "${ORT_INCLUDE}")
target_compile_options(flowfolio_ort PRIVATE -Wall -Wextra -Werror -O2 -fstack-protector-strong)
target_link_libraries(flowfolio_ort onnxruntime log)
