package com.example.firebaselocalsample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.firebaselocalsample.ui.theme.FirebaseLocalSampleTheme
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.time.measureTimedValue

/**
 * Enable this flag to run the setup that creates and deletes 10,000 documents.
 * Once you have run this once, disable it to run the fetch tests.
 */
const val RUN_SETUP = false

const val FETCH_COUNT = 200

const val DOCUMENT_CREATE_AND_DELETE_COUNT = 10_000

// As documented by Firestore
const val MAX_FIRESTORE_BATCH_SIZE = 500

class MainActivity : ComponentActivity() {

    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        firestore = Firebase.firestore
        firestore.useEmulator("10.0.2.2", 8080)

        val documentCreateCount = mutableIntStateOf(0)
        val documentDeleteCount = mutableIntStateOf(0)
        val setupDone = mutableStateOf(false)
        val totalTimeOfActiveCollectionMs = mutableStateListOf<Long>()
        val totalTimeOfInactiveCollectionMs = mutableStateListOf<Long>()

        lifecycleScope.launch {
            val activeCollectionRef = firestore.collection("users/active/docs")
            val inactiveCollectionRef = firestore.collection("users/inactive/docs")
            if(RUN_SETUP) {
                var createBatch = firestore.batch()
                var deleteBatch = firestore.batch()
                var batchSize = 0

                repeat(DOCUMENT_CREATE_AND_DELETE_COUNT) {
                    val documentRef = activeCollectionRef.document()
                    createBatch.set(documentRef, mapOf("foo" to it))
                    deleteBatch.delete(documentRef)
                    batchSize++

                    if (batchSize == MAX_FIRESTORE_BATCH_SIZE
                            || it + 1 == DOCUMENT_CREATE_AND_DELETE_COUNT) {
                        createBatch.commit().await()
                        documentCreateCount.intValue += batchSize
                        deleteBatch.commit().await()
                        documentDeleteCount.intValue += batchSize
                        createBatch = firestore.batch()
                        deleteBatch = firestore.batch()
                        batchSize = 0
                    }
                }

                check(batchSize == 0) { "batchSize should be 0, but got $batchSize" }
                setupDone.value = true
            } else {
                repeat(FETCH_COUNT) {
                    val timedValue = measureTimedValue {
                        inactiveCollectionRef.get(Source.CACHE).await()
                    }
                    totalTimeOfInactiveCollectionMs += timedValue.duration.inWholeMilliseconds
                }

                repeat(FETCH_COUNT) {
                    val timedValue = measureTimedValue {
                        activeCollectionRef.get(Source.CACHE).await()
                    }
                    totalTimeOfActiveCollectionMs += timedValue.duration.inWholeMilliseconds
                }
            }
        }

        setContent {
            FirebaseLocalSampleTheme {
                Column(
                    modifier = Modifier
                        .systemBarsPadding()
                ) {
                    if(RUN_SETUP) {
                        if (setupDone.value) {
                            Text(
                                text = "Setup completed. " +
                                    "Created ${documentCreateCount.intValue} documents. " +
                                    "Deleted ${documentDeleteCount.intValue} documents.",
                            )
                        } else {
                            Text(
                                text = "Setup in progress. " +
                                "Created ${documentCreateCount.intValue} documents. " +
                                "Deleted ${documentDeleteCount.intValue} documents.",
                            )
                        }
                    } else {
                        Text(
                            text = "Average time of inactive collection: " +
                                "${
                                    String.format(
                                        Locale.US,
                                        "%.2f",
                                        totalTimeOfInactiveCollectionMs.average()
                                    )
                                } ms" +
                                "(n=${totalTimeOfInactiveCollectionMs.size})",
                        )
                        Text(
                            text = "Average time of active collection: " +
                                "${
                                    String.format(
                                        Locale.US,
                                        "%.2f",
                                        totalTimeOfActiveCollectionMs.average()
                                    )
                                } ms " +
                                "(n=${totalTimeOfActiveCollectionMs.size})",
                        )
                    }
                }
            }
        }
    }

}
