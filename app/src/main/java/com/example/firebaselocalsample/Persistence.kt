package com.example.firebaselocalsample

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

object Persistence {

  @Database(entities = [TestResult::class], version = 1)
  @TypeConverters(Converters::class)
  abstract class AppDatabase : RoomDatabase() {
    abstract fun testResultsDao(): TestResultsDao
  }

  object Converters {

    @TypeConverter fun fromInstant(value: Instant?): String? = value?.toString()

    @TypeConverter fun toInstant(value: String?): Instant? = value?.let { Instant.parse(it) }

    @TypeConverter fun fromDuration(value: Duration?): Long? = value?.inWholeMilliseconds

    @TypeConverter fun toDuration(value: Long?): Duration? = value?.milliseconds
  }

  @Entity(tableName = "test_results")
  data class TestResult(
    @PrimaryKey(autoGenerate = true) val uid: Int,
    val firestoreBuildId: String,
    val date: Instant,
    val count: Int,
    val total: Duration,
    val average: Duration,
    val min: Duration,
    val max: Duration,
  )

  @Dao
  interface TestResultsDao {
    @Query("SELECT * FROM test_results") suspend fun getAll(): List<TestResult>

    @Insert suspend fun insert(testResult: TestResult)

    @Query("DELETE FROM test_results") suspend fun clear()
  }
}
