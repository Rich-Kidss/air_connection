package com.airconnection.app.network

import android.content.Context
import android.util.Log
import com.airconnection.app.models.ChildDevice
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import com.airconnection.app.AppConfig

object SignalingClient {
    private const val TAG = "SignalingClient"
    
    var serverUrl: String = AppConfig.SUPABASE_URL
    var listener: SignalingListener? = null
    
    private val scope = CoroutineScope(Dispatchers.IO)
    private var channelRef: RealtimeChannel? = null
    private var isConnected = false

    private val devicesMap = mutableMapOf<String, ChildDevice>()
    private var currentParentEmail: String = ""
    private var currentDeviceId: String = ""

    interface SignalingListener {
        fun onDeviceListUpdated(devices: List<ChildDevice>) {}
        fun onMirrorFrameReceived(deviceId: String, frameBase64: String) {}
        fun onCameraFrameReceived(deviceId: String, frameBase64: String) {}
        fun onPhotosListReceived(deviceId: String, photos: List<String>) {}
        fun onCommandStartMirror() {}
        fun onCommandStopMirror() {}
        fun onCommandStartCamera(facing: String) {}
        fun onCommandStopCamera() {}
        fun onCommandSwitchCamera(facing: String) {}
        fun onCommandGetPhotos() {}
        fun onCommandTouch(action: String, x: Float, y: Float, endX: Float, endY: Float) {}
        fun onCommandUninstallPolicy(allowUninstall: Boolean) {}
        fun onCommandLock(lock: Boolean) {}
    }

    fun init(context: Context) {
        SupabaseManager.init()
        val prefs = context.getSharedPreferences("air_connection_prefs", Context.MODE_PRIVATE)
        serverUrl = prefs.getString("server_url", AppConfig.SUPABASE_URL) ?: AppConfig.SUPABASE_URL
    }

