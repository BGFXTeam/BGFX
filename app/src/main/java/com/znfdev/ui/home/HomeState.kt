package com.znfdev.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Backing state for [HomeScreen]. Only what the UI actually reads.
 *
 * `isServiceRunning` drives the Start/Stop button label + color; it is
 * refreshed from [com.znfdev.bgfx.OverlayService.isRunning] on every
 * `onResume` and toggled locally on user tap.
 */
class HomeState {
    var version by mutableStateOf("")
    var server by mutableStateOf(ServerStatus.Connecting)
    var hasStorage by mutableStateOf(false)
    var hasOverlay by mutableStateOf(false)
    var isServiceRunning by mutableStateOf(false)
}

enum class ServerStatus { Connecting, Online, Offline }
