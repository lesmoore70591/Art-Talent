package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "key_value_store")
data class KeyValueEntry(
    @PrimaryKey val key: String,
    val value: String
)
