package com.example.nanyabusiness.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ssh_users")
data class SshUser(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val deviceId: Int,
    val username: String,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
    val lastUsed: Long = 0L
)
