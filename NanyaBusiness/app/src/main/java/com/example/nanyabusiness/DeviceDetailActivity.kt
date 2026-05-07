package com.example.nanyabusiness

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.nanyabusiness.ui.theme.NanyaBusinessTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// klasa dla procesow
data class DeviceProcess(val pid: Int, val name: String, val cpu: Int, val ram: Int)

class DeviceDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceId = intent.getIntExtra("deviceId", -1)
        setContent {
            NanyaBusinessTheme(darkTheme = AppState.darkTheme.value) {
                DeviceDetailScreen(deviceId = deviceId)
            }
        }
    }
}

@Composable
fun DeviceDetailScreen(deviceId: Int) {
    val ctx = LocalContext.current
    var credential by remember { mutableStateOf<com.example.nanyabusiness.data.database.Credential?>(null) }
    LaunchedEffect(deviceId) {
        if (deviceId > 0) {
            credential = com.example.nanyabusiness.data.database.AppDatabase.getDatabase(ctx).credentialDao().getCredentialById(deviceId)
        }
    }
    // zmienne procesow
    var processes by remember { mutableStateOf<List<DeviceProcess>>(emptyList()) }
    var processesLoading by remember { mutableStateOf(false) }

    // Parsers dla ps aux
    fun parsePsEOutput(out: String): List<DeviceProcess> {
        return out.lines().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 4) {
                val pid = parts[0].toIntOrNull() ?: return@mapNotNull null
                val name = parts[1]
                val cpu = parts[2].toDoubleOrNull()?.toInt() ?: 0
                val rssKb = parts[3].toIntOrNull() ?: 0
                val ramMb = rssKb / 1024
                DeviceProcess(pid = pid, name = name, cpu = cpu, ram = ramMb)
            } else null
        }
    }

    fun parsePsAuxOutput(out: String): List<DeviceProcess> {
        return out.lines().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            // ps aux: USER PID %CPU %MEM VSZ RSS TTY STAT START TIME COMMAND
            if (parts.size >= 11) {
                val pid = parts[1].toIntOrNull() ?: return@mapNotNull null
                val cpu = parts[2].toDoubleOrNull()?.toInt() ?: 0
                val rssKb = parts[5].toIntOrNull() ?: 0
                val name = parts.subList(10, parts.size).joinToString(" ")
                val ramMb = rssKb / 1024
                DeviceProcess(pid = pid, name = name, cpu = cpu, ram = ramMb)
            } else null
        }
    }

    fun parsePsSimpleOutput(out: String): List<DeviceProcess> {
        return out.lines().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"))
            // try to find PID and last token as command
            if (parts.size >= 2) {
                val maybePid = parts[0].toIntOrNull() ?: return@mapNotNull null
                val name = parts.last()
                DeviceProcess(pid = maybePid, name = name, cpu = 0, ram = 0)
            } else null
        }
    }

    

    suspend fun fetchProcessesForCredential(cred: com.example.nanyabusiness.data.database.Credential) {
        processesLoading = true
        try {
            // flagi dla psa
            val candidates = listOf(
                "ps -eo pid,comm,%cpu,rss --no-headers",
                "ps aux",
                "ps"
            )
            var result: List<DeviceProcess>? = null
            for (cmd in candidates) {
                try {
                    val out = com.example.nanyabusiness.utils.RealSSHManager.executeCommand(
                        host = cred.host,
                        port = cred.port,
                        username = cred.username,
                        password = cred.password.takeIf { it.isNotBlank() },
                        privateKey = cred.privateKey.takeIf { it.isNotBlank() },
                        passphrase = "",
                        command = cmd
                    )
                    val parsed = when (cmd) {
                        candidates[0] -> parsePsEOutput(out)
                        candidates[1] -> parsePsAuxOutput(out)
                        else -> parsePsSimpleOutput(out)
                    }
                    if (parsed.isNotEmpty()) {
                        result = parsed
                        break
                    }
                } catch (_: Exception) {

                }
            }
            processes = result ?: emptyList()
        } catch (e: Exception) {
            processes = emptyList()
        } finally {
            processesLoading = false
        }
    }

    

    // fetch
    LaunchedEffect(credential?.id) {
        credential?.let { cred ->
            try {
                fetchProcessesForCredential(cred)
            } catch (_: Exception) {}
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Szczegóły urządzenia #$deviceId", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            // Launch terminal activity
            val intent = Intent(ctx, TerminalActivity::class.java).apply {
                putExtra("deviceId", deviceId)
            }
            ctx.startActivity(intent)
        }) {
            Text("Sesh (terminal)")
        }
        Spacer(Modifier.height(8.dp))
        var testOutput by remember { mutableStateOf<String?>(null) }
        val uiScope = rememberCoroutineScope()
        Button(onClick = {
            if (credential != null) {
                uiScope.launch {
                    try {
                        val out = withContext(Dispatchers.IO) {
                            com.example.nanyabusiness.utils.RealSSHManager.executeCommand(
                                host = credential!!.host,
                                port = credential!!.port,
                                username = credential!!.username,
                                password = credential!!.password.takeIf { it.isNotBlank() },
                                privateKey = credential!!.privateKey.takeIf { it.isNotBlank() },
                                passphrase = "",
                                command = "echo test && uname -a"
                            )
                        }
                        testOutput = out
                        // Refresh procesow
                        try {
                            fetchProcessesForCredential(credential!!)
                        } catch (_: Exception) {}
                    } catch (e: Exception) {
                        testOutput = "Error: ${e.message}\n${e.stackTraceToString()}"
                    }
                }
            } else {
                testOutput = "No credential set for device"
            }
        }) { Text("Test SSH") }
        testOutput?.let { txt ->
            Spacer(Modifier.height(8.dp))
            Text("SSH test output:")
            Text(txt)
        }
        Spacer(Modifier.height(12.dp))
        // uzytkownicy ssh
        Text("SSH Users", style = MaterialTheme.typography.titleMedium)
        val usersFlow = com.example.nanyabusiness.data.database.AppDatabase.getDatabase(ctx).sshUserDao().getUsersForDevice(deviceId)
        val users by usersFlow.collectAsStateWithLifecycle(initialValue = emptyList())
        users.forEach { u ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(u.username)
                    if (u.lastUsed > 0) Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(u.lastUsed)), style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = {
                    //quick connect
                    uiScope.launch {
                        val updated = withContext(Dispatchers.IO) {
                            val db = com.example.nanyabusiness.data.database.AppDatabase.getDatabase(ctx)
                            val cred = db.credentialDao().getCredentialById(deviceId)
                            if (cred != null) {
                                val up = cred.copy(username = u.username, password = u.password)
                                db.credentialDao().insertCredential(up)
                                up
                            } else null
                        }
                        if (updated != null) {
                            // update state
                            credential = updated
                        }
                    }
                }) { Text("Use") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    uiScope.launch {
                        withContext(Dispatchers.IO) {
                            com.example.nanyabusiness.data.database.AppDatabase.getDatabase(ctx).sshUserDao().deleteUser(u)
                        }
                    }
                }) { Text("Delete") }
            }
        }
        Spacer(Modifier.height(8.dp))
        var newUser by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newUser, onValueChange = { newUser = it }, label = { Text("Username") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(value = newPass, onValueChange = { newPass = it }, label = { Text("Password") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            if (newUser.isNotBlank()) {
                uiScope.launch {
                    withContext(Dispatchers.IO) {
                        val db = com.example.nanyabusiness.data.database.AppDatabase.getDatabase(ctx)
                        db.sshUserDao().insertUser(com.example.nanyabusiness.data.database.SshUser(deviceId = deviceId, username = newUser, password = newPass, lastUsed = 0))
                    }
                    newUser = ""
                    newPass = ""
                }
            }
        }) { Text("Add SSH User") }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Procesy:")
            Spacer(Modifier.weight(1f))
            if (processesLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Button(onClick = {
                uiScope.launch {
                    credential?.let {
                        try {
                            fetchProcessesForCredential(it)
                        } catch (_: Exception) {}
                    }
                }
            }) { Text("Odśwież") }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(processes, key = { it.pid }) { proc ->
                Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(text = "${proc.name} (PID: ${proc.pid})")
                            Text(text = "CPU: ${proc.cpu}% RAM: ${proc.ram}MB")
                        }
                        Row {
                                Button(onClick = {
                                    // restart via SSH
                                    if (credential != null) {
                                        uiScope.launch {
                                            withContext(Dispatchers.IO) {
                                                val cmd = com.example.nanyabusiness.utils.SSHManager.commandForAction(proc.name, proc.pid, "restart")
                                                com.example.nanyabusiness.utils.SSHManager.executeCommandReal(
                                                    host = credential!!.host,
                                                    port = credential!!.port,
                                                    username = credential!!.username,
                                                    password = credential!!.password.takeIf { it.isNotBlank() },
                                                    privateKey = credential!!.privateKey.takeIf { it.isNotBlank() },
                                                    command = cmd
                                                )
                                            }
                                        }
                                    }
                                }) { Text("Restart") }
                                Spacer(Modifier.width(4.dp))
                                Button(onClick = {
                                    if (credential != null) {
                                        uiScope.launch {
                                            withContext(Dispatchers.IO) {
                                                val cmd = com.example.nanyabusiness.utils.SSHManager.commandForAction(proc.name, proc.pid, "pause")
                                                com.example.nanyabusiness.utils.SSHManager.executeCommandReal(
                                                    host = credential!!.host,
                                                    port = credential!!.port,
                                                    username = credential!!.username,
                                                    password = credential!!.password.takeIf { it.isNotBlank() },
                                                    privateKey = credential!!.privateKey.takeIf { it.isNotBlank() },
                                                    command = cmd
                                                )
                                            }
                                        }
                                    }
                                }) { Text("Pause") }
                                Spacer(Modifier.width(4.dp))
                                Button(onClick = {
                                    if (credential != null) {
                                        uiScope.launch {
                                            withContext(Dispatchers.IO) {
                                                val cmd = com.example.nanyabusiness.utils.SSHManager.commandForAction(proc.name, proc.pid, "kill")
                                                com.example.nanyabusiness.utils.SSHManager.executeCommandReal(
                                                    host = credential!!.host,
                                                    port = credential!!.port,
                                                    username = credential!!.username,
                                                    password = credential!!.password.takeIf { it.isNotBlank() },
                                                    privateKey = credential!!.privateKey.takeIf { it.isNotBlank() },
                                                    command = cmd
                                                )
                                            }
                                        }
                                    }
                                }) { Text("Kill") }
                        }
                    }
                }
            }
        }
    }
}

}

