package com.example.firebaselocalsample

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue
import kotlinx.coroutines.tasks.await

private const val FETCH_COUNT = 200

private const val DOCUMENT_CREATE_AND_DELETE_COUNT = 10_000

// As documented by Firestore
private const val MAX_FIRESTORE_BATCH_SIZE = 500

private enum class CollectionType {
  Normal,
  CollectionGroup,
}

private val COLLECTION_TYPE = CollectionType.Normal

class TestRunner(
  val firestore: FirebaseFirestore,
  val firestoreSdkVersion: String,
  val testResultsDao: Persistence.TestResultsDao,
  val isDeleteDocumentsInSetupEnabled: Boolean = true,
  val isInactiveTestEnabled: Boolean = true,
) {

  val runState = mutableStateOf<RunState>(RunState.NotStarted)
  val activeTimesMillis = mutableStateListOf<Long>()
  val inactiveTimesMillis = mutableStateListOf<Long>()

  suspend fun run() {
    activeTimesMillis.clear()
    inactiveTimesMillis.clear()

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

          if (batchSize == MAX_FIRESTORE_BATCH_SIZE || it + 1 == DOCUMENT_CREATE_AND_DELETE_COUNT) {
            createBatch.commit().await()
            setupState =
              setupState.copy(documentCreateCount = setupState.documentCreateCount + batchSize)
            runState.value = setupState

            if (isDeleteDocumentsInSetupEnabled) {
              deleteBatch.commit().await()
              setupState =
                setupState.copy(documentDeleteCount = setupState.documentDeleteCount + batchSize)
              runState.value = setupState
            }

            createBatch = firestore.batch()
            deleteBatch = firestore.batch()
            batchSize = 0
          }
        }

        check(batchSize == 0) { "batchSize should be 0, but got $batchSize" }
        setupCompletedDocRef.set(mapOf("completed" to true)).await()
        RunState.Test(setupState).also { runState.value = it }
      }

    if (isInactiveTestEnabled) {
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
          firestoreBuildId = firestoreSdkVersion,
          date = startTime,
          count = FETCH_COUNT,
          total = activeTimesMillis.sum().milliseconds,
          average = activeTimesMillis.average().milliseconds,
          min = activeTimesMillis.min().milliseconds,
          max = activeTimesMillis.max().milliseconds,
        )
        .also { Log.i(RESULTS_LOG_TAG, "New Test Result: ${it.toLogcatLogString()}") }
    )
  }
}

sealed interface RunState {
  data object NotStarted : RunState

  data class Setup(val documentCreateCount: Int = 0, val documentDeleteCount: Int = 0) : RunState

  data class Test(val setupResult: Setup? = null) : RunState

  data class Done(val setupResult: Setup?) : RunState
}
