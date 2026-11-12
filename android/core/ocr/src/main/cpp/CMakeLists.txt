cmake_minimum_required(VERSION 3.22)
project(flowfolio_tesseract LANGUAGES CXX)

# TESSERACT_ROOT is passed from the flowfolio.tesseract Gradle plugin: the cross-compiled libtesseract.so and
# libleptonica.so per ABI under jniLibs/, and the C API headers under include/.
set(TESSERACT_ABI_DIR "${TESSERACT_ROOT}/jniLibs/${ANDROID_ABI}")

add_library(tesseract SHARED IMPORTED)
set_target_properties(tesseract PROPERTIES IMPORTED_LOCATION "${TESSERACT_ABI_DIR}/libtesseract.so")

add_library(leptonica SHARED IMPORTED)
set_target_properties(leptonica PROPERTIES IMPORTED_LOCATION "${TESSERACT_ABI_DIR}/libleptonica.so")

add_library(flowfolio_tesseract SHARED tesseract_bridge.cpp)
target_include_directories(flowfolio_tesseract PRIVATE "${TESSERACT_ROOT}/include")
target_compile_options(flowfolio_tesseract PRIVATE -Wall -Wextra -Werror -O2 -fstack-protector-strong)
target_link_libraries(flowfolio_tesseract tesseract leptonica log)
