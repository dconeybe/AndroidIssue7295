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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.firebaselocalsample.ui.theme.FirebaseLocalSampleTheme
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import java.util.Locale
import kotlin.math.roundToLong
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

const val FETCH_COUNT = 200

const val DOCUMENT_CREATE_AND_DELETE_COUNT = 10_000

// As documented by Firestore
const val MAX_FIRESTORE_BATCH_SIZE = 500

enum class CollectionType {
  Normal,
  CollectionGroup,
}

val COLLECTION_TYPE = CollectionType.Normal

val availableProcessors = Runtime.getRuntime().availableProcessors()

class MainActivity : ComponentActivity() {

  private lateinit var firestore: FirebaseFirestore
  private lateinit var firestoreBuildId: String
  private lateinit var testResultsDao: Persistence.TestResultsDao

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    testResultsDao =
      Room.databaseBuilder(applicationContext, Persistence.AppDatabase::class.java, "dbgqamg2bd")
        .build()
        .testResultsDao()

    firestore = Firebase.firestore
    firestore.useEmulator("10.0.2.2", 8080)
    firestoreBuildId = firestore.fetchBuildId()

    val runState = mutableStateOf<RunState>(RunState.NotStarted)
    val activeTimesMillis = mutableStateListOf<Long>()
    val inactiveTimesMillis = mutableStateListOf<Long>()
    val testHistory = mutableStateOf<List<AverageInfo>?>(null)

    lifecycleScope.launch {
      launch(Dispatchers.Default) {
        val testResults = testResultsDao.getAll()
        testHistory.value = testResults.totalAverageByFirestoreBuildId()
        Log.i(RESULTS_LOG_TAG, "Previous Test Results:")
        testResults.logAllToLogcat()
      }

      val startTime = Clock.System.now()
      val activeCollectionRef = firestore.collection("users/active/docs")

      val setupCompletedDocRef = firestore.document("setup/completed")
      val setupCompleted =
        setupCompletedDocRef
          .get(Source.CACHE)
          .runCatching { await() }
          .fold(onSuccess = { it.exists() }, onFailure = { false })

      val testState: RunState.Test =
        if (setupCompleted) {
          RunState.Test().also { runState.value = it }
        } else {
          var setupState = RunState.Setup()
          runState.value = setupState
          var createBatch = firestore.batch()
          var deleteBatch = firestore.batch()
          var batchSize = 0

          repeat(DOCUMENT_CREATE_AND_DELETE_COUNT) {
            val documentRef = activeCollectionRef.document()
            createBatch.set(documentRef, mapOf("foo" to Random.nextInt(100)))
            deleteBatch.delete(documentRef)
            batchSize++

            if (
              batchSize == MAX_FIRESTORE_BATCH_SIZE || it + 1 == DOCUMENT_CREATE_AND_DELETE_COUNT
            ) {
              createBatch.commit().await()
              setupState =
                setupState.run { copy(documentCreateCount = documentCreateCount + batchSize) }
              runState.value = setupState

              deleteBatch.commit().await()
              setupState =
                setupState.run { copy(documentDeleteCount = documentDeleteCount + batchSize) }
              runState.value = setupState

              createBatch = firestore.batch()
              deleteBatch = firestore.batch()
              batchSize = 0
            }
          }

          check(batchSize == 0) { "batchSize should be 0, but got $batchSize" }
          setupCompletedDocRef.set(mapOf("completed" to true)).await()
          RunState.Test(setupState).also { runState.value = it }
        }

      var isInactiveWarmup = true
      repeat(FETCH_COUNT + 1) {
        val inactiveCollectionRef = firestore.collection(Random.nextAlphanumericString(20))
        val timedValue = measureTimedValue {
          val query =
            when (COLLECTION_TYPE) {
              CollectionType.Normal -> inactiveCollectionRef
              CollectionType.CollectionGroup -> firestore.collectionGroup(inactiveCollectionRef.id)
            }.whereGreaterThan("foo", 50)
          query.get(Source.CACHE).await()
        }
        if (isInactiveWarmup) {
          isInactiveWarmup = false
        } else {
          inactiveTimesMillis += timedValue.duration.inWholeMilliseconds
        }
      }

      var isActiveWarmup = true
      repeat(FETCH_COUNT + 1) {
        val query =
          when (COLLECTION_TYPE) {
            CollectionType.Normal -> activeCollectionRef
            CollectionType.CollectionGroup -> firestore.collectionGroup(activeCollectionRef.id)
          }.whereGreaterThan("foo", 50)
        val timedValue = measureTimedValue { query.get(Source.CACHE).await() }
        if (isActiveWarmup) {
          isActiveWarmup = false
        } else {
          activeTimesMillis += timedValue.duration.inWholeMilliseconds
        }
      }

      runState.value = RunState.Done(testState.setupResult)

      testResultsDao.insert(
        Persistence.TestResult(
            uid = 0,
            firestoreBuildId = firestoreBuildId,
            date = startTime,
            count = FETCH_COUNT,
            total = activeTimesMillis.sum().milliseconds,
            average = activeTimesMillis.average().milliseconds,
            min = activeTimesMillis.min().milliseconds,
            max = activeTimesMillis.max().milliseconds,
          )
          .also { Log.i(RESULTS_LOG_TAG, "New Test Result: ${it.toLogcatLogString()}") }
      )

      launch(Dispatchers.Default) {
        val testResults = testResultsDao.getAll()
        testHistory.value = testResults.totalAverageByFirestoreBuildId()
        Log.i(RESULTS_LOG_TAG, "Updated Test Results:")
        testResults.logAllToLogcat()
      }
    }

