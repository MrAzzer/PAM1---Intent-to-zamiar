package com.example.nanyabusiness

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.example.nanyabusiness.ui.theme.NanyaBusinessTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // ciemny motyw
        val prefs = getSharedPreferences("nanyabusiness_prefs", MODE_PRIVATE)
        AppState.darkTheme.value = prefs.getBoolean("dark_theme", false)

        setContent {
            NanyaBusinessTheme(darkTheme = AppState.darkTheme.value) {
                MainScreen()
            }
        }
    }
}

// =================================================================================
// --- Warstwa Danych: Definicje Room ---
// =================================================================================

// Data layer moved to data/database package (Credential entity, DAO and AppDatabase)

// Repozytorium, ViewModel i Fabryka dla `Credential` (zostały przeniesione z poprzedniej implementacji Task)
class CredentialRepository(private val credentialDao: com.example.nanyabusiness.data.database.CredentialDao) {
    val allCredentials: kotlinx.coroutines.flow.Flow<List<com.example.nanyabusiness.data.database.Credential>> =
        credentialDao.getAllCredentialsStream()

    suspend fun insert(credential: com.example.nanyabusiness.data.database.Credential) {
        credentialDao.insertCredential(credential)
    }

    suspend fun update(credential: com.example.nanyabusiness.data.database.Credential) {
        credentialDao.updateCredential(credential)
    }
}
//CRud
class SshUserRepository(private val userDao: com.example.nanyabusiness.data.database.SshUserDao) {
    fun usersForDevice(deviceId: Int) = userDao.getUsersForDevice(deviceId)
    suspend fun insert(user: com.example.nanyabusiness.data.database.SshUser) = userDao.insertUser(user)
    suspend fun update(user: com.example.nanyabusiness.data.database.SshUser) = userDao.updateUser(user)
    suspend fun delete(user: com.example.nanyabusiness.data.database.SshUser) = userDao.deleteUser(user)
}


// =================================================================================
// --- Architektura: ViewModel i Fabryka dla Credential ---
// =================================================================================

class CredentialViewModel(private val repository: CredentialRepository) : androidx.lifecycle.ViewModel() {
    val credentials: kotlinx.coroutines.flow.StateFlow<List<com.example.nanyabusiness.data.database.Credential>> =
        repository.allCredentials
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun addCredential(credential: com.example.nanyabusiness.data.database.Credential) {
        if (credential.name.isNotBlank()) {
            viewModelScope.launch {
                repository.insert(credential)
            }
        }
    }

    fun toggleActive(cred: com.example.nanyabusiness.data.database.Credential) {
        viewModelScope.launch {
            repository.update(cred.copy(isActive = !cred.isActive))
        }
    }
}

