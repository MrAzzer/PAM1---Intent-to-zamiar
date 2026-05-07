package com.example.nanyabusiness.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SshUserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: SshUser)

    @Update
    suspend fun updateUser(user: SshUser)

    @Delete
    suspend fun deleteUser(user: SshUser)

    @Query("SELECT * FROM ssh_users WHERE deviceId = :deviceId ORDER BY id DESC")
    fun getUsersForDevice(deviceId: Int): Flow<List<SshUser>>

    @Query("SELECT * FROM ssh_users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Int): SshUser?
}
