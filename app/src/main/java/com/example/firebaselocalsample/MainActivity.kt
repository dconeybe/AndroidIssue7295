package com.example.firebaselocalsample

import android.os.Build
import android.os.Bundle
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
import com.example.firebaselocalsample.ui.theme.FirebaseLocalSampleTheme
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import java.util.Locale
import kotlin.random.Random
import kotlin.time.measureTimedValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
  private var firestoreBuildId: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    firestore = Firebase.firestore
    firestore.useEmulator("10.0.2.2", 8080)

    val runState = mutableStateOf<RunState>(RunState.NotStarted)
    val activeTimesMillis = mutableStateListOf<Long>()
    val inactiveTimesMillis = mutableStateListOf<Long>()

    lifecycleScope.launch {
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
    }

    setContent {
      FirebaseLocalSampleTheme {
        MainScreen(
          firestoreBuildId = firestoreBuildId,
          runState = runState.value,
          activeTimesMillis = activeTimesMillis,
          inactiveTimesMillis = inactiveTimesMillis,
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
fun Random.nextAlphanumericString(length: Int): String {
  require(length >= 0) { "invalid length: $length" }
  return (0 until length).map { ALPHANUMERIC_ALPHABET.random(this) }.joinToString(separator = "")
}

// The set of characters comprising of the 10 numeric digits and the 26 lowercase letters of the
// English alphabet with some characters removed that can look similar in different fonts, like
// '1', 'l', and 'i'.
private const val ALPHANUMERIC_ALPHABET = "23456789abcdefghjkmnpqrstvwxyz"

fun FirebaseFirestore.fetchBuildId(): String? = document("foo/FetchBuildId_vbncckz7ar").id
