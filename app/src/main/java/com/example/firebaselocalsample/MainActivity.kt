package com.example.firebaselocalsample

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.firebaselocalsample.ui.theme.FirebaseLocalSampleTheme
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.time.measureTimedValue

/**
 * Enable this flag to run the setup that creates and deletes 10,000 documents.
 * Once you have run this once, disable it to run the fetch tests.
 */
const val RUN_SETUP = true

const val FETCH_COUNT = 200

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val totalTimeOfActiveCollectionMs = mutableStateListOf<Long>()
        val totalTimeOfInactiveCollectionMs = mutableStateListOf<Long>()


        lifecycleScope.launch {
            if(RUN_SETUP) {
                createAndDeleteDocuments()
            } else {
                repeat(FETCH_COUNT) {
                    val timedValue = measureTimedValue {
                        FirebaseFirestore.getInstance()
                            .collection("users/inactive/docs")
                            .get(Source.CACHE)
                            .await()
                    }

                    totalTimeOfInactiveCollectionMs += timedValue.duration.inWholeMilliseconds
                }

                repeat(FETCH_COUNT) {
                    val timedValue = measureTimedValue {
                        FirebaseFirestore.getInstance()
                            .collection("users/active/docs")
                            .get(Source.CACHE)
                            .await()
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
                        Text(
                            text = "Setup complete. Disable the RUN_SETUP flag and restart the app to run the fetch tests.",
                        )
                        return@Column
                    }

                    val averageTime = totalTimeOfActiveCollectionMs.average()
                    Text(
                        text = "Average time of active collection: ${String.format("%.2f", averageTime)} ms",
                    )

                    val averageTimeInactive = totalTimeOfInactiveCollectionMs.average()
                    Text(
                        text = "Average time of inactive collection: ${String.format("%.2f", averageTimeInactive)} ms",
                    )
                }
            }
        }
    }

    /**
     * This function creates and deletes 10,000 documents in Firestore. Therefore the result of
     * a query to this collection will be empty. However each document does not contain
     * much data so this never ends up being garbage collected by Firestore.
     */
    private fun createAndDeleteDocuments() {
        repeat(10_000) {
            val ref = FirebaseFirestore.getInstance()
                .collection("users/active/docs")
                .document()

            ref.set(
                mapOf("hello" to "world")
            )

            ref.delete()
        }
    }
}