// Fabryka do tworzenia ViewModelu z zależnością
class CredentialViewModelFactory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CredentialViewModel::class.java)) {
            val dao = com.example.nanyabusiness.data.database.AppDatabase.getDatabase(application).credentialDao()
            val repository = CredentialRepository(dao)
            @Suppress("UNCHECKED_CAST")
            return CredentialViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// =================================================================================
// --- UI ---
// =================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesListScreen(modifier: Modifier = Modifier) {
    // Pobranie Application context do fabryki
    val application = LocalContext.current.applicationContext as Application
    val viewModel: CredentialViewModel = viewModel(factory = CredentialViewModelFactory(application))
    val tasks by viewModel.credentials.collectAsStateWithLifecycle()
    var newTaskTitle by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize()) {
        // Dashboard for first device (or placeholder)
        val firstDevice = tasks.firstOrNull()
        if (firstDevice != null) {
            DeviceDashboard(credential = firstDevice)
            Spacer(Modifier.height(12.dp))
        }
        TopAppBar(title = { Text("Urządzenia") })
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            // Sekcja dodawania nowego zadania (placeholder dla dodawania urządzenia)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    label = { Text("Nowe urządzenie") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    if (newTaskTitle.isNotBlank()) {
                        val cred = com.example.nanyabusiness.data.database.Credential(
                            name = newTaskTitle,
                            lastUsed = System.currentTimeMillis()
                        )
                        viewModel.addCredential(cred)
                        newTaskTitle = ""
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Dodaj urządzenie")
                }
            }
            Spacer(Modifier.height(12.dp))
            // Lista urządzeń
            val ctx = LocalContext.current
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(tasks) { task ->
                    DeviceItem(
                        task,
                        onToggle = { viewModel.toggleActive(task) },
                        onClick = {
                            val intent = android.content.Intent(ctx, DeviceDetailActivity::class.java).apply {
                                putExtra("deviceId", task.id)
                            }
                            ctx.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceDashboard(credential: com.example.nanyabusiness.data.database.Credential) {
    val ctx = LocalContext.current
    var metrics by remember { mutableStateOf<DeviceMetrics?>(null) }
    val scope = rememberCoroutineScope()
    val statusColor = when {
        credential.isActive -> Color.Green
        credential.lastUsed > 0 && (System.currentTimeMillis() - credential.lastUsed) < 24*60*60*1000 -> Color(0xFFFFC107) // gold
        else -> Color.Gray
    }

    LaunchedEffect(credential.id) {
        scope.launch {
            metrics = fetchDeviceMetrics(credential)
        }
    }

    Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(statusColor))
                Spacer(Modifier.width(8.dp))
                Text(credential.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(onClick = { scope.launch { metrics = fetchDeviceMetrics(credential) } }) { Text("Odśwież") }
            }
            Spacer(Modifier.height(8.dp))
            // Disk usage
            val diskPct = metrics?.diskUsagePercent
            Text("Dysk: ${diskPct?.let { "$it%" } ?: "-"}")
            LinearProgressIndicator(progress = (diskPct ?: 0) / 100f, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            // CPU load
            Text("CPU load: ${metrics?.cpuLoad?.let { String.format("%.2f", it) } ?: "-"}")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val coreUsages = metrics?.cpuCoresUsage ?: emptyList()
                if (coreUsages.isEmpty()) {
                    Text("Brak danych o rdzeniach", fontSize = 12.sp)
                } else {
                    coreUsages.forEach { u ->
                        LinearProgressIndicator(progress = u / 100f, modifier = Modifier.width(24.dp).height(6.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Network
            Text("Net: RX ${metrics?.netRxKbps ?: "-"} kB/s • TX ${metrics?.netTxKbps ?: "-"} kB/s")
            Spacer(Modifier.height(8.dp))
            // Temperature
            Text("Temp: ${metrics?.temperatureC?.let { String.format("%.1f°C", it) } ?: "-"}")
        }
    }
}

data class DeviceMetrics(
    val diskUsagePercent: Int? = null,
    val cpuLoad: Double? = null,
    val cpuCoresUsage: List<Int>? = null,
    val netRxKbps: Int? = null,
    val netTxKbps: Int? = null,
    val temperatureC: Double? = null
)

suspend fun fetchDeviceMetrics(cred: com.example.nanyabusiness.data.database.Credential): DeviceMetrics {
    return withContext(Dispatchers.IO) {
        try {
            // Disk percent (root)
            val diskOut = com.example.nanyabusiness.utils.RealSSHManager.executeCommand(
                host = cred.host, port = cred.port, username = cred.username,
                password = cred.password.takeIf { it.isNotBlank() }, privateKey = cred.privateKey.takeIf { it.isNotBlank() },
                passphrase = "", command = "df -P / | awk 'NR==2{print $5}'"
            ).trim()
            val diskPct = diskOut.replace("%", "").toIntOrNull()

            // CPU load average
            val loadOut = com.example.nanyabusiness.utils.RealSSHManager.executeCommand(
                host = cred.host, port = cred.port, username = cred.username,
                password = cred.password.takeIf { it.isNotBlank() }, privateKey = cred.privateKey.takeIf { it.isNotBlank() },
                passphrase = "", command = "cat /proc/loadavg | awk '{print $1}'"
            ).trim()
            val cpuLoad = loadOut.toDoubleOrNull()

            // Try to get per-core by reading /proc/stat twice
            val stat1 = com.example.nanyabusiness.utils.RealSSHManager.executeCommand(
                host = cred.host, port = cred.port, username = cred.username,
                password = cred.password.takeIf { it.isNotBlank() }, privateKey = cred.privateKey.takeIf { it.isNotBlank() },
                passphrase = "", command = "grep '^cpu[0-9]' /proc/stat || true"
            )
            kotlinx.coroutines.delay(800)
            val stat2 = com.example.nanyabusiness.utils.RealSSHManager.executeCommand(
                host = cred.host, port = cred.port, username = cred.username,
                password = cred.password.takeIf { it.isNotBlank() }, privateKey = cred.privateKey.takeIf { it.isNotBlank() },
                passphrase = "", command = "grep '^cpu[0-9]' /proc/stat || true"
            )
            val coreUsages = if (stat1.isNotBlank() && stat2.isNotBlank()) {
                val map1 = parseProcStatLines(stat1)
                val map2 = parseProcStatLines(stat2)
                map1.keys.mapNotNull { k ->
                    val a = map1[k]
                    val b = map2[k]
                    if (a != null && b != null) {
                        val idle = (b.idle - a.idle).toDouble()
                        val total = (b.total - a.total).toDouble()
                        if (total > 0) ((1.0 - idle / total) * 100.0).toInt() else null
                    } else null
                }
            } else null

            // Network: sample /proc/net/dev
            val net1 = com.example.nanyabusiness.utils.RealSSHManager.executeCommand(
                host = cred.host, port = cred.port, username = cred.username,
                password = cred.password.takeIf { it.isNotBlank() }, privateKey = cred.privateKey.takeIf { it.isNotBlank() },
                passphrase = "", command = "cat /proc/net/dev"
            )
            kotlinx.coroutines.delay(1000)
            val net2 = com.example.nanyabusiness.utils.RealSSHManager.executeCommand(
                host = cred.host, port = cred.port, username = cred.username,
                password = cred.password.takeIf { it.isNotBlank() }, privateKey = cred.privateKey.takeIf { it.isNotBlank() },
                passphrase = "", command = "cat /proc/net/dev"
            )
            val (rxKbps, txKbps) = parseNetDevSpeed(net1, net2)

            // Temperature: try /sys/class/thermal
            val tempOut = com.example.nanyabusiness.utils.RealSSHManager.executeCommand(
                host = cred.host, port = cred.port, username = cred.username,
                password = cred.password.takeIf { it.isNotBlank() }, privateKey = cred.privateKey.takeIf { it.isNotBlank() },
                passphrase = "", command = "cat /sys/class/thermal/thermal_zone0/temp || true"
            ).trim()
            val tempC = tempOut.toDoubleOrNull()?.let { if (it > 1000) it / 1000.0 else it }

            DeviceMetrics(
                diskUsagePercent = diskPct,
                cpuLoad = cpuLoad,
                cpuCoresUsage = coreUsages,
                netRxKbps = rxKbps,
                netTxKbps = txKbps,
                temperatureC = tempC
            )
        } catch (e: Exception) {
            DeviceMetrics()
        }
    }
}

fun parseProcStatLines(out: String): Map<String, ProcCpu> {
    val map = mutableMapOf<String, ProcCpu>()
    out.lines().forEach { line ->
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.isNotEmpty() && parts[0].startsWith("cpu")) {
            val name = parts[0]
            val nums = parts.subList(1, parts.size).mapNotNull { it.toLongOrNull() }
            if (nums.size >= 4) {
                val idle = nums[3]
                val total = nums.sum()
                map[name] = ProcCpu(idle = idle, total = total)
            }
        }
    }
    return map
}

data class ProcCpu(val idle: Long, val total: Long)

fun parseNetDevSpeed(a: String, b: String): Pair<Int?, Int?> {
    try {
        fun parseOne(s: String): Map<String, Pair<Long, Long>> {
            val m = mutableMapOf<String, Pair<Long, Long>>()
            s.lines().drop(2).forEach { l ->
                val parts = l.trim().split(Regex("[:\\s]+"))
                if (parts.size >= 10) {
                    val iface = parts[0]
                    val rx = parts[1].toLongOrNull() ?: 0L
                    val tx = parts[9].toLongOrNull() ?: 0L
                    m[iface] = Pair(rx, tx)
                }
            }
            return m
        }
        val ma = parseOne(a)
        val mb = parseOne(b)
        // sum all interfaces except lo
        var rxDiff = 0L
        var txDiff = 0L
        for ((k, v) in ma) {
            if (k == "lo") continue
            val v2 = mb[k] ?: continue
            rxDiff += (v2.first - v.first)
            txDiff += (v2.second - v.second)
        }
        // bytes per second -> kB/s
        val rxkb = (rxDiff / 1024).toInt()
        val txkb = (txDiff / 1024).toInt()
        return Pair(rxkb, txkb)
    } catch (_: Exception) {
        return Pair(null, null)
    }
}
@Composable
fun DeviceItem(task: com.example.nanyabusiness.data.database.Credential, onToggle: () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // device type icon
        val deviceIcon = when {
            task.type.contains("phone", ignoreCase = true) -> Icons.Default.PhoneAndroid
            task.name.contains("phone", ignoreCase = true) -> Icons.Default.PhoneAndroid
            task.name.contains("android", ignoreCase = true) -> Icons.Default.PhoneAndroid
            task.name.contains("iphone", ignoreCase = true) -> Icons.Default.PhoneAndroid
            else -> Icons.Default.Computer
        }
        Icon(imageVector = deviceIcon, contentDescription = "Device type", tint = if (task.isActive) Color.Green else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(12.dp))
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Status",
            tint = if (task.isActive) Color.Green else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = task.name,
            style = if (task.isActive) TextStyle(textDecoration = TextDecoration.LineThrough) else TextStyle.Default
        )
    }
}

// -----------------------
// Main bottom navigation
// -----------------------

@Composable
fun MainScreen() {
    var selectedIndex by remember { mutableStateOf(0) }
    val items = listOf("Urządzenia", "Dodaj", "Ustawienia")

    // Drawer dla urzadzen
    ModalNavigationDrawer(drawerContent = {
        val application = LocalContext.current.applicationContext as Application
        val dao = com.example.nanyabusiness.data.database.AppDatabase.getDatabase(application).credentialDao()
        val devicesFlow = dao.getAllCredentialsStream()
        val devices by devicesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

        Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
            Text("Urządzenia w sieci", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            devices.forEach { d ->
                val deviceIcon = when {
                    d.type.contains("phone", ignoreCase = true) -> Icons.Default.PhoneAndroid
                    d.name.contains("phone", ignoreCase = true) -> Icons.Default.PhoneAndroid
                    d.name.contains("android", ignoreCase = true) -> Icons.Default.PhoneAndroid
                    d.name.contains("iphone", ignoreCase = true) -> Icons.Default.PhoneAndroid
                    else -> Icons.Default.Computer
                }
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = deviceIcon, contentDescription = "Device", tint = if (d.isActive) Color.Green else Color.Gray)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(d.name, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text(if (d.lastUsed > 0) java.text.DateFormat.getDateTimeInstance().format(java.util.Date(d.lastUsed)) else "brak logowania", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }) {
        Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        icon = { Icon(if (index == 0) Icons.Default.CheckCircle else Icons.Default.Add, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedIndex) {
                0 -> DevicesListScreen(modifier = Modifier.fillMaxSize())
                1 -> AddDeviceScreen()
                2 -> SettingsScreen()
            }
        }
    }
    // Periodic auto-refresh from Tailscale if configured
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences("nanyabusiness_prefs", Context.MODE_PRIVATE)
        while (true) {
            val key = prefs.getString("tailscale_key", "") ?: ""
            val tailnet = prefs.getString("tailscale_tailnet", "") ?: ""
            if (key.isNotBlank() && tailnet.isNotBlank()) {
                try {
                    val devices = com.example.nanyabusiness.utils.TailscaleClient.fetchDevices(key, tailnet)
                    val db = com.example.nanyabusiness.data.database.AppDatabase.getDatabase(ctx)
                    devices.forEach { d ->
                        val name = d["name"] ?: "device"
                        val host = d["host"] ?: ""
                        val cred = com.example.nanyabusiness.data.database.Credential(name = name, host = host, lastUsed = System.currentTimeMillis())
                        db.credentialDao().insertCredential(cred)
                    }
                    // heartbeat: check SSH port and update isActive
                    val list = db.credentialDao().getAllCredentialsStream().first()
                    list.forEach { c ->
                        val hostAddr = c.host
                        if (hostAddr.isNotBlank()) {
                            val open = com.example.nanyabusiness.utils.NetworkUtils.isTcpOpen(hostAddr, c.port, 1000)
                            db.credentialDao().updateCredential(c.copy(isActive = open))
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MainScreen", "Tailscale refresh error", e)
                }
            }
            delay(30.seconds)
        }
    }
}
}

@Composable
fun AddDeviceScreen() {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: CredentialViewModel = viewModel(factory = CredentialViewModelFactory(application))

    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("22") }
    var type by remember { mutableStateOf("SSH") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Dodaj urządzenie", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nazwa") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Host / IP") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = portText, onValueChange = { portText = it }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Typ (SSH/VPN/API)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = privateKey, onValueChange = { privateKey = it }, label = { Text("Private Key (optional)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            val port = portText.toIntOrNull() ?: 22
            val cred = com.example.nanyabusiness.data.database.Credential(
                name = name,
                host = host,
                port = port,
                type = type,
                username = username,
                password = password,
                privateKey = privateKey,
                lastUsed = System.currentTimeMillis()
            )
            viewModel.addCredential(cred)
            name = ""
            host = ""
            portText = "22"
            type = "SSH"
            username = ""
            password = ""
            privateKey = ""
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Zapisz urządzenie")
        }
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("nanyabusiness_prefs", Context.MODE_PRIVATE)
    var tailscaleKey by remember { mutableStateOf(prefs.getString("tailscale_key", "") ?: "") }
    var tailnet by remember { mutableStateOf(prefs.getString("tailscale_tailnet", "") ?: "") }
    var darkMode by remember { mutableStateOf(prefs.getBoolean("dark_theme", false)) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Top) {
        Text("Ustawienia", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = tailscaleKey, onValueChange = { tailscaleKey = it }, label = { Text("Tailscale API Key") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = tailnet, onValueChange = { tailnet = it }, label = { Text("Tailnet (np. example.com)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Switch(checked = darkMode, onCheckedChange = {
                darkMode = it
                prefs.edit().putBoolean("dark_theme", darkMode).apply()
                AppState.darkTheme.value = darkMode
            })
            Spacer(Modifier.width(8.dp))
            Text("Ciemny motyw")
        }
        Spacer(Modifier.height(12.dp))
        val uiScope = rememberCoroutineScope()
        Row {
            Button(onClick = {
                prefs.edit().putString("tailscale_key", tailscaleKey).putString("tailscale_tailnet", tailnet).apply()
            }) {
                Text("Zapisz konfigurację Tailscale")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                // fetch devices and insert to DB
                val appCtx = ctx.applicationContext
                uiScope.launch {
                    withContext(Dispatchers.IO) {
                        try {
                            val devices = com.example.nanyabusiness.utils.TailscaleClient.fetchDevices(tailscaleKey, tailnet)
                            val db = com.example.nanyabusiness.data.database.AppDatabase.getDatabase(appCtx)
                            devices.forEach { d ->
                                val name = d["name"] ?: "device"
                                val host = d["host"] ?: ""
                                val cred = com.example.nanyabusiness.data.database.Credential(name = name, host = host, lastUsed = System.currentTimeMillis())
                                db.credentialDao().insertCredential(cred)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }) {
                Text("Pobierz urządzenia z Tailscale")
            }
        }
    }
}