    setContent {
      FirebaseLocalSampleTheme {
        MainScreen(
          firestoreBuildId = firestoreBuildId,
          runState = runState.value,
          activeTimesMillis = activeTimesMillis,
          inactiveTimesMillis = inactiveTimesMillis,
          testHistory = testHistory.value,
        )
      }
    }
  }
}

private sealed interface RunState {
  data object NotStarted : RunState

  data class Setup(val documentCreateCount: Int = 0, val documentDeleteCount: Int = 0) : RunState

  data class Test(val setupResult: Setup? = null) : RunState

  data class Done(val setupResult: Setup?) : RunState
}

@Composable
private fun MainScreen(
  firestoreBuildId: String?,
  runState: RunState,
  activeTimesMillis: List<Long>,
  inactiveTimesMillis: List<Long>,
  testHistory: List<AverageInfo>?,
) {
  Box(modifier = Modifier.systemBarsPadding()) {
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
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

@Composable
private fun EnvText(firestoreBuildId: String?) {
  Text("Test Environment", textDecoration = TextDecoration.Underline)
  Text("firestore build id: $firestoreBuildId")
  Text("availableProcessors: $availableProcessors")
  Text("android sdk version: ${Build.VERSION.SDK_INT}")
  Spacer(Modifier.height(8.dp))
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

  Text("Test Setup", textDecoration = TextDecoration.Underline)
  Text("Created ${setupState.documentCreateCount} documents.")
  Text("Deleted ${setupState.documentDeleteCount} documents.")

  if (runState !is RunState.Setup) {
    Text("Setup completed")
  }

  Spacer(Modifier.height(8.dp))
}

@Composable
private fun TestText(
  runState: RunState,
  activeTimesMillis: List<Long>,
  inactiveTimesMillis: List<Long>,
) {
  when (runState) {
    RunState.NotStarted,
    is RunState.Setup -> return
    is RunState.Test,
    is RunState.Done -> {}
  }

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
  Text("Test History (${testHistory.size})", textDecoration = TextDecoration.Underline)
  testHistory.forEach { Text(it.toLogcatLogString()) }
}

@Composable
private fun TestTimes(name: String, timesMillis: List<Long>) {
  val average = timesMillis.average().takeIf { !it.isNaN() } ?: 0.0
  val averageStr = String.format(Locale.US, "%.2f", average)
  val totalStr = String.format(Locale.US, "%,d", timesMillis.sum())
  Text(name)
  Text("    count: ${timesMillis.size}")
  Text("    total: $totalStr ms")
  Text("    average: $averageStr ms")
  Text("    min: ${timesMillis.minOrNull() ?: 0} ms")
  Text("    max: ${timesMillis.maxOrNull() ?: 0} ms")
}

/**
 * Generates and returns a string containing random alphanumeric characters.
 *
 * The characters returned are taken from the set of characters comprising of the 10 numeric digits
 * and the 26 lowercase English characters.
 *
 * @param length the number of random characters to generate and include in the returned string;
 *   must be greater than or equal to zero.
 * @return a string containing the given number of random alphanumeric characters.
 * @hide
 */
@Suppress("unused")
fun Random.nextAlphanumericString(length: Int): String {
  require(length >= 0) { "invalid length: $length" }
  return (0 until length).map { ALPHANUMERIC_ALPHABET.random(this) }.joinToString(separator = "")
}

// The set of characters comprising of the 10 numeric digits and the 26 lowercase letters of the
// English alphabet with some characters removed that can look similar in different fonts, like
// '1', 'l', and 'i'.
private const val ALPHANUMERIC_ALPHABET = "23456789abcdefghjkmnpqrstvwxyz"

fun FirebaseFirestore.fetchBuildId(): String = document("foo/FetchBuildId_vbncckz7ar").id

val logcatLogStringLocalDateTimeFormat =
  LocalDateTime.Format {
    monthName(MonthNames.ENGLISH_ABBREVIATED)
    char(' ')
    day(Padding.ZERO)
    chars(", ")
    year(Padding.ZERO)
    chars(" @ ")
    hour(Padding.ZERO)
    char(':')
    minute(Padding.ZERO)
    char(':')
    second(Padding.ZERO)
  }

fun Int.formattedWithThousandsSeparator(): String = toLong().formattedWithThousandsSeparator()

fun Long.formattedWithThousandsSeparator(): String = String.format(Locale.US, "%,d", this)

fun Persistence.TestResult.toLogcatLogString() = buildString {
  fun appendMs(key: String, duration: Duration) = apply {
    append(' ')
    append(key)
    append('=')
    append(duration.inWholeMilliseconds.formattedWithThousandsSeparator())
  }

  val localDate = date.toLocalDateTime(TimeZone.currentSystemDefault())
  logcatLogStringLocalDateTimeFormat.formatTo(this, localDate)
  append(": count=").append(count)
  append(" firestoreBuildId=").append(firestoreBuildId)
  appendMs("total", total).append(" ms")
  appendMs("average", average)
  appendMs("min", min)
  appendMs("max", max)
}

const val RESULTS_LOG_TAG = "tresbyrfhg"

fun Persistence.TestResult.logToLogcat() {
  Log.i(RESULTS_LOG_TAG, toLogcatLogString())
}

data class AverageInfo(val firestoreBuildId: String, val n: Int, val average: Double)

fun AverageInfo.toLogcatLogString(): String = buildString {
  append(firestoreBuildId)
  append(" n=").append(n.formattedWithThousandsSeparator())
  append(" average=").append(average.roundToLong().formattedWithThousandsSeparator()).append(" ms")
}

fun Iterable<Persistence.TestResult>.totalAverageByFirestoreBuildId(): List<AverageInfo> =
  groupBy { it.firestoreBuildId }
    .map { (firestoreBuildId, testResults) ->
      val average = testResults.map { testResult -> testResult.total.inWholeMilliseconds }.average()
      AverageInfo(firestoreBuildId = firestoreBuildId, n = testResults.size, average = average)
    }
    .sortedBy { it.firestoreBuildId.lowercase() }

suspend fun List<Persistence.TestResult>.logAllToLogcat() {
  Log.i(RESULTS_LOG_TAG, "Got ${size} test results")
  withContext(Dispatchers.Default) {
    sortedBy { it.date }.forEach { it.logToLogcat() }
    totalAverageByFirestoreBuildId().forEach { Log.i("tresbyrfhg", it.toLogcatLogString()) }
  }
}
