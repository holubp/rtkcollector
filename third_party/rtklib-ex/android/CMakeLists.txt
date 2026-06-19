cmake_minimum_required(VERSION 3.22)

project(rtkcollector_rtklib_shell C)

# This file is intentionally not wired into Gradle yet. It documents the future
# Android native build boundary while the pinned RTKLIB-EX checkout and exact
# source list are still being selected. The native bridge must not open serial
# devices, sockets or files; Kotlin owns recording and session artifacts.
