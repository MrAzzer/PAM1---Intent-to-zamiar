package com.example.nanyabusiness.utils

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class ShellSession(
    val session: Session,
    val channel: ChannelShell,
    private val input: InputStream,
    private val output: OutputStream,
    private val job: Job
) {
    suspend fun send(line: String) {
        withContext(Dispatchers.IO) {
            output.write((line + "\n").toByteArray())
            output.flush()
        }
    }

    fun close() {
        try {
            job.cancel()
        } catch (_: Exception) {}
        try {
            channel.disconnect()
        } catch (_: Exception) {}
        try {
            session.disconnect()
        } catch (_: Exception) {}
    }
}

object RealSSHManager {
    suspend fun executeCommand(
        host: String,
        port: Int = 22,
        username: String,
        password: String? = null,
        privateKey: String? = null,
        passphrase: String? = null,
        command: String
    ): String = withContext(Dispatchers.IO) {
        val jsch = JSch()
        var tempKeyFile: java.io.File? = null
        if (!privateKey.isNullOrBlank()) {
            try {
                tempKeyFile = java.io.File.createTempFile("sshkey", ".pem")
                tempKeyFile.writeText(privateKey)
                if (!passphrase.isNullOrBlank()) {
                    jsch.addIdentity(tempKeyFile.absolutePath, passphrase)
                } else {
                    jsch.addIdentity(tempKeyFile.absolutePath)
                }
            } catch (e: Exception) {
                android.util.Log.w("RealSSHManager", "failed to write temp key", e)
            }
        }

        var session: Session? = null
        try {
            session = jsch.getSession(username, host, port)
            if (!password.isNullOrBlank()) session.setPassword(password)
            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            session.setConfig(config)
            session.connect(10_000)

            val channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            val baos = ByteArrayOutputStream()
            channel.outputStream = baos
            channel.connect(5_000)
            while (!channel.isClosed) {
                Thread.sleep(50)
            }
            val exitStatus = channel.exitStatus
            channel.disconnect()
            return@withContext baos.toString(Charsets.UTF_8.name()) + "\nExit:$exitStatus"
        } catch (e: Exception) {
            android.util.Log.e("RealSSHManager", "executeCommand error", e)
            return@withContext "SSH error: ${e.message}\n${e.stackTraceToString()}"
        } finally {
            session?.disconnect()
            try { tempKeyFile?.delete() } catch (e: Exception) { android.util.Log.w("RealSSHManager", "temp key delete failed", e) }
        }
    }

    // start shell
    suspend fun startShellSession(
        host: String,
        port: Int = 22,
        username: String,
        password: String? = null,
        privateKey: String? = null,
        scope: CoroutineScope,
        onOutput: (String) -> Unit
    ): ShellSession = withContext(Dispatchers.IO) {
        val jsch = JSch()
        var tempKeyFile: java.io.File? = null
        if (!privateKey.isNullOrBlank()) {
            try {
                tempKeyFile = java.io.File.createTempFile("sshkey", ".pem")
                tempKeyFile.writeText(privateKey)
                jsch.addIdentity(tempKeyFile.absolutePath)
            } catch (e: Exception) {
                android.util.Log.w("RealSSHManager", "failed to write temp key for shell", e)
            }
        }

        var session: Session? = null
        try {
            session = jsch.getSession(username, host, port)
            if (!password.isNullOrBlank()) session.setPassword(password)
            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            session.setConfig(config)
            session.connect(10_000)

            val channel = session.openChannel("shell") as ChannelShell
            channel.setPty(true)
            channel.setPtyType("vt100")
            try {
                channel.setPtySize(80, 24, 640, 480)
            } catch (_: Throwable) {}
            val input = channel.inputStream
            val output = channel.outputStream
            channel.connect(5_000)

            val job = scope.launch(Dispatchers.IO) {
                val buf = ByteArray(1024)
                try {
                    while (isActive && !channel.isClosed) {
                        val available = input.available()
                        if (available > 0) {
                            val len = input.read(buf, 0, minOf(buf.size, available))
                            if (len > 0) {
                                val s = String(buf, 0, len)
                                onOutput(s)
                            }
                        } else {
                            kotlinx.coroutines.delay(50)
                        }
                    }
                } catch (_: Exception) {}
            }
            return@withContext ShellSession(session, channel, input, output, job)
        } catch (e: Exception) {
            session?.disconnect()
            try { tempKeyFile?.delete() } catch (ignored: Exception) {}
            throw e
        }
    }
}
