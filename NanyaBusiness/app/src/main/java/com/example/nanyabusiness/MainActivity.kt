package com.example.nanyabusiness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.nanyabusiness.ui.theme.NanyaBusinessTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NanyaBusinessTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                        TaskScreen()

                }
            }
        }
    }
}

// =================================================================================
// --- Warstwa Danych: Definicje Room ---
// =================================================================================

// Encja (Tabela w bazie danych)
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val isCompleted: Boolean = false
)

// DAO (Data Access Object) - Interfejs zapytań
@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)
    @Update
    suspend fun updateTask(task: Task)
    @Delete
    suspend fun deleteTask(task: Task)
    @Query("SELECT * FROM tasks ORDER BY id DESC")
    fun getAllTasksStream(): Flow<List<Task>>
}

// Baza Danych
@Database(entities = [Task::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "task_database_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Repozytorium - pośrednik między ViewModel a DAO
class TaskRepository(private val taskDao: TaskDao) {
    val allTasks: Flow<List<Task>> = taskDao.getAllTasksStream()
    suspend fun insert(task: Task) {
        taskDao.insertTask(task)
    }
    suspend fun update(task: Task) {
        taskDao.updateTask(task)
    }
}


// =================================================================================
// --- Architektura: ViewModel i Fabryka ---
// =================================================================================

class TaskViewModel(private val repository: TaskRepository) : ViewModel() {
    val tasks: StateFlow<List<Task>> = repository.allTasks
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    fun addTask(title: String) {
        if (title.isNotBlank()) {
            viewModelScope.launch {
                repository.insert(Task(title = title))
            }
        }
    }
    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            repository.update(task.copy(isCompleted = !task.isCompleted))
        }
    }
}

// Fabryka do tworzenia ViewModelu z zależnością
class TaskViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            val dao = AppDatabase.getDatabase(application).taskDao()
            val repository = TaskRepository(dao)
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// =================================================================================
// --- UI ---
// =================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen() {
    // Pobranie Application context do fabryki
    val application = LocalContext.current.applicationContext as Application
    val viewModel: TaskViewModel = viewModel(factory = TaskViewModelFactory(application))
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    var newTaskTitle by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Room To-Do Lista") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Sekcja dodawania nowego zadania
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    label = { Text("Nowe zadanie") },
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    viewModel.addTask(newTaskTitle)
                    newTaskTitle = ""
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Dodaj zadanie")
                }
            }
            // Lista zadań
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(tasks) { task ->
                    TaskItem(task = task, onToggle = { viewModel.toggleTaskCompletion(task) })
                }
            }
        }
    }
}

@Composable
fun TaskItem(task: Task, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.CheckCircle,
            contentDescription = "Status zadania",
            tint = if (task.isCompleted) Color.Green else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = task.title,
            style = if (task.isCompleted) TextStyle(textDecoration = TextDecoration.LineThrough) else TextStyle.Default
        )
    }
}