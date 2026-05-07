package com.example.nanyabusiness.utils

import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object TailscaleClient {
    private val client = OkHttpClient()

    // lista urzadzenie-IP
    suspend fun fetchDevices(apiKey: String, tailnet: String): List<Map<String, String>> {
        val url = "https://api.tailscale.com/api/v2/tailnet/$tailnet/devices"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(apiKey, ""))
            .get()
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Tailscale API error: ${resp.code}")
            val body = resp.body?.string() ?: ""
            val json = JSONObject(body)
            val devices = mutableListOf<Map<String, String>>()
            val items = json.optJSONArray("devices") ?: return devices
            for (i in 0 until items.length()) {
                val d = items.getJSONObject(i)
                val name = d.optString("hostname", d.optString("name", "unknown"))
                val addrsArr = d.optJSONArray("addresses")
                var host = ""
                if (addrsArr != null && addrsArr.length() > 0) {
                    host = addrsArr.optString(0)
                }
                devices.add(mapOf("name" to name, "host" to host))
            }
            return devices
        }
    }
}
