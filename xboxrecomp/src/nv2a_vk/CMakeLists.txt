cmake_minimum_required(VERSION 3.20)
project(nv2a_vk C)

# Vulkan 1.3 renderer for the pushbuffer executor (XBOXRECOMP_VULKAN=ON):
# the Switch's NVK from mesa-switch, linked statically (NVK_SDK), or any
# Vulkan driver on Linux. Shares the shader generators with nv2a_gl, in
# their Vulkan dialect, and compiles them with glslang at run time.
add_library(xbox_nv2a_vk STATIC
    nv2a_vk.c
    ../nv2a_gl/gl_vsh.c
    ../nv2a_gl/gl_psh.c
)
target_include_directories(xbox_nv2a_vk PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

find_package(glslang CONFIG REQUIRED)
target_link_libraries(xbox_nv2a_vk PUBLIC glslang::glslang glslang::glslang-default-resource-limits)

find_package(Vulkan REQUIRED)
find_package(PkgConfig REQUIRED)
pkg_check_modules(SDL2 REQUIRED IMPORTED_TARGET sdl2)
# The distribution glslang is built with its optimizer (static SPIRV-Tools).
target_link_libraries(xbox_nv2a_vk PUBLIC Vulkan::Vulkan PkgConfig::SDL2
    SPIRV-Tools-opt SPIRV-Tools stdc++)
if(NOT MSVC)
    target_compile_options(xbox_nv2a_vk PRIVATE -Wall -Wextra -Wno-unused-parameter -Wno-missing-field-initializers -O2)
endif()
