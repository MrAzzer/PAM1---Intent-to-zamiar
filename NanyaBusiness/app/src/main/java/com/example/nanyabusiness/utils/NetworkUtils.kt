package com.example.nanyabusiness.utils

import java.net.InetSocketAddress
import java.net.Socket

object NetworkUtils {
    fun isTcpOpen(host: String, port: Int, timeoutMs: Int = 1500): Boolean {
        return try {
            Socket().use { sock ->
                sock.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}
