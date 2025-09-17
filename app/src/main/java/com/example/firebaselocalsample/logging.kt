package com.example.firebaselocalsample

import android.util.Log
import java.util.Locale
import kotlin.collections.Iterable
import kotlin.collections.List
import kotlin.collections.average
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach
import kotlin.collections.groupBy
import kotlin.collections.map
import kotlin.collections.sortedBy
import kotlin.math.roundToLong
import kotlin.text.append
import kotlin.text.buildString
import kotlin.text.format
import kotlin.text.lowercase
import kotlin.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime

const val RESULTS_LOG_TAG = "tresbyrfhg"

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

private val logcatLogStringLocalDateTimeFormat =
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
