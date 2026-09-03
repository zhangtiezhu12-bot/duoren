package com.yourcompany.videocallsdk

data class ConnectionInfo(
    val roomName: String,
    val identity: String,
    val name: String,
    val serverUrl: String,
    val token: String
)
