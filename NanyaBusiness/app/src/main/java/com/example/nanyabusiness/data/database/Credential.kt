package com.example.nanyabusiness.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credentials")
data class Credential(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val host: String = "",
    val port: Int = 22,
    val type: String = "SSH",
    val username: String = "",
    val password: String = "",
    val privateKey: String = "",
    val vpnConfig: String = "",
    val lastUsed: Long = 0L,
    val isActive: Boolean = false
)
