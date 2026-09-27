package com.airconnection.app.models

data class ChildDevice(
    val deviceId: String,
    val parentEmail: String,
    val deviceName: String,
    var customName: String,
    var batteryLevel: Int = 100,
    var model: String = "Android Device",
    var status: String = "online",
    var canUninstall: Boolean = false,
    var isLocked: Boolean = false
)
