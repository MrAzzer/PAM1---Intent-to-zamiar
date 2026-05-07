package com.example.nanyabusiness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.nanyabusiness.ui.theme.NanyaBusinessTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

class TerminalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deviceId = intent.getIntExtra("deviceId", -1)
        setContent {
            NanyaBusinessTheme(darkTheme = AppState.darkTheme.value) {
                TerminalScreen(deviceId = deviceId)
            }
        }
    }
}

@Composable
fun TerminalScreen(deviceId: Int) {
    val lines = remember { mutableStateListOf<String>() }
    var input by remember { mutableStateOf("") }
    val mainScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val ctx = LocalContext.current
    var credential by remember { mutableStateOf<com.example.nanyabusiness.data.database.Credential?>(null) }
    LaunchedEffect(deviceId) {
        if (deviceId > 0) {
            credential = com.example.nanyabusiness.data.database.AppDatabase.getDatabase(ctx).credentialDao().getCredentialById(deviceId)
        }
    }

    // Shell session state
    var shellSession by remember { mutableStateOf<com.example.nanyabusiness.utils.ShellSession?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Terminal — urządzenie #$deviceId", style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onBackground))
        }
        Spacer(Modifier.height(8.dp))
        Box(modifier = Modifier
            .weight(1f)
            .padding(8.dp)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp))
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(8.dp)) {
                items(lines) { line ->
                    val stripped = stripAnsi(line)
                    Text(
                        text = stripped,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Auto-scroll to bottom when new lines arrive
        LaunchedEffect(lines.size) {
            if (lines.isNotEmpty()) {
                listState.animateScrollToItem(lines.size - 1)
            }
        }

        Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                textStyle = ComposeTextStyle(color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                modifier = Modifier.weight(1f).padding(4.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                val cmd = input.trim()
                if (cmd.isNotEmpty()) {
                    lines.add("> $cmd")
                    input = ""
                    mainScope.launch {
                        if (credential != null) {
                            // If no shell session yet, start one
                            if (shellSession == null) {
                                try {
                                    val s = com.example.nanyabusiness.utils.RealSSHManager.startShellSession(
                                        host = credential!!.host,
                                        port = credential!!.port,
                                        username = credential!!.username,
                                        password = credential!!.password.takeIf { it.isNotBlank() },
                                        privateKey = credential!!.privateKey.takeIf { it.isNotBlank() },
                                        scope = mainScope,
                                        onOutput = { outChunk ->
                                            mainScope.launch {
                                                val parts = outChunk.split('\n').map { it.trimEnd() }.filter { it.isNotEmpty() }
                                                if (parts.isNotEmpty()) lines.addAll(parts)
                                            }
                                        }
                                    )
                                    shellSession = s
                                    // update lastUsed
                                    val db = com.example.nanyabusiness.data.database.AppDatabase.getDatabase(ctx)
                                    val updated = credential!!.copy(lastUsed = System.currentTimeMillis())
                                    db.credentialDao().updateCredential(updated)
                                    credential = updated
                                } catch (e: Exception) {
                                    lines.add("SSH shell error: ${e.message}")
                                }
                            }
                            // send command to shell (ensure newline)
                            shellSession?.send(cmd + "\n")
                        } else {
                            val out = com.example.nanyabusiness.utils.SSHManager.executeCommandMock(deviceId, cmd)
                            lines.addAll(out.split('\n').filter { it.isNotEmpty() })
                        }
                    }
                }
            }) {
                Text("Wyślij")
            }
        }
    }
}

// Simple ANSI escape stripper for now (can be extended to map colors)
fun stripAnsi(s: String): String {
    return s.replace(Regex("\\u001B\\[[;\\d]*m"), "")
}
