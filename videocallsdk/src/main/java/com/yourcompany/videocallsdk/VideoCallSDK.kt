package com.yourcompany.videocallsdk

import android.content.Context
import android.view.View
import androidx.camera.core.UseCase
import androidx.lifecycle.ProcessLifecycleOwner
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.renderer.TextureViewRenderer
import io.livekit.android.room.Room
import io.livekit.android.room.participant.Participant
import io.livekit.android.room.participant.RemoteParticipant
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import io.livekit.android.room.track.video.CameraCapturerUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import livekit.org.webrtc.CameraXHelper
import livekit.org.webrtc.RendererCommon
import java.util.concurrent.ConcurrentHashMap

/**
 * 统一版原生视频通话 SDK。
 *
 * - 1 对 1 与多人会议均使用 LiveKit Native Android SDK。
 * - 不使用 WebView/H5/浏览器控件。
 * - Token 必须由可信后端生成，API Secret 不进入 APK。
 */
class VideoCallSDK(
    context: Context,
    private val tokenEndpoint: String? = null,
    listener: VideoCallListener? = null
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val room: Room

    @Volatile
    var listener: VideoCallListener? = listener

    private var eventJob: Job? = null
    private var currentIdentity: String? = null
    private var currentName: String? = null
    private var currentRoomName: String? = null

    private val participants = ConcurrentHashMap<String, ParticipantInfo>()
    private val videoTracks = ConcurrentHashMap<String, VideoTrack>()
    private val rendererBindings = ConcurrentHashMap<TextureViewRenderer, Pair<String, VideoTrack>>()

    private var cameraProvider: CameraCapturerUtils.CameraProvider? = null

    init {
        // 优先注册 LiveKit CameraX provider，避免部分机型直接使用旧 Camera2 capturer 时出现兼容问题。
        runCatching {
            val provider = CameraXHelper.createCameraProvider(
                ProcessLifecycleOwner.get(),
                emptyArray<UseCase>()
            )
            if (provider.isSupported(appContext)) {
                CameraCapturerUtils.registerCameraProvider(provider)
                cameraProvider = provider
            }
        }

        room = LiveKit.create(appContext)
        startEventCollector()
    }

    /** 通过后端 Token Endpoint 加入房间。 */
    fun joinRoom(roomName: String, identity: String, name: String) {
        val endpoint = tokenEndpoint
        if (endpoint.isNullOrBlank()) {
            listener?.onError("未配置 tokenEndpoint；请改用 joinRoomWithToken()")
            return
        }

        scope.launch {
            listener?.onConnecting(roomName)
            runCatching {
                TokenClient.fetch(endpoint, roomName, identity, name)
            }.onSuccess { info ->
                connect(info)
            }.onFailure { e ->
                listener?.onError("获取 LiveKit Token 失败: ${e.message}", e)
            }
        }
    }

    /** 业务方已有 Token 时可直接连接。 */
    fun joinRoomWithToken(
        serverUrl: String,
        token: String,
        roomName: String,
        identity: String,
        name: String
    ) {
        scope.launch {
            listener?.onConnecting(roomName)
            connect(ConnectionInfo(roomName, identity, name, serverUrl, token))
        }
    }

    private suspend fun connect(info: ConnectionInfo) {
        runCatching {
            if (currentRoomName != null) {
                leaveRoomInternal("切换房间")
            }

            currentIdentity = info.identity
            currentName = info.name
            currentRoomName = info.roomName

            room.connect(info.serverUrl, info.token)

            val local = ParticipantInfo(info.identity, info.name, true)
            participants[info.identity] = local
            listener?.onConnected(info.roomName, local)
            listener?.onParticipantJoined(local)

            // ParticipantConnected 不会为“已经在房间里的参与者”补发，因此连接后主动同步一次。
            room.remoteParticipants.values.forEach { remote ->
                val participant = toInfo(remote)
                participants[participant.identity] = participant
                listener?.onParticipantJoined(participant)

                val existingTrack = remote.getTrackPublication(Track.Source.CAMERA)?.track
                if (existingTrack is VideoTrack) {
                    videoTracks[participant.identity] = existingTrack
                    listener?.onVideoAvailable(participant)
                }
            }

            room.localParticipant.setMicrophoneEnabled(true)
            room.localParticipant.setCameraEnabled(true)

            val localTrack = room.localParticipant
                .getTrackPublication(Track.Source.CAMERA)
                ?.track as? LocalVideoTrack

            if (localTrack != null) {
                videoTracks[info.identity] = localTrack
                listener?.onVideoAvailable(local)
            }
        }.onFailure { e ->
            listener?.onError("连接 LiveKit 房间失败: ${e.message}", e)
        }
    }

    private fun startEventCollector() {
        eventJob?.cancel()
        eventJob = scope.launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.ParticipantConnected -> {
                        val info = toInfo(event.participant)
                        participants[info.identity] = info
                        listener?.onParticipantJoined(info)
                    }

                    is RoomEvent.ParticipantDisconnected -> {
                        val info = toInfo(event.participant)
                        participants.remove(info.identity)
                        videoTracks.remove(info.identity)
                        detachParticipantRenderers(info.identity)
                        listener?.onParticipantLeft(info)
                    }

                    is RoomEvent.TrackSubscribed -> {
                        val track = event.track
                        if (track is VideoTrack) {
                            val info = toInfo(event.participant)
                            participants[info.identity] = info
                            videoTracks[info.identity] = track
                            listener?.onVideoAvailable(info)
                        }
                    }

                    is RoomEvent.TrackUnsubscribed -> {
                        if (event.track is VideoTrack) {
                            val info = toInfo(event.participant)
                            videoTracks.remove(info.identity)
                            detachParticipantRenderers(info.identity)
                            listener?.onVideoUnavailable(info.identity)
                        }
                    }

                    is RoomEvent.Reconnecting -> listener?.onReconnecting()
                    is RoomEvent.Reconnected -> listener?.onReconnected()

                    is RoomEvent.Disconnected -> {
                        listener?.onDisconnected(event.reason.toString())
                    }

                    is RoomEvent.FailedToConnect -> {
                        listener?.onError("LiveKit 连接失败: ${event.error.message}", event.error)
                    }

                    else -> Unit
                }
            }
        }
    }

    fun setCameraEnabled(enabled: Boolean) {
        scope.launch {
            runCatching {
                room.localParticipant.setCameraEnabled(enabled)
            }.onSuccess {
                val identity = currentIdentity ?: return@onSuccess
                val info = participants[identity] ?: return@onSuccess
                val track = room.localParticipant
                    .getTrackPublication(Track.Source.CAMERA)
                    ?.track as? LocalVideoTrack

                if (enabled && track != null) {
                    videoTracks[identity] = track
                    listener?.onVideoAvailable(info)
                } else if (!enabled) {
                    listener?.onVideoUnavailable(identity)
                }
            }.onFailure { e ->
                listener?.onError("切换摄像头状态失败: ${e.message}", e)
            }
        }
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        scope.launch {
            runCatching {
                room.localParticipant.setMicrophoneEnabled(enabled)
            }.onFailure { e ->
                listener?.onError("切换麦克风状态失败: ${e.message}", e)
            }
        }
    }

    fun switchCamera() {
        runCatching {
            val track = room.localParticipant
                .getTrackPublication(Track.Source.CAMERA)
                ?.track as? LocalVideoTrack
                ?: error("当前没有可切换的本地摄像头 Track")
            track.switchCamera()
        }.onFailure { e ->
            listener?.onError("切换前后摄像头失败: ${e.message}", e)
        }
    }

    /** 创建一个 SDK 已初始化好的原生 TextureView 视频控件。 */
    fun createVideoView(context: Context): View {
        return TextureViewRenderer(context).apply {
            room.initVideoRenderer(this)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
            setEnableHardwareScaler(true)
        }
    }

    /** 将指定参与者的视频绑定到 createVideoView() 返回的 View。 */
    fun attachVideo(participantIdentity: String, view: View, mirror: Boolean? = null): Boolean {
        val renderer = view as? TextureViewRenderer ?: return false
        val track = videoTracks[participantIdentity] ?: return false

        detachVideo(renderer)
        renderer.setMirror(mirror ?: (participantIdentity == currentIdentity))
        track.addRenderer(renderer)
        rendererBindings[renderer] = participantIdentity to track
        return true
    }

    fun detachVideo(view: View) {
        val renderer = view as? TextureViewRenderer ?: return
        val binding = rendererBindings.remove(renderer) ?: return
        runCatching { binding.second.removeRenderer(renderer) }
    }

    /** Activity/Fragment 销毁视图时调用，释放 EGL/Texture 资源。 */
    fun releaseVideoView(view: View) {
        val renderer = view as? TextureViewRenderer ?: return
        detachVideo(renderer)
        runCatching { renderer.release() }
    }

    fun getParticipants(): List<ParticipantInfo> =
        participants.values.sortedWith(compareByDescending<ParticipantInfo> { it.isLocal }.thenBy { it.name })

    fun leaveRoom() {
        scope.launch { leaveRoomInternal("用户退出") }
    }

    private fun leaveRoomInternal(reason: String) {
        rendererBindings.keys.toList().forEach { detachVideo(it) }
        videoTracks.clear()
        participants.clear()
        currentIdentity = null
        currentName = null
        currentRoomName = null
        room.disconnect()
        listener?.onDisconnected(reason)
    }

    fun release() {
        runCatching { leaveRoomInternal("SDK 释放") }
        eventJob?.cancel()
        scope.cancel()
        runCatching { room.release() }
        cameraProvider?.let { provider ->
            runCatching { CameraCapturerUtils.unregisterCameraProvider(provider) }
        }
        cameraProvider = null
    }

    private fun toInfo(participant: Participant): ParticipantInfo {
        val identity = participant.identity?.value ?: participant.sid.value
        return ParticipantInfo(
            identity = identity,
            name = participant.name?.takeIf { it.isNotBlank() } ?: identity,
            isLocal = identity == currentIdentity
        )
    }

    private fun detachParticipantRenderers(identity: String) {
        rendererBindings.entries
            .filter { it.value.first == identity }
            .map { it.key }
            .forEach { detachVideo(it) }
    }
}
