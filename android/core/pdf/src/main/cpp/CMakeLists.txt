cmake_minimum_required(VERSION 3.22)
project(flowfolio_pdfium LANGUAGES CXX)

# PDFIUM_ROOT is passed from the flowfolio.pdfium Gradle plugin; one unpacked pdfium-binaries tree per ABI.
set(PDFIUM_ABI_DIR "${PDFIUM_ROOT}/${ANDROID_ABI}")

add_library(pdfium SHARED IMPORTED)
set_target_properties(pdfium PROPERTIES IMPORTED_LOCATION "${PDFIUM_ABI_DIR}/lib/libpdfium.so")

add_library(flowfolio_pdfium SHARED pdfium_jni.cpp)
target_include_directories(flowfolio_pdfium PRIVATE "${PDFIUM_ABI_DIR}/include")
target_compile_options(flowfolio_pdfium PRIVATE -Wall -Wextra -Werror -O2 -fstack-protector-strong)
target_link_libraries(flowfolio_pdfium pdfium jnigraphics log)
