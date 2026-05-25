package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyValueDao {
    @Query("SELECT value FROM key_value_store WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Query("SELECT value FROM key_value_store WHERE `key` = :key LIMIT 1")
    fun getValueFlow(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertValue(entry: KeyValueEntry)

    @Query("DELETE FROM key_value_store WHERE `key` = :key")
    suspend fun deleteValue(key: String)

    @Query("DELETE FROM key_value_store")
    suspend fun clearAll()
}
