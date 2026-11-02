package vn.dinhtrung.thuocla.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {

    /** INSERT OR IGNORE: returns -1 when (dateKey, minuteOfDay) already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: RecordEntity): Long

    @Query("SELECT * FROM records WHERE dateKey = :dateKey AND minuteOfDay = :minuteOfDay LIMIT 1")
    suspend fun get(dateKey: String, minuteOfDay: Int): RecordEntity?

    @Query("SELECT * FROM records WHERE dateKey = :dateKey ORDER BY minuteOfDay ASC")
    suspend fun day(dateKey: String): List<RecordEntity>

    @Query("SELECT * FROM records WHERE dateKey = :dateKey ORDER BY minuteOfDay ASC")
    fun dayFlow(dateKey: String): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE dateKey BETWEEN :from AND :to ORDER BY dateKey ASC, minuteOfDay ASC")
    suspend fun range(from: String, to: String): List<RecordEntity>

    @Query("SELECT * FROM records WHERE dateKey BETWEEN :from AND :to ORDER BY dateKey ASC, minuteOfDay ASC")
    fun rangeFlow(from: String, to: String): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE dateKey = :dateKey ORDER BY minuteOfDay DESC LIMIT 1")
    suspend fun last(dateKey: String): RecordEntity?

    @Query("DELETE FROM records WHERE dateKey = :dateKey AND minuteOfDay = :minuteOfDay")
    suspend fun delete(dateKey: String, minuteOfDay: Int): Int

    /** [triggerIdx] null clears the reason. */
    @Query(
        "UPDATE records SET triggerIdx = :triggerIdx " +
            "WHERE dateKey = :dateKey AND minuteOfDay = :minuteOfDay"
    )
    suspend fun updateTrigger(dateKey: String, minuteOfDay: Int, triggerIdx: Int?): Int

    @Query("SELECT COUNT(*) FROM records WHERE dateKey BETWEEN :from AND :to")
    suspend fun countBetween(from: String, to: String): Int

    @Query("SELECT COUNT(*) FROM records WHERE dateKey BETWEEN :from AND :to")
    fun countBetweenFlow(from: String, to: String): Flow<Int>

    @Query("SELECT COUNT(DISTINCT dateKey) FROM records")
    suspend fun distinctDayCount(): Int

    @Query("SELECT COUNT(DISTINCT dateKey) FROM records")
    fun distinctDayCountFlow(): Flow<Int>

    /** Bulk insert used by import; -1 marks a row skipped because its minute was already taken. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<RecordEntity>): List<Long>

    @Query("DELETE FROM records")
    suspend fun deleteAll(): Int

    @Query("SELECT * FROM records ORDER BY dateKey ASC, minuteOfDay ASC")
    suspend fun all(): List<RecordEntity>
}

@Dao
interface ConfigDao {

    @Query("SELECT * FROM config WHERE id = ${ConfigEntity.SINGLETON_ID}")
    suspend fun get(): ConfigEntity?

    @Query("SELECT * FROM config WHERE id = ${ConfigEntity.SINGLETON_ID}")
    fun flow(): Flow<ConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConfigEntity)
}
