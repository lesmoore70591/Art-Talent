package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppRepository(private val db: AppDatabase) {
    private val keyValueDao = db.keyValueDao()
    private val historyDao = db.historyDao()

    // Key-value setters and getters
    suspend fun saveString(key: String, value: String) {
        keyValueDao.insertValue(KeyValueEntry(key, value))
    }

    suspend fun getString(key: String, default: String = ""): String {
        return keyValueDao.getValue(key) ?: default
    }

    fun getStringFlow(key: String, default: String = ""): Flow<String> {
        return keyValueDao.getValueFlow(key).map { it ?: default }
    }

    suspend fun saveBoolean(key: String, value: Boolean) {
        keyValueDao.insertValue(KeyValueEntry(key, value.toString()))
    }

    suspend fun getBoolean(key: String, default: Boolean = false): Boolean {
        return keyValueDao.getValue(key)?.toBooleanStrictOrNull() ?: default
    }

    fun getBooleanFlow(key: String, default: Boolean = false): Flow<Boolean> {
        return keyValueDao.getValueFlow(key).map { it?.toBooleanStrictOrNull() ?: default }
    }

    // Settings helpers
    suspend fun isOnboarded(): Boolean = getBoolean("is_onboarded", false)
    fun isOnboardedFlow(): Flow<Boolean> = getBooleanFlow("is_onboarded", false)

    suspend fun setOnboarded(value: Boolean) = saveBoolean("is_onboarded", value)

    // History interaction
    val historyList: Flow<List<HistoryEntry>> = historyDao.getAllHistory()

    suspend fun addHistory(contentType: String, content: String) {
        historyDao.insertHistory(HistoryEntry(contentType = contentType, content = content))
    }

    suspend fun deleteHistory(id: Int) {
        historyDao.deleteHistoryById(id)
    }

    suspend fun clearAllData() {
        keyValueDao.clearAll()
        historyDao.clearAll()
    }
}
