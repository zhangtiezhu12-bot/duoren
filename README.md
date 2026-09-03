# VideoCallSDK LiveKit Unified

统一版 Android 原生视频通话 SDK。

## 目标

- 一个 SDK 同时支持 1 对 1 与多人会议。
- 底层统一使用 LiveKit SFU，不再维护 P2P 与多人两套信令。
- Android Native，不使用 WebView/H5/浏览器控件。
- SDK 通过可信后端获取 LiveKit Token；API Secret 永远不进入 APK。

## 当前测试环境

- LiveKit WSS: `wss://223.109.200.173`
- Token Endpoint: `https://223.109.200.173/livekit-token/token`
- LiveKit Android SDK: `2.28.1`
- Demo 默认房间: `test-room-001`

## 模块

- `videocallsdk/`：甲方接入的 SDK 源码模块，构建产物 `videocallsdk-release.aar`
- `app/`：2～4 人测试 Demo，构建产物 `app-debug.apk`

## 核心 API

```kotlin
val sdk = VideoCallSDK(
    context = applicationContext,
    tokenEndpoint = "https://223.109.200.173/livekit-token/token",
    listener = listener
)

sdk.joinRoom("meeting-001", "user-001", "张三")

val view = sdk.createVideoView(context)
sdk.attachVideo("user-001", view)

sdk.setCameraEnabled(true)
sdk.setMicrophoneEnabled(true)
sdk.switchCamera()
sdk.leaveRoom()
```

## GitHub Actions

上传整个工程后，Actions 会同时产出：

- `LiveKit-Unified-Demo-APK`
- `LiveKit-Unified-SDK-AAR`

## 重要说明

当前 AAR 是“包装 SDK AAR”，LiveKit 依赖仍通过 Maven/Gradle 解析。甲方使用本地 AAR 时，需要同时在项目中声明 LiveKit 依赖，详见 `SDK_INTEGRATION.md`。如果后续要求单文件离线 AAR，可再做 fat-AAR 或内部 Maven 仓库发布版。
