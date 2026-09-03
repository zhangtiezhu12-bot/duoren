package com.yourcompany.videocallsdk

interface VideoCallListener {
    fun onConnecting(roomName: String) {}
    fun onConnected(roomName: String, localParticipant: ParticipantInfo) {}
    fun onParticipantJoined(participant: ParticipantInfo) {}
    fun onParticipantLeft(participant: ParticipantInfo) {}
    fun onVideoAvailable(participant: ParticipantInfo) {}
    fun onVideoUnavailable(participantIdentity: String) {}
    fun onReconnecting() {}
    fun onReconnected() {}
    fun onDisconnected(reason: String) {}
    fun onError(message: String, throwable: Throwable? = null) {}
}
