package com.example.firebaselocalsample

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.firebaselocalsample.ui.theme.FirebaseLocalSampleTheme
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data object DefaultTestConfig {
  val useFirestoreEmulator = true
  val isDeleteDocumentsInSetupEnabled = true
  val isInactiveTestEnabled = true
}

class MainActivity : ComponentActivity() {

  private lateinit var testRunner: TestRunner
  private lateinit var testHistory: MutableState<List<AverageInfo>?>
  private var testJob: Job? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    testHistory = mutableStateOf(null)

    val firestore = Firebase.firestore
    if (DefaultTestConfig.useFirestoreEmulator) {
      firestore.useEmulator("10.0.2.2", 8080)
    }

    testRunner =
      TestRunner(
        firestore = firestore,
        firestoreBuildId = firestore.fetchBuildId(),
        testResultsDao =
          Room.databaseBuilder(
              applicationContext,
              Persistence.AppDatabase::class.java,
              "dbgqamg2bd",
            )
            .build()
            .testResultsDao(),
        isDeleteDocumentsInSetupEnabled = DefaultTestConfig.isDeleteDocumentsInSetupEnabled,
        isInactiveTestEnabled = DefaultTestConfig.isInactiveTestEnabled,
      )

    setContent {
      FirebaseLocalSampleTheme {
        MainScreen(
          firestoreBuildId = testRunner.firestoreBuildId,
          runState = testRunner.runState.value,
          activeTimesMillis = testRunner.activeTimesMillis,
          inactiveTimesMillis =
            if (testRunner.isInactiveTestEnabled) testRunner.inactiveTimesMillis else null,
          testHistory = testHistory.value,
          onReRunClick = ::startTest,
          onClearHistoryClick = ::clearHistory,
        )
      }
    }

    startTest()
  }

  fun startTest() {
    testJob?.isActive?.let {
      if (it) {
        return
      }
    }

    testJob =
      lifecycleScope.launch {
        val previousUpdateTestHistoryJob = launch {
          updateTestHistory("Previous", testRunner.testResultsDao, testHistory)
        }

        testRunner.run()

        previousUpdateTestHistoryJob.join()

        launch { updateTestHistory("Updated", testRunner.testResultsDao, testHistory) }
      }
  }

  private fun clearHistory() {
    lifecycleScope.launch {
      withContext(Dispatchers.Default) {
        testRunner.testResultsDao.clear()
        updateTestHistory("Cleared", testRunner.testResultsDao, testHistory)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
  firestoreBuildId: String?,
  runState: RunState,
  activeTimesMillis: List<Long>?,
  inactiveTimesMillis: List<Long>?,
  testHistory: List<AverageInfo>?,
  onReRunClick: () -> Unit,
  onClearHistoryClick: () -> Unit,
) {
  var showMenu by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Firebase Local Sample") },
        actions = {
          IconButton(onClick = { showMenu = !showMenu }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
          }
          DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
              text = { Text("Re-Run Test") },
              onClick = {
                showMenu = false
                onReRunClick()
              },
            )
            DropdownMenuItem(
              text = { Text("Clear History") },
              onClick = {
                showMenu = false
                onClearHistoryClick()
              },
            )
          }
        },
      )
    }
  ) { innerPadding ->
    Box(modifier = Modifier.padding(innerPadding)) {
      Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        EnvText(firestoreBuildId)
        SetupText(runState)
        TestText(
          runState,
          activeTimesMillis = activeTimesMillis,
          inactiveTimesMillis = inactiveTimesMillis,
        )
        TestHistoryText(testHistory)
      }
    }
  }
}

@Composable
private fun EnvText(firestoreBuildId: String?) {
  Spacer(Modifier.height(8.dp))
  Text("Test Environment", textDecoration = TextDecoration.Underline)
  Text("firestore build id: $firestoreBuildId")
  Text("availableProcessors: $availableProcessors")
  Text("android sdk version: ${Build.VERSION.SDK_INT}")
}

@Composable
private fun SetupText(runState: RunState) {
  val setupState: RunState.Setup =
    when (runState) {
      RunState.NotStarted -> return
      is RunState.Setup -> runState
      is RunState.Test -> runState.setupResult ?: return
      is RunState.Done -> runState.setupResult ?: return
    }

  Spacer(Modifier.height(8.dp))
  Text("Test Setup", textDecoration = TextDecoration.Underline)
  Text("Created ${setupState.documentCreateCount} documents.")
  Text("Deleted ${setupState.documentDeleteCount} documents.")

  if (runState !is RunState.Setup) {
    Text("Setup completed")
  }
}

@Composable
private fun TestText(
  runState: RunState,
  activeTimesMillis: List<Long>?,
  inactiveTimesMillis: List<Long>?,
) {
  when (runState) {
    RunState.NotStarted,
    is RunState.Setup -> return
    is RunState.Test,
    is RunState.Done -> {}
  }

  Spacer(Modifier.height(8.dp))
  Text("Test Execution", textDecoration = TextDecoration.Underline)
  TestTimes("inactive", inactiveTimesMillis)
  TestTimes("active", activeTimesMillis)

  if (runState is RunState.Done) {
    Text("Test completed")
  }
}

@Composable
private fun TestHistoryText(testHistory: List<AverageInfo>?) {
  if (testHistory === null || testHistory.isEmpty()) {
    return
  }
  Spacer(Modifier.height(8.dp))
  Text("Test History (${testHistory.sumOf { it.n }})", textDecoration = TextDecoration.Underline)
  testHistory.forEach {
    Spacer(Modifier.height(4.dp))
    Text(it.toLogcatLogString())
  }
}

@Composable
private fun TestTimes(name: String, timesMillis: List<Long>?) {
  if (timesMillis === null) {
    return
  }
  val average = timesMillis.average().takeIf { !it.isNaN() } ?: 0.0
  val averageStr = String.format(Locale.US, "%.2f", average)
  val totalStr = String.format(Locale.US, "%,d", timesMillis.sum())
  Spacer(Modifier.height(4.dp))
  Text(name)
  Text("    count: ${timesMillis.size}")
  Text("    total: $totalStr ms")
  Text("    average: $averageStr ms")
  Text("    min: ${timesMillis.minOrNull() ?: 0} ms")
  Text("    max: ${timesMillis.maxOrNull() ?: 0} ms")
}

private fun FirebaseFirestore.fetchBuildId(): String = document("foo/FetchBuildId_vbncckz7ar").id

suspend fun updateTestHistory(
  label: String,
  testResultsDao: Persistence.TestResultsDao,
  mutableState: MutableState<List<AverageInfo>?>,
) {
  withContext(Dispatchers.Default) {
    val testResults = testResultsDao.getAll()
    mutableState.value = testResults.totalAverageByFirestoreBuildId()
    Log.i(RESULTS_LOG_TAG, "$label Test Results:")
    testResults.logAllToLogcat()
  }
}

private val availableProcessors = Runtime.getRuntime().availableProcessors()
