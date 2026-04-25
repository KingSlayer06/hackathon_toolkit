package com.kingslayer06.vox.data

/**
 * Backend base URL.
 *
 * Defaults are platform-specific via expect/actual:
 *   - Android emulator: http://10.0.2.2:8080  (host loopback)
 *   - iOS simulator:    http://localhost:8080
 *
 * On real devices, override [VoxConfig.baseUrl] before constructing the API
 * (or surface a settings screen later).
 */
expect val defaultBaseUrl: String

object VoxConfig {
    var baseUrl: String = defaultBaseUrl
}
