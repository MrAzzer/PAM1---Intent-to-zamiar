package com.example.nanyabusiness.utils

import kotlinx.coroutines.delay

object SSHManager {
    // execute
    suspend fun executeCommandMock(deviceId: Int, command: String): String {
        delay(300)
        return "Output (device#$deviceId): simulated result for '$command'"
    }

    suspend fun executeCommandReal(host: String, port: Int, username: String, password: String?, privateKey: String?, command: String): String {
        return RealSSHManager.executeCommand(host = host, port = port, username = username, password = password, privateKey = privateKey, command = command)
    }

    // linux
    fun commandForAction(processName: String, pid: Int, action: String, extra: Map<String, String> = emptyMap()): String {
        return when (action.lowercase()) {
            "restart" -> "sudo systemctl restart $processName || kill -HUP $pid"
            "pause" -> "sudo kill -STOP $pid"
            "kill" -> "sudo kill -9 $pid"
            "start" -> "sudo systemctl start $processName"
            else -> commandForAction(processName, pid, "restart")
        }
    }
}
