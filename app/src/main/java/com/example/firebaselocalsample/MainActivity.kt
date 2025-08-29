package com.example.firebaselocalsample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.firebaselocalsample.ui.theme.FirebaseLocalSampleTheme
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import java.util.Locale
import kotlin.time.measureTimedValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

const val FETCH_COUNT = 200

const val DOCUMENT_CREATE_AND_DELETE_COUNT = 10_000

// As documented by Firestore
const val MAX_FIRESTORE_BATCH_SIZE = 500

class MainActivity : ComponentActivity() {

  private lateinit var firestore: FirebaseFirestore

  private sealed interface RunState {
    data object NotStarted : RunState

    data class Setup(val documentCreateCount: Int = 0, val documentDeleteCount: Int = 0) : RunState

    data class Test(val setupResult: Setup? = null) : RunState

    data class Done(val setupResult: Setup?) : RunState
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    firestore = Firebase.firestore
    firestore.useEmulator("10.0.2.2", 8080)

    val runState = mutableStateOf<RunState>(RunState.NotStarted)
    val totalTimeOfActiveCollectionMs = mutableStateListOf<Long>()
    val totalTimeOfInactiveCollectionMs = mutableStateListOf<Long>()

    lifecycleScope.launch {
      val activeCollectionRef = firestore.collection("users/active/docs")
      val inactiveCollectionRef = firestore.collection("users/inactive/docs")

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
            createBatch.set(documentRef, mapOf("foo" to it))
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

      repeat(FETCH_COUNT) {
        val timedValue = measureTimedValue { inactiveCollectionRef.get(Source.CACHE).await() }
        totalTimeOfInactiveCollectionMs += timedValue.duration.inWholeMilliseconds
      }

      repeat(FETCH_COUNT) {
        val timedValue = measureTimedValue { activeCollectionRef.get(Source.CACHE).await() }
        totalTimeOfActiveCollectionMs += timedValue.duration.inWholeMilliseconds
      }

      runState.value = RunState.Done(testState.setupResult)
    }

    setContent {
      FirebaseLocalSampleTheme {
        Column(modifier = Modifier.systemBarsPadding()) {
          val setupState: RunState.Setup? =
            when (val state = runState.value) {
              RunState.NotStarted -> null
              is RunState.Setup -> state
              is RunState.Test -> state.setupResult
              is RunState.Done -> state.setupResult
            }

          if (setupState !== null) {
            when (runState.value) {
              RunState.NotStarted -> throw IllegalStateException("should never get here")
              is RunState.Setup -> Text("Setup in progress...")
              is RunState.Test,
              is RunState.Done -> Text("Setup completed.")
            }
            Text("Created ${setupState.documentCreateCount} documents.")
            Text("Deleted ${setupState.documentDeleteCount} documents.")
          }

          when (runState.value) {
            RunState.NotStarted,
            is RunState.Setup -> {}
            is RunState.Test -> Text("Test in progress...")
            is RunState.Done -> Text("Test completed.")
          }

          when (runState.value) {
            RunState.NotStarted,
            is RunState.Setup -> {}
            is RunState.Test,
            is RunState.Done -> {
              Text(
                text =
                  "Average time of inactive collection: " +
                    "${
                                        String.format(
                                            Locale.US,
                                            "%.2f",
                                            totalTimeOfInactiveCollectionMs.average(),
                                        )
                                    } ms" +
                    "(n=${totalTimeOfInactiveCollectionMs.size})"
              )
              Text(
                text =
                  "Average time of active collection: " +
                    "${
                                        String.format(
                                            Locale.US,
                                            "%.2f",
                                            totalTimeOfActiveCollectionMs.average(),
                                        )
                                    } ms " +
                    "(n=${totalTimeOfActiveCollectionMs.size})"
              )
            }
          }
        }
      }
    }
  }
}
