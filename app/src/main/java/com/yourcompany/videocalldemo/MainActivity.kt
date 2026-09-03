package com.yourcompany.videocalldemo

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.yourcompany.videocallsdk.ParticipantInfo
import com.yourcompany.videocallsdk.VideoCallListener
import com.yourcompany.videocallsdk.VideoCallSDK

class MainActivity : AppCompatActivity(), VideoCallListener {

    companion object {
        private const val REQUEST_MEDIA_PERMISSIONS = 1001
        private const val TOKEN_ENDPOINT = "https://223.109.200.173/livekit-token/token"
    }

    private lateinit var sdk: VideoCallSDK
    private lateinit var roomInput: EditText
    private lateinit var nameInput: EditText
    private lateinit var grid: GridLayout
    private lateinit var statusView: TextView
    private lateinit var joinButton: Button
    private lateinit var cameraButton: Button
    private lateinit var micButton: Button

    private val tiles = linkedMapOf<String, ParticipantTile>()
    private var cameraEnabled = true
    private var micEnabled = true
    private var pendingJoin = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sdk = VideoCallSDK(
            context = applicationContext,
            tokenEndpoint = TOKEN_ENDPOINT,
            listener = this
        )
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(15, 23, 42))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }

        root.addView(TextView(this).apply {
            text = "LiveKit 原生多人视频 Demo"
            setTextColor(Color.WHITE)
            textSize = 22f
        })

        root.addView(TextView(this).apply {
            text = "同一个 Room 可用于 1 对 1 或多人会议；当前测试 2～4 人。"
            setTextColor(Color.LTGRAY)
            textSize = 13f
        })

        val formRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(6))
        }

        roomInput = EditText(this).apply {
            setText("test-room-001")
            hint = "房间号"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                marginEnd = dp(6)
            }
        }
        nameInput = EditText(this).apply {
            setText("Android-${(100..999).random()}")
            hint = "姓名"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
        }
        formRow.addView(roomInput)
        formRow.addView(nameInput)
        root.addView(formRow)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        joinButton = Button(this).apply {
            text = "加入会议"
            setOnClickListener { ensurePermissionsAndJoin() }
        }
        cameraButton = Button(this).apply {
            text = "关闭摄像头"
            setOnClickListener {
                cameraEnabled = !cameraEnabled
                sdk.setCameraEnabled(cameraEnabled)
                text = if (cameraEnabled) "关闭摄像头" else "打开摄像头"
            }
        }
        micButton = Button(this).apply {
            text = "静音"
            setOnClickListener {
                micEnabled = !micEnabled
                sdk.setMicrophoneEnabled(micEnabled)
                text = if (micEnabled) "静音" else "取消静音"
            }
        }
        val switchButton = Button(this).apply {
            text = "切换摄像头"
            setOnClickListener { sdk.switchCamera() }
        }
        val leaveButton = Button(this).apply {
            text = "退出"
            setOnClickListener {
                sdk.leaveRoom()
                clearTiles()
            }
        }

        listOf(joinButton, cameraButton, micButton, switchButton, leaveButton).forEach { button ->
            buttonRow.addView(button, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        root.addView(buttonRow)

        statusView = TextView(this).apply {
            text = "未连接"
            setTextColor(Color.rgb(147, 197, 253))
            textSize = 13f
            setPadding(0, dp(6), 0, dp(6))
        }
        root.addView(statusView)

        val scroll = ScrollView(this)
        grid = GridLayout(this).apply {
            columnCount = 2
            rowCount = GridLayout.UNDEFINED
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        scroll.addView(grid)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        return root
    }

    private fun ensurePermissionsAndJoin() {
        val missing = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }

        if (missing.isEmpty()) {
            join()
        } else {
            pendingJoin = true
            requestPermissions(missing.toTypedArray(), REQUEST_MEDIA_PERMISSIONS)
        }
    }

    private fun join() {
        val room = roomInput.text.toString().trim()
        val name = nameInput.text.toString().trim()
        if (room.isBlank() || name.isBlank()) {
            toast("请填写房间号和姓名")
            return
        }

        val identity = name.replace(" ", "_") + "-" + System.currentTimeMillis().toString().takeLast(6)
        status("正在加入 $room ...")
        joinButton.isEnabled = false
        sdk.joinRoom(room, identity, name)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MEDIA_PERMISSIONS) {
            val ok = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (ok && pendingJoin) join() else toast("摄像头和麦克风权限是视频会议必需权限")
            pendingJoin = false
        }
    }

    override fun onConnecting(roomName: String) = status("正在连接 LiveKit：$roomName")

    override fun onConnected(roomName: String, localParticipant: ParticipantInfo) {
        runOnUiThread {
            status("✅ 已进入 $roomName，等待其他参与者...")
            joinButton.isEnabled = false
        }
    }

    override fun onParticipantJoined(participant: ParticipantInfo) {
        runOnUiThread {
            ensureTile(participant)
            status("当前 ${tiles.size} 人在线；${participant.name} 已加入")
        }
    }

    override fun onParticipantLeft(participant: ParticipantInfo) {
        runOnUiThread {
            removeTile(participant.identity)
            status("当前 ${tiles.size} 人在线；${participant.name} 已离开")
        }
    }

    override fun onVideoAvailable(participant: ParticipantInfo) {
        runOnUiThread {
            val tile = ensureTile(participant)
            sdk.attachVideo(participant.identity, tile.videoView)
            tile.placeholder.visibility = View.GONE
        }
    }

    override fun onVideoUnavailable(participantIdentity: String) {
        runOnUiThread {
            tiles[participantIdentity]?.placeholder?.visibility = View.VISIBLE
        }
    }

    override fun onReconnecting() = status("网络波动，正在重连...")
    override fun onReconnected() = status("✅ 已重新连接")

    override fun onDisconnected(reason: String) {
        runOnUiThread {
            status("已退出：$reason")
            joinButton.isEnabled = true
        }
    }

    override fun onError(message: String, throwable: Throwable?) {
        runOnUiThread {
            status("❌ $message")
            joinButton.isEnabled = true
        }
    }

    private fun ensureTile(participant: ParticipantInfo): ParticipantTile {
        tiles[participant.identity]?.let { return it }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(30, 41, 59))
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        val videoHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
        }

        val videoView = sdk.createVideoView(this)
        videoHolder.addView(videoView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(190)
        ))

        val placeholder = TextView(this).apply {
            text = "摄像头未开启"
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
            setBackgroundColor(Color.argb(170, 0, 0, 0))
        }
        videoHolder.addView(placeholder, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(32)
        ))

        val label = TextView(this).apply {
            text = participant.name + if (participant.isLocal) "（我）" else ""
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }

        container.addView(videoHolder)
        container.addView(label)

        val spec = GridLayout.LayoutParams().apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }
        grid.addView(container, spec)

        return ParticipantTile(container, videoView, placeholder).also {
            tiles[participant.identity] = it
        }
    }

    private fun removeTile(identity: String) {
        val tile = tiles.remove(identity) ?: return
        sdk.releaseVideoView(tile.videoView)
        grid.removeView(tile.container)
    }

    private fun clearTiles() {
        tiles.keys.toList().forEach { removeTile(it) }
    }

    private fun status(message: String) {
        runOnUiThread { statusView.text = message }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        clearTiles()
        sdk.release()
        super.onDestroy()
    }

    private data class ParticipantTile(
        val container: View,
        val videoView: View,
        val placeholder: TextView
    )
}
