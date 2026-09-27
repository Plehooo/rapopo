# CI Fix 2026

The Android build workflow uses `android-actions/setup-android@v4` with `packages: ''`.
This avoids the removed legacy `tools` SDK package. Required SDK components are installed
explicitly with `sdkmanager`: platform-tools, API 36 platform, build-tools 36.0.0, CMake 3.22.1,
and NDK 26.3.11579264.
