# Android 原生视频通话 SDK 接入说明（LiveKit 统一版）

## 1. 能力

统一 SDK 同时支持：

- 1 对 1 视频通话
- 3～4 人视频会议
- 后续可扩展到 10+ 人 SFU 会议
- 摄像头开启/关闭
- 麦克风开启/静音
- 前后摄像头切换
- 参与者加入/离开回调
- 远端视频 Track 自动订阅
- 网络重连事件

底层全部为 Android Native + LiveKit，不使用 WebView/H5。

## 2. AAR 接入

将：

`videocallsdk-release.aar`

放到甲方：

`app/libs/`

并在甲方 `build.gradle` 中配置：

```gradle
repositories {
    google()
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation files('libs/videocallsdk-release.aar')
    implementation 'io.livekit:livekit-android:2.28.1'
    implementation 'io.livekit:livekit-android-camerax:2.28.1'
}
```

> 当前 AAR 不把 LiveKit 的所有传递依赖物理打进一个文件；正式交付如果要求完全离线的单 AAR，可另做 fat-AAR 或私有 Maven 发布。

## 3. 权限

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

摄像头与麦克风仍需按 Android 系统要求申请运行时权限。

## 4. 初始化

```kotlin
val sdk = VideoCallSDK(
    context = applicationContext,
    tokenEndpoint = "https://your-server/livekit-token/token",
    listener = object : VideoCallListener {
        override fun onParticipantJoined(participant: ParticipantInfo) {}
        override fun onParticipantLeft(participant: ParticipantInfo) {}
        override fun onVideoAvailable(participant: ParticipantInfo) {}
        override fun onError(message: String, throwable: Throwable?) {}
    }
)
```

## 5. 加入房间

```kotlin
sdk.joinRoom(
    roomName = "meeting-001",
    identity = "user-001",
    name = "张三"
)
```

一个房间里 2 人即为 1 对 1；3 人及以上就是多人会议，不需要切换 SDK 模式。

## 6. 视频显示

SDK 不强制甲方页面布局。甲方自行创建容器，然后：

```kotlin
val videoView = sdk.createVideoView(context)
container.addView(videoView)

sdk.attachVideo(
    participantIdentity = participant.identity,
    view = videoView
)
```

页面销毁时：

```kotlin
sdk.releaseVideoView(videoView)
```

## 7. 媒体控制

```kotlin
sdk.setCameraEnabled(false)
sdk.setCameraEnabled(true)

sdk.setMicrophoneEnabled(false)
sdk.setMicrophoneEnabled(true)

sdk.switchCamera()
```

## 8. 退出与释放

```kotlin
sdk.leaveRoom()
```

Activity/Application 最终销毁 SDK 时：

```kotlin
sdk.release()
```

## 9. Token 服务

SDK 不保存 LiveKit API Secret。

客户端只请求后端：

`GET /livekit-token/token?room=...&identity=...&name=...`

后端返回：

```json
{
  "room": "meeting-001",
  "identity": "user-001",
  "name": "张三",
  "wsUrl": "wss://223.109.200.173",
  "token": "eyJ..."
}
```

## 10. 当前阶段

服务器端 3 人浏览器 LiveKit SFU 已完成实际验证。此 Android 工程为统一 Native SDK 第一版，需要通过 GitHub Actions 编译并进行 Android + PC、Android + Android 多人真机验证后，再作为正式交付基线。