    fun saveServerUrl(context: Context, url: String) {
        var formattedUrl = url.trim()
        if (!formattedUrl.startsWith("http://") && !formattedUrl.startsWith("https://")) {
            formattedUrl = "https://$formattedUrl"
        }
        serverUrl = formattedUrl
        val prefs = context.getSharedPreferences("air_connection_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("server_url", formattedUrl).apply()
    }

    fun connect(url: String = serverUrl) {
        if (isConnected) return
        scope.launch {
            try {
                SupabaseManager.init()
                val ch = SupabaseManager.client.realtime.channel("air_connection_signal")
                channelRef = ch
                
                ch.broadcastFlow<JsonObject>(event = "signal").onEach { payload: JsonObject ->
                    handleSignalPayload(payload)
                }.launchIn(scope)

                ch.subscribe()
                isConnected = true
                Log.d(TAG, "Connected to Supabase Realtime Signaling Channel!")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect Supabase Realtime channel", e)
            }
        }
    }

    private fun handleSignalPayload(payload: JsonObject) {
        try {
            val event = payload["event"]?.jsonPrimitive?.content ?: return
            val targetDeviceId = payload["deviceId"]?.jsonPrimitive?.content ?: ""
            val targetParentEmail = payload["parentEmail"]?.jsonPrimitive?.content ?: ""

            when (event) {
                "register_child" -> {
                    val deviceId = payload["deviceId"]?.jsonPrimitive?.content ?: ""
                    val parentEmail = payload["parentEmail"]?.jsonPrimitive?.content ?: ""
                    val deviceName = payload["deviceName"]?.jsonPrimitive?.content ?: ""
                    val customName = payload["customName"]?.jsonPrimitive?.content ?: ""
                    val batteryLevel = payload["batteryLevel"]?.jsonPrimitive?.int ?: 100
                    val model = payload["model"]?.jsonPrimitive?.content ?: ""
                    val canUninstall = payload["canUninstall"]?.jsonPrimitive?.boolean ?: false
                    val isLocked = payload["isLocked"]?.jsonPrimitive?.boolean ?: false

                    val dev = ChildDevice(
                        deviceId = deviceId,
                        parentEmail = parentEmail,
                        deviceName = deviceName,
                        customName = customName,
                        batteryLevel = batteryLevel,
                        model = model,
                        status = "online",
                        canUninstall = canUninstall,
                        isLocked = isLocked
                    )
                    devicesMap[deviceId] = dev
                    notifyParentDeviceList(parentEmail)
                }

                "request_device_list" -> {
                    if (currentDeviceId.isNotEmpty() && devicesMap.containsKey(currentDeviceId)) {
                        val dev = devicesMap[currentDeviceId]
                        if (dev != null && dev.parentEmail == targetParentEmail) {
                            broadcastSignal(
                                buildJsonObject {
                                    put("event", JsonPrimitive("register_child"))
                                    put("deviceId", JsonPrimitive(dev.deviceId))
                                    put("parentEmail", JsonPrimitive(dev.parentEmail))
                                    put("deviceName", JsonPrimitive(dev.deviceName))
                                    put("customName", JsonPrimitive(dev.customName))
                                    put("batteryLevel", JsonPrimitive(dev.batteryLevel))
                                    put("model", JsonPrimitive(dev.model))
                                    put("canUninstall", JsonPrimitive(dev.canUninstall))
                                    put("isLocked", JsonPrimitive(dev.isLocked))
                                }
                            )
                        }
                    }
                }

                "device_list_update" -> {
                    if (currentParentEmail.isNotEmpty() && targetParentEmail == currentParentEmail) {
                        val devicesArr = payload["devices"]?.jsonArray
                        val list = mutableListOf<ChildDevice>()
                        devicesArr?.forEach { elem ->
                            val obj = elem.jsonObject
                            list.add(
                                ChildDevice(
                                    deviceId = obj["deviceId"]?.jsonPrimitive?.content ?: "",
                                    parentEmail = obj["parentEmail"]?.jsonPrimitive?.content ?: "",
                                    deviceName = obj["deviceName"]?.jsonPrimitive?.content ?: "",
                                    customName = obj["customName"]?.jsonPrimitive?.content ?: "",
                                    batteryLevel = obj["batteryLevel"]?.jsonPrimitive?.int ?: 100,
                                    model = obj["model"]?.jsonPrimitive?.content ?: "",
                                    status = obj["status"]?.jsonPrimitive?.content ?: "online",
                                    canUninstall = obj["canUninstall"]?.jsonPrimitive?.boolean ?: false,
                                    isLocked = obj["isLocked"]?.jsonPrimitive?.boolean ?: false
                                )
                            )
                        }
                        listener?.onDeviceListUpdated(list)
                    }
                }

                "mirror_frame" -> {
                    val devId = payload["deviceId"]?.jsonPrimitive?.content ?: ""
                    val frame = payload["frameBase64"]?.jsonPrimitive?.content ?: ""
                    if (currentParentEmail.isNotEmpty() && targetParentEmail == currentParentEmail) {
                        listener?.onMirrorFrameReceived(devId, frame)
                    }
                }

                "camera_frame" -> {
                    val devId = payload["deviceId"]?.jsonPrimitive?.content ?: ""
                    val frame = payload["frameBase64"]?.jsonPrimitive?.content ?: ""
                    if (currentParentEmail.isNotEmpty() && targetParentEmail == currentParentEmail) {
                        listener?.onCameraFrameReceived(devId, frame)
                    }
                }

                "photos_list" -> {
                    val devId = payload["deviceId"]?.jsonPrimitive?.content ?: ""
                    val photosArr = payload["photos"]?.jsonArray
                    val list = mutableListOf<String>()
                    photosArr?.forEach { list.add(it.jsonPrimitive.content) }
                    if (currentParentEmail.isNotEmpty() && targetParentEmail == currentParentEmail) {
                        listener?.onPhotosListReceived(devId, list)
                    }
                }

                "start_mirror" -> {
                    if (currentDeviceId == targetDeviceId) {
                        listener?.onCommandStartMirror()
                    }
                }

                "stop_mirror" -> {
                    if (currentDeviceId == targetDeviceId) {
                        listener?.onCommandStopMirror()
                    }
                }

                "start_camera" -> {
                    if (currentDeviceId == targetDeviceId) {
                        val facing = payload["facing"]?.jsonPrimitive?.content ?: "back"
                        listener?.onCommandStartCamera(facing)
                    }
                }

                "stop_camera" -> {
                    if (currentDeviceId == targetDeviceId) {
                        listener?.onCommandStopCamera()
                    }
                }

                "switch_camera" -> {
                    if (currentDeviceId == targetDeviceId) {
                        val facing = payload["facing"]?.jsonPrimitive?.content ?: "back"
                        listener?.onCommandSwitchCamera(facing)
                    }
                }

                "get_photos" -> {
                    if (currentDeviceId == targetDeviceId) {
                        listener?.onCommandGetPhotos()
                    }
                }

                "command_touch" -> {
                    if (currentDeviceId == targetDeviceId) {
                        val action = payload["action"]?.jsonPrimitive?.content ?: ""
                        val x = payload["x"]?.jsonPrimitive?.double?.toFloat() ?: 0f
                        val y = payload["y"]?.jsonPrimitive?.double?.toFloat() ?: 0f
                        val endX = payload["endX"]?.jsonPrimitive?.double?.toFloat() ?: 0f
                        val endY = payload["endY"]?.jsonPrimitive?.double?.toFloat() ?: 0f
                        listener?.onCommandTouch(action, x, y, endX, endY)
                    }
                }

                "command_uninstall_policy" -> {
                    if (currentDeviceId == targetDeviceId) {
                        val allowUninstall = payload["allowUninstall"]?.jsonPrimitive?.boolean ?: false
                        listener?.onCommandUninstallPolicy(allowUninstall)
                    }
                }

                "command_lock" -> {
                    if (currentDeviceId == targetDeviceId) {
                        val lock = payload["lock"]?.jsonPrimitive?.boolean ?: false
                        listener?.onCommandLock(lock)
                    }
                }

                "rename_child" -> {
                    val devId = payload["deviceId"]?.jsonPrimitive?.content ?: ""
                    val newName = payload["newCustomName"]?.jsonPrimitive?.content ?: ""
                    if (devicesMap.containsKey(devId)) {
                        val dev = devicesMap[devId]!!
                        dev.customName = newName
                        devicesMap[devId] = dev
                        notifyParentDeviceList(dev.parentEmail)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing signal payload", e)
        }
    }

    private fun broadcastSignal(payload: JsonObject) {
        scope.launch {
            try {
                if (channelRef == null) {
                    connect()
                }
                channelRef?.broadcast(event = "signal", message = payload)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to broadcast signal", e)
            }
        }
    }

    private fun notifyParentDeviceList(parentEmail: String) {
        val parentDevices = devicesMap.values.filter { it.parentEmail == parentEmail }
        val devicesJsonArr = buildJsonArray {
            parentDevices.forEach { dev ->
                add(
                    buildJsonObject {
                        put("deviceId", JsonPrimitive(dev.deviceId))
                        put("parentEmail", JsonPrimitive(dev.parentEmail))
                        put("deviceName", JsonPrimitive(dev.deviceName))
                        put("customName", JsonPrimitive(dev.customName))
                        put("batteryLevel", JsonPrimitive(dev.batteryLevel))
                        put("model", JsonPrimitive(dev.model))
                        put("status", JsonPrimitive(dev.status))
                        put("canUninstall", JsonPrimitive(dev.canUninstall))
                        put("isLocked", JsonPrimitive(dev.isLocked))
                    }
                )
            }
        }

        broadcastSignal(
            buildJsonObject {
                put("event", JsonPrimitive("device_list_update"))
                put("parentEmail", JsonPrimitive(parentEmail))
                put("devices", devicesJsonArr)
            }
        )

        if (currentParentEmail == parentEmail) {
            listener?.onDeviceListUpdated(parentDevices)
        }
    }

    fun registerParent(email: String) {
        currentParentEmail = email
        connect()
        broadcastSignal(
            buildJsonObject {
                put("event", JsonPrimitive("request_device_list"))
                put("parentEmail", JsonPrimitive(email))
            }
        )
    }

    fun registerChild(
        deviceId: String,
        parentEmail: String,
        deviceName: String,
        customName: String,
        batteryLevel: Int,
        model: String
    ) {
        currentDeviceId = deviceId
        connect()

        val dev = ChildDevice(
            deviceId = deviceId,
            parentEmail = parentEmail,
            deviceName = deviceName,
            customName = customName,
            batteryLevel = batteryLevel,
            model = model,
            status = "online"
        )
        devicesMap[deviceId] = dev

        broadcastSignal(
            buildJsonObject {
                put("event", JsonPrimitive("register_child"))
                put("deviceId", JsonPrimitive(deviceId))
                put("parentEmail", JsonPrimitive(parentEmail))
                put("deviceName", JsonPrimitive(deviceName))
                put("customName", JsonPrimitive(customName))
                put("batteryLevel", JsonPrimitive(batteryLevel))
                put("model", JsonPrimitive(model))
                put("canUninstall", JsonPrimitive(dev.canUninstall))
                put("isLocked", JsonPrimitive(dev.isLocked))
            }
        )
    }

    fun renameChild(deviceId: String, newName: String) {
        if (devicesMap.containsKey(deviceId)) {
            val dev = devicesMap[deviceId]!!
            dev.customName = newName
            devicesMap[deviceId] = dev
            notifyParentDeviceList(dev.parentEmail)
        }
        broadcastSignal(
            buildJsonObject {
                put("event", JsonPrimitive("rename_child"))
                put("deviceId", JsonPrimitive(deviceId))
                put("newCustomName", JsonPrimitive(newName))
            }
        )
    }

    fun startMirroring(deviceId: String) {
        broadcastSignal(
            buildJsonObject {
                put("event", JsonPrimitive("start_mirror"))
                put("deviceId", JsonPrimitive(deviceId))
            }
        )
    }

    fun sendScreenFrame(parentEmail: String, deviceId: String, frameBase64: String) {
        broadcastSignal(
            buildJsonObject {
                put("event", JsonPrimitive("mirror_frame"))
                put("parentEmail", JsonPrimitive(parentEmail))
                put("deviceId", JsonPrimitive(deviceId))
                put("frameBase64", JsonPrimitive(frameBase64))
            }
        )
    }

    fun sendTouchEvent(deviceId: String, action: String, x: Float, y: Float, endX: Float = 0f, endY: Float = 0f) {
        broadcastSignal(
            buildJsonObject {
                put("event", JsonPrimitive("command_touch"))
                put("deviceId", JsonPrimitive(deviceId))
                put("action", JsonPrimitive(action))
                put("x", JsonPrimitive(x))
                put("y", JsonPrimitive(y))
                put("endX", JsonPrimitive(endX))
                put("endY", JsonPrimitive(endY))
            }
        )
    }

    fun setUninstallLock(deviceId: String, allowUninstall: Boolean) {
        if (devicesMap.containsKey(deviceId)) {
            val dev = devicesMap[deviceId]!!
            dev.canUninstall = allowUninstall
            devicesMap[deviceId] = dev
            notifyParentDeviceList(dev.parentEmail)
        }
        broadcastSignal(
            buildJsonObject {
                put("event", JsonPrimitive("command_uninstall_policy"))
                put("deviceId", JsonPrimitive(deviceId))
                put("allowUninstall", JsonPrimitive(allowUninstall))
            }
        )
    }

    fun lockDevice(deviceId: String, lock: Boolean) {
        if (devicesMap.containsKey(deviceId)) {
            val dev = devicesMap[deviceId]!!
            dev.isLocked = lock
            devicesMap[deviceId] = dev
            notifyParentDeviceList(dev.parentEmail)
        }
        broadcastSignal(
            buildJsonObject {
                put("event", JsonPrimitive("command_lock"))
                put("deviceId", JsonPrimitive(deviceId))
                put("lock", JsonPrimitive(lock))
            }
        )
    }

    fun startCamera(deviceId: String, facing: String = "back") {
        broadcastSignal(
            buildJsonObject {
                put("event", JsonPrimitive("start_camera"))
                put("deviceId", JsonPrimitive(deviceId))
                put("facing", JsonPrimitive(facing))
            }
        )
    }

    fun stopCamera(deviceId: String) {
        broadcastSignal(
            buildJsonObject {
                put("event", JsonPrimitive("stop_camera"))
                put("deviceId", JsonPrimitive(deviceId))
            }
        )
    }

    fun switchCamera(deviceId: String, facing: String) {
        broadcastSignal(
            buildJsonObject {
                put("event", JsonPrimitive("switch_camera"))
                put("deviceId", JsonPrimitive(deviceId))
                put("facing", JsonPrimitive(facing))
            }
        )
    }

    fun sendCameraFrame(parentEmail: String, deviceId: String, frameBase64: String) {
        broadcastSignal(
            buildJsonObject {
                put("event", JsonPrimitive("camera_frame"))
                put("parentEmail", JsonPrimitive(parentEmail))
                put("deviceId", JsonPrimitive(deviceId))
                put("frameBase64", JsonPrimitive(frameBase64))
            }
        )
    }

    fun getPhotos(deviceId: String) {
        broadcastSignal(
            buildJsonObject {
                put("event", JsonPrimitive("get_photos"))
                put("deviceId", JsonPrimitive(deviceId))
            }
        )
    }

    fun sendPhotosList(parentEmail: String, deviceId: String, photos: List<String>) {
        val jsonArr = buildJsonArray {
            photos.forEach { add(JsonPrimitive(it)) }
        }
        broadcastSignal(
            buildJsonObject {
                put("event", JsonPrimitive("photos_list"))
                put("parentEmail", JsonPrimitive(parentEmail))
                put("deviceId", JsonPrimitive(deviceId))
                put("photos", jsonArr)
            }
        )
    }
}
